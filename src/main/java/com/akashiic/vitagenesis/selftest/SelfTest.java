package com.akashiic.vitagenesis.selftest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.vitagenesis.CaveBlocks;
import com.akashiic.vitagenesis.CaveConfig;
import com.akashiic.vitagenesis.GenStats;
import com.akashiic.vitagenesis.VitaGenesis;
import com.akashiic.vitagenesis.feature.CaveBiomes;
import com.akashiic.vitagenesis.feature.CaveFeatures;
import com.akashiic.vitagenesis.light.DeepLighting;

/**
 * Headless verification on a dedicated server ({@code tools/selftest.sh}), in separate JVMs:
 * <ul>
 * <li>{@code bootstrap}: Below Core generation off; area A is generated as a 1.7.10 world without deep layer.</li>
 * <li>{@code full}: area A is loaded from disk (retrogen) and area B generated fresh; then the nearest lush area (C)
 * and
 * dripstone area (D) are found and generated. All are measured and checked, and a checksum of their deep layer is
 * written.</li>
 * <li>{@code reload}: a new JVM reads every area back and compares the checksum (blocks, metadata, block light) and the
 * "lit" marks.</li>
 * </ul>
 * The report is {@code vitagenesis-selftest-<phase>.txt} in the server directory.
 */
public final class SelfTest {

    /** Loaded radius around an area centre; chunks within LOADED - 3 are lit (5x5 decorated, each 3x3 with terrain). */
    static final int LOADED = 7;
    static final int MEASURED = LOADED - 3;
    static final int[] AREA_A = { 40, 40 };
    static final int[] AREA_B = { -60, 90 };
    /** Per-chunk checksums of area A at Y >= 0, written by bootstrap, compared after the retrogen. */
    static final String SURFACE_FILE = "vitagenesis-selftest-surface.txt";
    /** Chunks of area A whose population and that of their neighbours ended in bootstrap. */
    static final int SURFACE_RADIUS = LOADED - 2;

    /** Area of the seam test, far from the other areas and from spawn. */
    static final int[] AREA_E = { 120, -120 };
    static final String CHECKSUM_FILE = "vitagenesis-selftest-checksum.txt";

    private final List<String> lines = new ArrayList<>();
    private int failures;

    private SelfTest() {}

