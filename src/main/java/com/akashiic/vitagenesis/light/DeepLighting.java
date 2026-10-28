package com.akashiic.vitagenesis.light;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.vitagenesis.FeatureConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Light stage of the deep layer: the block light of everything the cave generator placed that glows (lava, glow lichen,
 * glow berries, amethyst, magma), and the fluids that should start flowing.
 * <p>
 * Generation writes blocks without light (the terrain and decoration stages run with the chunks around them only
 * partly loaded, where {@code World.updateLightByType} would be skipped). A chunk is lit once the 5x5 chunks around it
 * are loaded and decorated: from then on no generator writes into the 3x3 its light can reach (a decoration writes at
 * most one chunk away from its own chunk), so a single flood fill from the chunk's light sources gives the final
 * values. The fill computes exactly the vanilla rule of {@code World.computeLightValue} ({@code max(emission,
 * neighbour - max(1, opacity))}, opaque emitters let light in) and only ever raises values, so the light of the sources
 * of each chunk adds up to the light of all of them, in any order. Changed sections are sent again to the players that
 * already have them. The "lit" mark is saved with the chunk, so each chunk is lit once. A chunk whose 5x5 completes
 * only when a decorated neighbour comes back from disk is retried after the next world tick. Only chunks VitaGenesis
 * decorated are lit and have their fluids started.
 */
public final class DeepLighting {

    /** Chunks already lit. Identity of the loaded chunk object; cleared by garbage collection. */
    private static final Set<Chunk> LIT = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * Chunks decorated by VitaGenesis. Only those are lit and get their fluids started: a chunk whose deep layer was
     * decorated without VitaGenesis may hold what players built, still fluids included.
     */
    private static final Set<Chunk> OURS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * Chunks loaded since the last world tick, per dimension: a chunk that comes back from disk already decorated may
     * complete the 5x5 of a chunk that could not be lit when it was decorated (a neighbour was unloaded then).
     */
    private static final Map<Integer, Set<Long>> PENDING = new HashMap<>();

    private static final String NBT_KEY = "VitaGenesis";
    private static final int NBT_VERSION = 1;

    private DeepLighting() {}

    public static boolean isLit(Chunk chunk) {
        return LIT.contains(chunk);
    }

    /** Self test only: puts a chunk back to the state of a light stage that could not run. */
    public static void forgetLitForTests(Chunk chunk) {
        LIT.remove(chunk);
        chunk.setChunkModified();
    }

    /** Called from the decoration stage for the chunk VitaGenesis decorates. */
    public static void markOurs(Chunk chunk) {
        OURS.add(chunk);
        chunk.setChunkModified();
    }

    /** Called after {@code chunk} was decorated: lights every chunk whose 5x5 neighbourhood that completed. */
    public static void onDecorated(WorldServer world, Chunk chunk) {
        int minY = BellowAPI.getMinY(world);
        if (minY >= 0) return;
        tryAround(world, chunk.xPosition, chunk.zPosition, minY);
    }

