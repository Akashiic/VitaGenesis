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
import com.akashiic.vitagenesis.layer.DeepLayer;
import com.akashiic.vitagenesis.layer.Ores;

import cpw.mods.fml.common.registry.GameRegistry;

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
    static final String ORE_SURVEY_FILE = "vitagenesis-ore-survey.txt";
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
                Files.write(new File(ORE_SURVEY_FILE).toPath(), oreSurvey(world).getBytes(StandardCharsets.UTF_8));
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
        // Below Core runs the stages after decoration (light, onLit) after the world tick; the self test runs before
        BellowAPI.runDeepStages(world);
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
        this.emptyLayer(world, minY);
        this.line(GenStats.report() + " (fluids: onLit, per lit chunk; the light stage itself is Below Core's)");
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

    /** Whether Below Core lit the chunk and called VitaGenesis' {@code onLit} on it. */
    static boolean lit(Chunk c) {
        return (BellowAPI.getGeneratorStages(c, VitaGenesis.GENERATOR_ID) & DeepConstants.STAGE_ON_LIT) != 0
            && !BellowAPI.isLightPending(c);
    }

    /**
     * Chunk at the east edge of what area E decorates: decorated, but its 5x5 is not, so its light stage waits when
     * the server stops.
     */
    private static int[] seamTarget() {
        return new int[] { AREA_E[0] + LOADED - 1, AREA_E[1] };
    }

    /** First half of the seam test: the target is decorated and saved with its light stage pending. */
    private void seamSetup(WorldServer world) {
        load(world, AREA_E);
        int[] t = seamTarget();
        Chunk target = world.getChunkFromChunkCoords(t[0], t[1]);
        this.check(
            BellowAPI.isDecorated(target) && BellowAPI.isLightPending(target) && !lit(target),
            "seam set up: chunk at the edge decorated, light stage pending");
    }

    /**
     * Second half, in a new JVM: the target comes back from disk with its light pending; it is lit (and its fluids
     * started) once the chunks east of it are generated and decorated, and not before.
     */
    private void seamReload(WorldServer world) {
        int[] t = seamTarget();
        load(world, t, 1);
        Chunk target = world.getChunkFromChunkCoords(t[0], t[1]);
        boolean before = lit(target);
        load(world, t, 3);
        boolean after = lit(target);
        this.check(
            !before && after,
            "chunk saved with its light pending lit once its 5x5 is decorated (lit before " + before
                + ", after "
                + after
                + ")");
    }

    /**
     * VitaGenesis makes the layer on its own from what Below Core hands over (empty sections, or its provisional
     * stone): the
     * deep layer of one chunk is emptied (as Below Core will hand it over), two blocks a player could have placed are
     * put in it, and the layer stage runs again. Everything else must be the fill or the 1.18 bedrock floor.
     */
    private void emptyLayer(WorldServer world, int minY) {
        int[] g = { 200, 200 };
        load(world, g, 0);
        Chunk c = world.getChunkFromChunkCoords(g[0], g[1]);
        for (int sy = minY >> 4; sy < 0; sy++) {
            ExtendedBlockStorage ebs = BellowAPI.getDeepSection(c, sy);
            for (int i = 0; i < 4096; i++) ebs.func_150818_a(i & 15, i >> 8, i >> 4 & 15, Blocks.air);
        }
        BellowAPI.getDeepSection(c, -2)
            .func_150818_a(5, 2, 5, Blocks.glass); // y -30
        BellowAPI.getDeepSection(c, minY >> 4)
            .func_150818_a(7, 1, 7, Blocks.glass); // floor band
        DeepLayer.generate(c, minY, DeepLayer.bedrockRandom(world.getSeed()));
        int glass = 0;
        int other = 0;
        int bedrock = 0;
        int floorBroken = 0;
        for (int y = minY; y < 0; y++) {
            ExtendedBlockStorage ebs = BellowAPI.getDeepSection(c, y >> 4);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Block b = ebs.getBlockByExtId(x, y & 15, z);
                    if (b == Blocks.glass) glass++;
                    else if (b == Blocks.bedrock) bedrock++;
                    else if (b != DeepLayer.fill || ebs.getExtBlockMetadata(x, y & 15, z) != DeepLayer.fillMeta)
                        other++;
                    if (y == minY && b != Blocks.bedrock) floorBroken++;
                }
            }
        }
        // 256 * (1 + 0.8 + 0.6 + 0.4 + 0.2) = 768 bedrock blocks expected
        this.check(
            glass == 2 && other == 0 && floorBroken == 0 && bedrock > 650 && bedrock < 880,
            "empty deep layer made by VitaGenesis: placed blocks kept " + glass
                + "/2, other blocks "
                + other
                + ", floor broken "
                + floorBroken
                + ", bedrock "
                + bedrock
                + " (~768)");
    }

    /**
     * A chunk decorated while VitaGenesis was off may hold what players built: its fluids must not be started. Area F
     * is decorated with VitaGenesis off (its 5x5 not yet, so it is not lit), a still water source is put next to an
     * air room in it (as a player could), then its 5x5 is decorated with VitaGenesis on, which lights it and calls
     * {@code onLit}.
     */
    private void notOurs(WorldServer world) {
        int[] f = { -130, -130 };
        boolean enabled = CaveConfig.enabled;
        CaveConfig.enabled = false;
        try {
            load(world, f, 2);
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
        boolean before = lit(centre);
        load(world, f, 4);
        // a started source turns into flowing water (BlockStaticLiquid.setNotStationary)
        this.check(
            !before && lit(centre) && world.getBlock(x0 - 1, y0, z0) == Blocks.water,
            "chunk decorated while VitaGenesis was off: lit, fluids left alone (lit before " + before
                + ", after "
                + lit(centre)
                + ", water still: "
                + (world.getBlock(x0 - 1, y0, z0) == Blocks.water)
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
                    if (lit(world.getChunkFromChunkCoords(area[0] + dx, area[1] + dz))) lit++;
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
    /**
     * What the pack's own generators put at Y >= 0 of the pre-generated area A, for every ore Et Futurum has a
     * deepslate variant of: blocks per chunk, the Y range and the mean Y. The defaults of {@code layer.moddedOres}
     * come from it.
     */
    private static String oreSurvey(WorldServer world) {
        Map<Long, Ores.EfrOre> keys = new java.util.HashMap<>();
        for (Ores.EfrOre o : Ores.etFuturumDeepslateMap())
            keys.put((long) Block.getIdFromBlock(o.from) << 4 | o.fromMeta & 15, o);
        Map<Long, int[]> stats = new TreeMap<>(); // count, minY, maxY, sumY
        int chunks = 0;
        for (int dx = -SURFACE_RADIUS; dx <= SURFACE_RADIUS; dx++) {
            for (int dz = -SURFACE_RADIUS; dz <= SURFACE_RADIUS; dz++) {
                chunks++;
                ExtendedBlockStorage[] sections = world.getChunkFromChunkCoords(AREA_A[0] + dx, AREA_A[1] + dz)
                    .getBlockStorageArray();
                for (int sy = 0; sy < sections.length; sy++) {
                    ExtendedBlockStorage ebs = sections[sy];
                    if (ebs == null) continue;
                    for (int i = 0; i < 4096; i++) {
                        int x = i & 15;
                        int y = i >> 8;
                        int z = i >> 4 & 15;
                        Block b = ebs.getBlockByExtId(x, y, z);
                        long key = (long) Block.getIdFromBlock(b) << 4 | ebs.getExtBlockMetadata(x, y, z) & 15;
                        if (!keys.containsKey(key)) continue;
                        int wy = (sy << 4) + y;
                        int[] st = stats.computeIfAbsent(key, k -> new int[] { 0, 999, -999, 0 });
                        st[0]++;
                        st[1] = Math.min(st[1], wy);
                        st[2] = Math.max(st[2], wy);
                        st[3] += wy;
                    }
                }
            }
        }
        StringBuilder out = new StringBuilder(
            "# ore, blocks per chunk, minY, maxY, mean Y -> deepslate variant [ore dictionary]; " + chunks
                + " chunks\n");
        for (Map.Entry<Long, int[]> e : stats.entrySet()) {
            Ores.EfrOre o = keys.get(e.getKey());
            int[] st = e.getValue();
            out.append(GameRegistry.findUniqueIdentifierFor(o.from))
                .append(':')
                .append(o.fromMeta)
                .append(
                    String
                        .format(" %.2f %d %d %.0f -> ", st[0] / (double) chunks, st[1], st[2], st[3] / (double) st[0]))
                .append(GameRegistry.findUniqueIdentifierFor(o.to))
                .append(':')
                .append(o.toMeta)
                .append(' ')
                .append(o.names)
                .append('\n');
        }
        return out.toString();
    }

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
                if (BellowAPI.isDecorated(c)) decorated++;
                if (!lit(c)) notLit++;
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
        // the ores of the layer are VitaGenesis' (Below Core's own are off in the test): 1.18 places about 25
        // deepslate diamond and 20 deepslate iron ore blocks per chunk at floor -64, outside the iron veins
        int chunks = (2 * MEASURED + 1) * (2 * MEASURED + 1);
        int diamond = histogramPrefix(histogram, "etfuturum:deepslate_diamond_ore");
        int redstone = histogramPrefix(histogram, "etfuturum:deepslate_redstone_ore");
        int fill = histogramPrefix(histogram, Block.blockRegistry.getNameForObject(DeepLayer.fill));
        this.check(
            diamond > 5 * chunks && diamond < 80 * chunks && redstone > 5 * chunks,
            "1.18 ores in the layer: " + diamond / chunks
                + " deepslate diamond and "
                + redstone / chunks
                + " deepslate redstone ore per chunk");
        this.check(fill > 0, "layer made of " + Block.blockRegistry.getNameForObject(DeepLayer.fill));
        List<Object[]> modded = Ores.moddedTargets();
        if (!modded.isEmpty()) {
            StringBuilder m = new StringBuilder("ores of other mods per chunk:");
            int missing = 0;
            for (Object[] t : modded) {
                String key = t[1] + ((Integer) t[2] == 0 ? "" : ":" + t[2]);
                Integer n = histogram.get(key);
                if (n == null) missing++;
                m.append(String.format(" %s=%.1f", t[0], (n == null ? 0 : n) / (double) chunks));
            }
            this.line(m.toString());
            this.check(
                missing == 0,
                "every configured ore of other mods placed: " + (modded.size() - missing) + "/" + modded.size());
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