    public static void start() {
        MinecraftServer server = MinecraftServer.getServer();
        String phase = System.getProperty("vitagenesis.selftest.phase", "full");
        SelfTest test = new SelfTest();
        try {
            test.run(server.worldServerForDimension(0), phase);
        } catch (Throwable t) {
            test.fail("exception: " + t);
            VitaGenesis.LOG.error("[selftest] crashed", t);
        }
        test.line(test.failures == 0 ? "RESULT PASS" : "RESULT FAIL (" + test.failures + ")");
        for (String l : test.lines) VitaGenesis.LOG.info("[selftest] {}", l);
        try {
            Files
                .write(new File("vitagenesis-selftest-" + phase + ".txt").toPath(), test.lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            VitaGenesis.LOG.error("Cannot write the self test report", e);
        }
        if (Boolean.getBoolean("vitagenesis.selftest.exit")) server.initiateShutdown();
    }

    private void line(String s) {
        this.lines.add(s);
    }

    private void check(boolean ok, String what) {
        this.line((ok ? "PASS " : "FAIL ") + what);
        if (!ok) this.failures++;
    }

    private void fail(String what) {
        this.check(false, what);
    }

    private void run(WorldServer world, String phase) throws IOException {
        int minY = BellowAPI.getMinY(world);
        this.line("phase " + phase + ", floor " + minY + ", generators " + BellowAPI.getGeneratorIds());
        switch (phase) {
            case "bootstrap":
                this.check(minY < 0, "dimension 0 has a deep layer");
                long t0 = System.nanoTime();
                load(world, AREA_A);
                world.theChunkProviderServer.saveChunks(true, null);
                this.line(
                    String.format("area A generated without deep layer in %.1f s", (System.nanoTime() - t0) / 1e9));
                Chunk c = world.getChunkFromChunkCoords(AREA_A[0], AREA_A[1]);
                this.check(
                    ((IDeepChunk) c).bellow$getGenState() == DeepConstants.GEN_NONE,
                    "area A has no deep layer yet");
                Files.write(new File(SURFACE_FILE).toPath(), surfaceSums(world).getBytes(StandardCharsets.UTF_8));
                return;
            case "full":
                this.full(world, minY);
                return;
            case "reload":
                this.reload(world, minY);
                return;
            default:
                this.fail("unknown phase " + phase);
        }
    }

    static void load(WorldServer world, int[] centre) {
        load(world, centre, LOADED);
    }

    public static void load(WorldServer world, int[] centre, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++)
                world.theChunkProviderServer.loadChunk(centre[0] + dx, centre[1] + dz);
        }
        // the light stage runs after the world tick; the self test runs before the first tick
        while (DeepLighting.processPending(world));
    }

    private void full(WorldServer world, int minY) throws IOException {
        this.check(minY < 0, "dimension 0 has a deep layer");
        this.check(
            BellowAPI.getGeneratorIds()
                .contains(VitaGenesis.MODID + ":caves"),
            "generator registered with Below Core");
        long t0 = System.nanoTime();
        load(world, AREA_A);
        long t1 = System.nanoTime();
        this.surfaceUnchanged(world);
        load(world, AREA_B);
        long t2 = System.nanoTime();
        int chunks = (2 * LOADED + 1) * (2 * LOADED + 1);
        this.line(
            String.format(
                "area A (retrogen from disk): %.1f ms per chunk; area B (new): %.1f ms per chunk",
                (t1 - t0) / 1e6 / chunks,
                (t2 - t1) / 1e6 / chunks));
        this.measure(world, minY, AREA_A, "A retrogen");
        this.measure(world, minY, AREA_B, "B new");
        List<int[]> areas = new ArrayList<>();
        areas.add(AREA_A);
        areas.add(AREA_B);
        CaveFeatures features = new CaveFeatures(world.getSeed());
        String[] names = { "C lush", "D dripstone" };
        int[] kinds = { CaveBiomes.LUSH, CaveBiomes.DRIPSTONE };
        Block[] markers = { CaveBlocks.mossBlock, CaveBlocks.dripstoneBlock };
        for (int i = 0; i < 2; i++) {
            if (markers[i] == null) {
                this.line("area " + names[i] + " skipped: its Et Futurum blocks are disabled");
                continue;
            }
            int[] area = findArea(world, features, kinds[i]);
            if (area == null) {
                this.fail("no " + names[i] + " area found within 600 chunks");
                continue;
            }
            load(world, area);
            Map<String, Integer> histogram = this
                .measure(world, minY, area, names[i] + " at chunk " + area[0] + "," + area[1]);
            int count = histogramPrefix(histogram, Block.blockRegistry.getNameForObject(markers[i]));
            this.check(
                count > 0,
                names[i] + " caves generated: " + count + " " + Block.blockRegistry.getNameForObject(markers[i]));
            areas.add(area);
        }
        this.seamSetup(world);
        this.notOurs(world);
        this.line(GenStats.report() + " (light: per lit chunk)");
        world.theChunkProviderServer.saveChunks(true, null);
        StringBuilder sums = new StringBuilder();
        for (int[] area : areas) sums.append(area[0])
            .append(' ')
            .append(area[1])
            .append(' ')
            .append(checksum(world, minY, area))
            .append('\n');
        Files.write(
            new File(CHECKSUM_FILE).toPath(),
            sums.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * First half of the seam test: the centre of area E has its whole 5x5 decorated, and its lit mark is removed, as
     * when its light stage could not run because a neighbour was unloaded at the time.
     */
    private void seamSetup(WorldServer world) {
        load(world, AREA_E);
        Chunk target = world.getChunkFromChunkCoords(AREA_E[0], AREA_E[1]);
        this.check(DeepLighting.isLit(target), "seam set up: area E centre lit");
        DeepLighting.forgetLitForTests(target);
    }

    /**
     * Second half, in a new JVM: the 5x5 around the centre of E is loaded without its west neighbour, so the centre
     * cannot be lit. The neighbour then comes back from disk already decorated, which decorates nothing: only the
     * retry queue (fed by chunk loads) can light the centre.
     */
    private void seamReload(WorldServer world) {
        int ex = AREA_E[0];
        int ez = AREA_E[1];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx != -2 || dz != 0) world.theChunkProviderServer.loadChunk(ex + dx, ez + dz);
            }
        }
        while (DeepLighting.processPending(world));
        Chunk target = world.getChunkFromChunkCoords(ex, ez);
        boolean before = DeepLighting.isLit(target);
        world.theChunkProviderServer.loadChunk(ex - 2, ez);
        while (DeepLighting.processPending(world));
        boolean after = DeepLighting.isLit(target);
        this.check(
            !before && after,
            "chunk lit once a decorated neighbour comes back from disk (lit before " + before
                + ", after "
                + after
                + ")");
    }

    /**
     * A chunk decorated by Below Core without VitaGenesis (before it was installed) may hold what players built: the
     * light stage must leave it alone. Area F is generated with VitaGenesis off, a still water source is put next to
     * an air room in it (as a player could), then the light stage runs on it with VitaGenesis on.
     */
    private void notOurs(WorldServer world) {
        int[] f = { -130, -130 };
        boolean enabled = CaveConfig.enabled;
        CaveConfig.enabled = false;
        try {
            load(world, f, 4);
        } finally {
            CaveConfig.enabled = enabled;
        }
        int x0 = (f[0] << 4) + 6;
        int z0 = (f[1] << 4) + 6;
        int y0 = -30;
        for (int x = x0; x < x0 + 3; x++) {
            for (int y = y0; y < y0 + 3; y++) {
                for (int z = z0; z < z0 + 3; z++) world.setBlock(x, y, z, Blocks.air, 0, 2);
            }
        }
        world.setBlock(x0 - 1, y0, z0, Blocks.water, 0, 2);
        Chunk centre = world.getChunkFromChunkCoords(f[0], f[1]);
        DeepLighting.onDecorated(world, centre);
        while (DeepLighting.processPending(world));
        this.check(
            world.getBlock(x0 - 1, y0, z0) == Blocks.water && !DeepLighting.isLit(centre),
            "chunk decorated without VitaGenesis left alone by the light stage (water still: "
                + (world.getBlock(x0 - 1, y0, z0) == Blocks.water)
                + ", lit: "
                + DeepLighting.isLit(centre)
                + ")");
    }

    /** The nearest area (from the origin) where at least half of a 5x5 sample of columns has the cave biome. */
    public static int[] findArea(WorldServer world, CaveFeatures features, int kind) {
        for (int r = 0; r <= 600; r += 6) {
            for (int a = -r; a <= r; a += 6) {
                int[][] candidates = { { a, -r }, { a, r }, { -r, a }, { r, a } };
                for (int[] c : candidates) {
                    int hits = 0;
                    for (int i = -2; i <= 2; i++) {
                        for (int j = -2; j <= 2; j++) {
                            if (features.caveBiome(world, (c[0] << 4) + 8 + i * 32, (c[1] << 4) + 8 + j * 32) == kind)
                                hits++;
                        }
                    }
                    if (hits >= 13) return c;
                }
            }
        }
        return null;
    }

    private void reload(WorldServer world, int minY) throws IOException {
        List<int[]> areas = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (String l : Files.readAllLines(new File(CHECKSUM_FILE).toPath(), StandardCharsets.UTF_8)) {
            String[] f = l.trim()
                .split(" ");
            if (f.length != 3) continue;
            areas.add(new int[] { Integer.parseInt(f[0]), Integer.parseInt(f[1]) });
            expected.add(f[2]);
        }
        for (int i = 0; i < areas.size(); i++) {
            int[] area = areas.get(i);
            load(world, area);
            String actual = checksum(world, minY, area);
            this.check(
                expected.get(i)
                    .equals(actual),
                "area at chunk " + area[0]
                    + ","
                    + area[1]
                    + " read back unchanged (blocks, metadata, block light): "
                    + actual);
        }
        int lit = 0;
        int total = 0;
        for (int[] area : areas) {
            for (int dx = -MEASURED; dx <= MEASURED; dx++) {
                for (int dz = -MEASURED; dz <= MEASURED; dz++) {
                    total++;
                    if (DeepLighting.isLit(world.getChunkFromChunkCoords(area[0] + dx, area[1] + dz))) lit++;
                }
            }
        }
        this.check(lit == total, "lit marks restored from disk: " + lit + "/" + total);
        this.seamReload(world);
    }

    /**
     * Blocks and metadata at Y >= 0 of the chunks of area A that are fully populated (their neighbours too, so no later
     * population writes into them). The old bedrock at Y 0..4 may become the deep block (Below Core's documented
     * legacy bedrock replacement): bedrock and deepslate hash the same there.
     */
    private static String surfaceSums(WorldServer world) {
        StringBuilder out = new StringBuilder();
        for (int dx = -SURFACE_RADIUS; dx <= SURFACE_RADIUS; dx++) {
            for (int dz = -SURFACE_RADIUS; dz <= SURFACE_RADIUS; dz++) {
                Chunk c = world.getChunkFromChunkCoords(AREA_A[0] + dx, AREA_A[1] + dz);
                CRC32 crc = new CRC32();
                ExtendedBlockStorage[] sections = c.getBlockStorageArray();
                for (int sy = 0; sy < sections.length; sy++) {
                    ExtendedBlockStorage ebs = sections[sy];
                    if (ebs == null) continue;
                    for (int i = 0; i < 4096; i++) {
                        int x = i & 15;
                        int z = i >> 4 & 15;
                        int y = i >> 8;
                        Block b = ebs.getBlockByExtId(x, y, z);
                        boolean legacyFloor = sy == 0 && y <= 4 && (b == Blocks.bedrock || b == CaveBlocks.deepslate);
                        crc.update(legacyFloor ? -1 : Block.getIdFromBlock(b));
                        crc.update(legacyFloor ? 0 : ebs.getExtBlockMetadata(x, y, z));
                    }
                }
                out.append(c.xPosition)
                    .append(' ')
                    .append(c.zPosition)
                    .append(' ')
                    .append(Long.toHexString(crc.getValue()))
                    .append('\n');
            }
        }
        return out.toString();
    }

    /** The golden rule, measured: the retrogen changed nothing at Y >= 0 of the pre-generated area. */
    private void surfaceUnchanged(WorldServer world) throws IOException {
        File f = new File(SURFACE_FILE);
        if (!f.isFile()) {
            this.fail("no surface checksums from the bootstrap phase");
            return;
        }
        List<String> before = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        String[] after = surfaceSums(world).split("\n");
        int changed = 0;
        StringBuilder which = new StringBuilder();
        for (int i = 0; i < after.length; i++) {
            if (i >= before.size() || !before.get(i)
                .equals(after[i])) {
                changed++;
                if (changed <= 5) which.append(' ')
                    .append(after[i], 0, after[i].lastIndexOf(' '));
            }
        }
        this.check(
            changed == 0,
            "retrogen left Y >= 0 of " + after.length
                + " pre-generated chunks unchanged: "
                + changed
                + " changed"
                + which);
    }

    /** Checksum of blocks, metadata and block light of the measured chunks, below Y 0. */
    private static String checksum(WorldServer world, int minY, int[] area) {
        CRC32 crc = new CRC32();
        for (int dx = -MEASURED; dx <= MEASURED; dx++) {
            for (int dz = -MEASURED; dz <= MEASURED; dz++) {
                Chunk c = world.getChunkFromChunkCoords(area[0] + dx, area[1] + dz);
                for (int sy = minY >> 4; sy < 0; sy++) {
                    ExtendedBlockStorage ebs = BellowAPI.getDeepSection(c, sy);
                    if (ebs == null) continue;
                    for (int i = 0; i < 4096; i++) {
                        int x = i & 15;
                        int z = i >> 4 & 15;
                        int y = i >> 8;
                        crc.update(Block.getIdFromBlock(ebs.getBlockByExtId(x, y, z)));
                        crc.update(ebs.getExtBlockMetadata(x, y, z));
                        crc.update(ebs.getExtBlocklightValue(x, y, z));
                    }
                }
            }
        }
        return Long.toHexString(crc.getValue());
    }

    private Map<String, Integer> measure(WorldServer world, int minY, int[] area, String name) {
        int ceiling = CaveConfig.settings()
            .ceiling();
        Map<String, Integer> histogram = new TreeMap<>();
        int[] openByBand = new int[(-minY + 7) / 8];
        int open = 0;
        int volume = 0;
        int floorBroken = 0;
        int sealBroken = 0;
        int emitters = 0;
        int emittersDark = 0;
        int notLit = 0;
        int decorated = 0;
        int columns = (2 * MEASURED + 1) * (2 * MEASURED + 1);
        List<int[]> lightSamples = new ArrayList<>();
        for (int dx = -MEASURED; dx <= MEASURED; dx++) {
            for (int dz = -MEASURED; dz <= MEASURED; dz++) {
                Chunk c = world.getChunkFromChunkCoords(area[0] + dx, area[1] + dz);
                if (((IDeepChunk) c).bellow$getGenState() == DeepConstants.GEN_DECORATED) decorated++;
                if (!DeepLighting.isLit(c)) notLit++;
                for (int y = minY; y < 0; y++) {
                    ExtendedBlockStorage ebs = BellowAPI.getDeepSection(c, y >> 4);
                    if (ebs == null) continue;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            Block b = ebs.getBlockByExtId(x, y & 15, z);
                            int meta = ebs.getExtBlockMetadata(x, y & 15, z);
                            volume++;
                            Material m = b.getMaterial();
                            boolean isOpen = m == Material.air || m.isLiquid();
                            if (isOpen) {
                                open++;
                                openByBand[(y - minY) / 8]++;
                            }
                            if (y == minY && b != Blocks.bedrock) floorBroken++;
                            if (y > ceiling && isOpen) sealBroken++;
                            String key = Block.blockRegistry.getNameForObject(b) + (meta != 0 ? ":" + meta : "");
                            histogram.merge(key, 1, Integer::sum);
                            int wx = (c.xPosition << 4) + x;
                            int wz = (c.zPosition << 4) + z;
                            int light = b.getLightValue(world, wx, y, wz);
                            if (light > 0) {
                                emitters++;
                                if (ebs.getExtBlocklightValue(x, y & 15, z) < light) emittersDark++;
                                if (lightSamples.size() < 4000 && (wx * 31 + y * 17 + wz) % 7 == 0) {
                                    lightSamples.add(new int[] { wx, y, wz });
                                }
                            }
                        }
                    }
                }
            }
        }
        this.line("--- area " + name + " (" + columns + " chunks)");
        double fraction = open / (double) volume;
        StringBuilder bands = new StringBuilder();
        for (int i = 0; i < openByBand.length; i++) {
            bands.append(String.format(" %d:%.2f", minY + i * 8, openByBand[i] / (8.0D * 256 * columns)));
        }
        this.line(String.format("open (air + fluids) %.3f; by Y:%s", fraction, bands));
        this.check(decorated == columns, "all measured chunks decorated: " + decorated + "/" + columns);
        this.check(notLit == 0, "all measured chunks lit: " + (columns - notLit) + "/" + columns);
        this.check(floorBroken == 0, "floor (Y " + minY + ") is bedrock everywhere: " + floorBroken + " broken");
        this.check(sealBroken == 0, "seal below Y 0 (Y " + (ceiling + 1) + "..-1) is solid: " + sealBroken + " open");
        // 9x9-chunk areas of the 1.18 noise range from 1.7 % to 17 % open at floor -64 (60 random areas, two seeds):
        // the check only catches a generator that does nothing or carves everything
        this.check(
            fraction > 0.01D && fraction < 0.30D,
            String.format("cave volume plausible (1%%..30%%): %.3f", fraction));
        int lavaLow = 0;
        for (Map.Entry<String, Integer> e : histogram.entrySet()) {
            if (e.getKey()
                .contains("lava")) lavaLow += e.getValue();
        }
        this.check(lavaLow > 0, "lava in the deep layer: " + lavaLow);
        this.check(
            emittersDark == 0,
            "every light source carries its own light: " + emittersDark + " of " + emitters + " dark");
        this.lightCheck(world, lightSamples);
        StringBuilder h = new StringBuilder("blocks:");
        for (Map.Entry<String, Integer> e : histogram.entrySet()) h.append(' ')
            .append(e.getKey())
            .append('=')
            .append(e.getValue());
        this.line(h.toString());
        if (CaveBlocks.glowLichen != null) {
            String lichen = Block.blockRegistry.getNameForObject(CaveBlocks.glowLichen);
            this.check(histogramPrefix(histogram, lichen) > 0, "glow lichen generated");
        }
        return histogram;
    }

    private static int histogramPrefix(Map<String, Integer> histogram, String prefix) {
        int n = 0;
        for (Map.Entry<String, Integer> e : histogram.entrySet()) {
            if (e.getKey()
                .startsWith(prefix)) n += e.getValue();
        }
        return n;
    }

    /**
     * Recomputes the block light around sampled light sources with the world's own engine: if the stage wrote the
     * values vanilla would compute, the engine changes nothing.
     */
    private void lightCheck(WorldServer world, List<int[]> samples) {
        int cells = 0;
        int changed = 0;
        for (int[] s : samples) {
            int[] before = new int[27];
            int k = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                before[k++] = world.getSavedLightValue(EnumSkyBlock.Block, s[0] + dx, s[1] + dy, s[2] + dz);
            }
            world.updateLightByType(EnumSkyBlock.Block, s[0], s[1], s[2]);
            k = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                cells++;
                if (world.getSavedLightValue(EnumSkyBlock.Block, s[0] + dx, s[1] + dy, s[2] + dz) != before[k++])
                    changed++;
            }
        }
        this.check(
            samples.isEmpty() || changed <= cells / 100,
            "block light matches the vanilla engine around " + samples
                .size() + " sources: " + changed + " of " + cells + " cells changed when relit");
    }
}