    private static void tryAround(WorldServer world, int x, int z, int minY) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) tryLight(world, x + dx, z + dz, minY);
        }
    }

    /** Retries the chunks around those loaded since the last tick of this world. */
    public static void processPending(WorldServer world) {
        Set<Long> pending;
        synchronized (PENDING) {
            pending = PENDING.remove(world.provider.dimensionId);
        }
        if (pending == null) return;
        int minY = BellowAPI.getMinY(world);
        if (minY >= 0) return;
        for (long key : pending) tryAround(world, (int) (key >> 32), (int) key, minY);
    }

    private static Chunk loaded(WorldServer world, int x, int z) {
        IChunkProvider provider = world.getChunkProvider();
        return provider.chunkExists(x, z) ? provider.provideChunk(x, z) : null;
    }

    private static boolean decorated(Chunk c) {
        return c != null && ((IDeepChunk) c).bellow$getGenState() == DeepConstants.GEN_DECORATED;
    }

    private static void tryLight(WorldServer world, int cx, int cz, int minY) {
        Chunk centre = loaded(world, cx, cz);
        if (!decorated(centre) || LIT.contains(centre) || !OURS.contains(centre)) return;
        Chunk[] core = new Chunk[9];
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                Chunk c = loaded(world, cx + dx, cz + dz);
                if (!decorated(c)) return;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) core[(dz + 1) * 3 + dx + 1] = c;
            }
        }
        LIT.add(centre);
        centre.setChunkModified();
        new Flood(world, core, cx, cz, minY).run();
        if (FeatureConfig.flowingFluids) FluidActivation.run(world, centre, minY);
    }

    // ------------------------------------------------------------------------------------------- light properties

    /** Per block: whether its light value or opacity depends on the position (a subclass overrides the Forge hook). */
    private static final Map<Block, Integer> DYNAMIC = new IdentityHashMap<>();
    private static final int DYNAMIC_LIGHT = 1;
    private static final int DYNAMIC_OPACITY = 2;

    static int dynamic(Block b) {
        Integer v = DYNAMIC.get(b);
        if (v == null) {
            int flags = 0;
            if (overrides(b.getClass(), "getLightValue")) flags |= DYNAMIC_LIGHT;
            if (overrides(b.getClass(), "getLightOpacity")) flags |= DYNAMIC_OPACITY;
            DYNAMIC.put(b, v = flags);
        }
        return v;
    }

    private static boolean overrides(Class<?> c, String name) {
        // the dev name of the Forge hooks; in production Forge keeps these names too (they are Forge methods)
        for (Class<?> k = c; k != null && k != Block.class; k = k.getSuperclass()) {
            try {
                k.getDeclaredMethod(name, IBlockAccess.class, int.class, int.class, int.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                // keep looking up the hierarchy
            } catch (LinkageError e) {
                return true; // cannot tell: ask the block every time
            }
        }
        return false;
    }

    static int emission(Block b, WorldServer world, int x, int y, int z) {
        return (dynamic(b) & DYNAMIC_LIGHT) != 0 ? b.getLightValue(world, x, y, z) : b.getLightValue();
    }

    static int opacity(Block b, WorldServer world, int x, int y, int z) {
        return (dynamic(b) & DYNAMIC_OPACITY) != 0 ? b.getLightOpacity(world, x, y, z) : b.getLightOpacity();
    }

    // ------------------------------------------------------------------------------------------- flood fill

    /** Block light flood fill from the sources of the centre chunk over its 3x3 neighbourhood, {@code minY..-1}. */
    static final class Flood {

        private final WorldServer world;
        private final Chunk[] core;
        private final int originX;
        private final int originZ;
        private final int minY;
        private final int height;
        private final ExtendedBlockStorage[][] sections;
        private final int[] touched = new int[9];

        Flood(WorldServer world, Chunk[] core, int cx, int cz, int minY) {
            this.world = world;
            this.core = core;
            this.originX = (cx - 1) << 4;
            this.originZ = (cz - 1) << 4;
            this.minY = minY;
            this.height = -minY;
            this.sections = new ExtendedBlockStorage[9][this.height >> 4];
            for (int i = 0; i < 9; i++) {
                for (int sy = minY >> 4; sy < 0; sy++) {
                    this.sections[i][sy - (minY >> 4)] = BellowAPI.getDeepSection(core[i], sy);
                }
            }
        }

        private ExtendedBlockStorage section(int lx, int y, int lz) {
            return this.sections[(lz >> 4) * 3 + (lx >> 4)][(y - this.minY) >> 4];
        }

        private int index(int lx, int y, int lz) {
            return ((y - this.minY) * 48 + lz) * 48 + lx;
        }

        void run() {
            int[] queue = new int[4096];
            int head = 0;
            int tail = 0;
            // sources of the centre chunk (local x/z 16..31)
            for (int y = this.minY; y < 0; y++) {
                for (int lz = 16; lz < 32; lz++) {
                    for (int lx = 16; lx < 32; lx++) {
                        ExtendedBlockStorage ebs = this.section(lx, y, lz);
                        if (ebs == null) continue;
                        Block b = ebs.getBlockByExtId(lx & 15, y & 15, lz & 15);
                        if (b.getMaterial() == Material.air) continue;
                        int light = emission(b, this.world, this.originX + lx, y, this.originZ + lz);
                        if (light <= 0) continue;
                        if (this.raise(ebs, lx, y, lz, light)) {
                            if (tail == queue.length) queue = java.util.Arrays.copyOf(queue, queue.length * 2);
                            queue[tail++] = this.index(lx, y, lz);
                        }
                    }
                }
            }
            while (head < tail) {
                int p = queue[head++];
                int lx = p % 48;
                int lz = p / 48 % 48;
                int y = p / (48 * 48) + this.minY;
                int value = this.section(lx, y, lz)
                    .getExtBlocklightValue(lx & 15, y & 15, lz & 15);
                if (value <= 1) continue;
                for (int side = 0; side < 6; side++) {
                    int nx = lx + DX[side];
                    int ny = y + DY[side];
                    int nz = lz + DZ[side];
                    if (nx < 0 || nz < 0 || nx >= 48 || nz >= 48 || ny < this.minY || ny >= 0) continue;
                    ExtendedBlockStorage ebs = this.section(nx, ny, nz);
                    if (ebs == null) continue;
                    Block b = ebs.getBlockByExtId(nx & 15, ny & 15, nz & 15);
                    int wx = this.originX + nx;
                    int wz = this.originZ + nz;
                    int opacity = opacity(b, this.world, wx, ny, wz);
                    if (opacity >= 15 && b.getMaterial() != Material.air && emission(b, this.world, wx, ny, wz) > 0) {
                        opacity = 1;
                    }
                    if (opacity < 1) opacity = 1;
                    if (opacity >= 15) continue;
                    int next = value - opacity;
                    if (next > 0 && this.raise(ebs, nx, ny, nz, next)) {
                        if (tail == queue.length) {
                            // compact the consumed head before growing
                            System.arraycopy(queue, head, queue, 0, tail - head);
                            tail -= head;
                            head = 0;
                            if (tail == queue.length) queue = java.util.Arrays.copyOf(queue, queue.length * 2);
                        }
                        queue[tail++] = this.index(nx, ny, nz);
                    }
                }
            }
            for (int i = 0; i < 9; i++) {
                if (this.touched[i] == 0) continue;
                this.core[i].setChunkModified();
                BellowAPI.resendDeepSections(this.world, this.core[i], this.touched[i]);
            }
        }

        private boolean raise(ExtendedBlockStorage ebs, int lx, int y, int lz, int value) {
            if (ebs.getExtBlocklightValue(lx & 15, y & 15, lz & 15) >= value) return false;
            ebs.setExtBlocklightValue(lx & 15, y & 15, lz & 15, value);
            this.touched[(lz >> 4) * 3 + (lx >> 4)] |= 1 << DeepConstants.slot(y >> 4);
            return true;
        }
    }

    static final int[] DX = { 0, 0, 0, 0, -1, 1 };
    static final int[] DY = { -1, 1, 0, 0, 0, 0 };
    static final int[] DZ = { 0, 0, -1, 1, 0, 0 };

    // ------------------------------------------------------------------------------------------- persistence

    /** Saves and restores the marks in the chunk's {@code Level} compound, and queues loaded chunks for a retry. */
    public static final class Events {

        @SubscribeEvent
        public void onChunkDataLoad(ChunkDataEvent.Load event) {
            NBTTagCompound level = event.getData()
                .getCompoundTag("Level");
            if (!level.hasKey(NBT_KEY, 10)) return;
            NBTTagCompound tag = level.getCompoundTag(NBT_KEY);
            if (tag.getInteger("V") > NBT_VERSION) return;
            if (tag.getBoolean("Ours")) OURS.add(event.getChunk());
            if (tag.getBoolean("Lit")) LIT.add(event.getChunk());
        }

        @SubscribeEvent
        public void onChunkSave(ChunkDataEvent.Save event) {
            boolean ours = OURS.contains(event.getChunk());
            boolean lit = LIT.contains(event.getChunk());
            if (!ours && !lit) return;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("V", NBT_VERSION);
            tag.setBoolean("Ours", ours);
            tag.setBoolean("Lit", lit);
            event.getData()
                .getCompoundTag("Level")
                .setTag(NBT_KEY, tag);
        }

        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load event) {
            if (!(event.world instanceof WorldServer) || !BellowAPI.hasDeepLayers(event.world)) return;
            Chunk c = event.getChunk();
            if (((IDeepChunk) c).bellow$getGenState() != DeepConstants.GEN_DECORATED) return;
            long key = (long) c.xPosition << 32 | c.zPosition & 0xFFFFFFFFL;
            synchronized (PENDING) {
                PENDING.computeIfAbsent(event.world.provider.dimensionId, k -> new LinkedHashSet<>())
                    .add(key);
            }
        }

        /** FML bus: runs the retries after the world tick, outside any chunk load. */
        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.world instanceof WorldServer) {
                processPending((WorldServer) event.world);
            }
        }
    }

    /** True for the still or flowing fluids of vanilla. */
    static boolean isFluid(Block b) {
        return b == Blocks.water || b == Blocks.flowing_water || b == Blocks.lava || b == Blocks.flowing_lava;
    }
}
