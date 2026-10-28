package com.akashiic.vitagenesis.terrain;

import com.akashiic.vitagenesis.noise.Mth;

/**
 * Terrain stage of one chunk below Y 0, in 1.18 order: the noise caves with their aquifers and ore veins (one pass over
 * the cells, as {@code NoiseChunk} fills a chunk), then the carvers. Pure function of the seed, the chunk position and
 * the settings, and it only touches this chunk, so chunk borders match whatever order chunks come in.
 * <p>
 * The density is sampled on the 1.18 overworld cell grid (4 x 8 x 4 blocks, aligned to the floor) and interpolated
 * trilinearly. Near the top of the layer the caves close: from {@code ceiling - fade} up to {@code ceiling} the density
 * is raised (and carver ellipsoids shrink) until nothing is carved, and every Y above {@code ceiling} stays solid, so
 * the deep caves never open into the 1.7.10 world above.
 */
public final class DeepCaves {

    /** What the generator sees of the chunk; x and z are chunk-local, y is world Y. */
    public interface Chunk {

        int OTHER = 0;
        /** The deep stone Bellow filled the layer with. */
        int FILL = 1;
        /** Bedrock above the floor (the floor itself reports {@link #OTHER}). */
        int BEDROCK = 2;
        /** A block placed by the ore veins of this pass. */
        int VEIN = 3;

        int kind(int lx, int y, int lz);

        /** One of {@link Aquifer#AIR}, {@link Aquifer#WATER}, {@link Aquifer#LAVA}. */
        void setSubstance(int lx, int y, int lz, byte substance);

        /** One of {@link OreVeins#ORE}, {@link OreVeins#RAW_BLOCK}, {@link OreVeins#FILLER}. */
        void setVein(int lx, int y, int lz, byte vein);
    }

    public static final class Settings {

        public boolean noiseCaves = true;
        public boolean aquifers = true;
        public boolean oreVeins = true;
        public boolean carvers = true;
        public boolean extraUndergroundCarver = true;
        /** Solid layers kept right below Y 0. */
        public int seal = 4;
        /** Blocks below the ceiling over which the caves close. */
        public int fade = 8;

        /** Highest Y that caves and cave features may change. */
        public int ceiling() {
            return -1 - Math.max(this.seal, 1);
        }
    }

    static final int CELL_WIDTH = 4;
    static final int CELL_HEIGHT = 8;
    private static final int CORNERS_XZ = 16 / CELL_WIDTH + 1;
    /** Density added at the ceiling; above the largest negative density the router can produce. */
    static final double CEILING_BIAS = 0.5D;

    private DeepCaves() {}

    public static void generate(CaveNoise noise, int chunkX, int chunkZ, int minY, Settings settings, Chunk chunk) {
        int ceiling = settings.ceiling();
        if (ceiling <= minY) return;
        Aquifer aquifer = new Aquifer(noise, chunkX, chunkZ, minY, -1, settings.aquifers);
        if (settings.noiseCaves || settings.oreVeins) noisePass(noise, chunkX, chunkZ, minY, settings, aquifer, chunk);
        if (settings.carvers) carve(noise.seed, chunkX, chunkZ, minY, settings, aquifer, chunk);
    }

    /** Density added near the ceiling: 0 below the fade band, {@link #CEILING_BIAS} at the ceiling. */
    static double ceilingBias(int y, int ceiling, int fade) {
        double t = (y - (ceiling - fade)) / (double) Math.max(fade, 1);
        if (t <= 0.0D) return 0.0D;
        return CEILING_BIAS * Math.min(t, 1.0D) * Math.min(t, 1.0D);
    }

    private static void noisePass(CaveNoise noise, int chunkX, int chunkZ, int minY, Settings settings, Aquifer aquifer,
        Chunk chunk) {
        int ceiling = settings.ceiling();
        int cellsY = -minY / CELL_HEIGHT;
        int cornersY = cellsY + 1;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int yShift = minY + 64;
        boolean caves = settings.noiseCaves;
        boolean veins = settings.oreVeins;
        OreVeins oreVeins = veins ? new OreVeins(noise, minY) : null;

        int n = CORNERS_XZ * cornersY * CORNERS_XZ;
        double[] base = caves ? new double[n] : null;
        double[] toggle = caves ? new double[n] : null;
        double[] thick = caves ? new double[n] : null;
        double[] ridgeA = caves ? new double[n] : null;
        double[] ridgeB = caves ? new double[n] : null;
        double[] veinToggle = veins ? new double[n] : null;
        double[] veinRidged = veins ? new double[n] : null;
        for (int cx = 0; cx < CORNERS_XZ; cx++) {
            for (int cz = 0; cz < CORNERS_XZ; cz++) {
                for (int cy = 0; cy < cornersY; cy++) {
                    int x = baseX + cx * CELL_WIDTH;
                    int y = minY + cy * CELL_HEIGHT;
                    int z = baseZ + cz * CELL_WIDTH;
                    int i = corner(cx, cy, cz, cornersY);
                    if (caves) {
                        int yRel = y - yShift;
                        base[i] = noise.base(x, y, z, yRel);
                        boolean band = CaveNoise.inNoodleBand(y, yRel);
                        toggle[i] = band ? noise.noodleToggle(x, y, z) : -1.0D;
                        thick[i] = band ? noise.noodleThickness(x, y, z) : 0.0D;
                        ridgeA[i] = band ? noise.noodleRidgeA(x, y, z) : 0.0D;
                        ridgeB[i] = band ? noise.noodleRidgeB(x, y, z) : 0.0D;
                    }
                    if (veins) {
                        veinToggle[i] = oreVeins.toggle(x, y, z);
                        veinRidged[i] = oreVeins.ridged(x, y, z);
                    }
                }
            }
        }

        for (int cellX = 0; cellX < CORNERS_XZ - 1; cellX++) {
            for (int cellZ = 0; cellZ < CORNERS_XZ - 1; cellZ++) {
                for (int cellY = 0; cellY < cellsY; cellY++) {
                    for (int dy = 0; dy < CELL_HEIGHT; dy++) {
                        int y = minY + cellY * CELL_HEIGHT + dy;
                        if (y == minY) continue; // the floor is never touched
                        double fy = dy / (double) CELL_HEIGHT;
                        double bias = ceilingBias(y, ceiling, settings.fade);
                        for (int dx = 0; dx < CELL_WIDTH; dx++) {
                            double fx = dx / (double) CELL_WIDTH;
                            int lx = cellX * CELL_WIDTH + dx;
                            for (int dz = 0; dz < CELL_WIDTH; dz++) {
                                double fz = dz / (double) CELL_WIDTH;
                                int lz = cellZ * CELL_WIDTH + dz;
                                int kind = chunk.kind(lx, y, lz);
                                if (kind != Chunk.FILL && kind != Chunk.BEDROCK) continue;
                                byte substance = Aquifer.SOLID;
                                if (caves && y <= ceiling) {
                                    double density = CaveNoise
                                        .postProcess(lerp(base, cellX, cellY, cellZ, cornersY, fx, fy, fz));
                                    double noodle = CaveNoise.noodle(
                                        lerp(toggle, cellX, cellY, cellZ, cornersY, fx, fy, fz),
                                        lerp(thick, cellX, cellY, cellZ, cornersY, fx, fy, fz),
                                        lerp(ridgeA, cellX, cellY, cellZ, cornersY, fx, fy, fz),
                                        lerp(ridgeB, cellX, cellY, cellZ, cornersY, fx, fy, fz));
                                    density = Math.min(density, noodle) + bias;
                                    substance = aquifer.compute(baseX + lx, y, baseZ + lz, density);
                                }
                                if (substance != Aquifer.SOLID) {
                                    chunk.setSubstance(lx, y, lz, substance);
                                } else if (veins && kind == Chunk.FILL) {
                                    byte vein = oreVeins.compute(
                                        baseX + lx,
                                        y,
                                        baseZ + lz,
                                        lerp(veinToggle, cellX, cellY, cellZ, cornersY, fx, fy, fz),
                                        lerp(veinRidged, cellX, cellY, cellZ, cornersY, fx, fy, fz));
                                    if (vein != OreVeins.NONE) chunk.setVein(lx, y, lz, vein);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static int corner(int cx, int cy, int cz, int cornersY) {
        return (cx * CORNERS_XZ + cz) * cornersY + cy;
    }

    private static double lerp(double[] v, int cellX, int cellY, int cellZ, int cornersY, double fx, double fy,
        double fz) {
        return Mth.lerp3(
            fx,
            fy,
            fz,
            v[corner(cellX, cellY, cellZ, cornersY)],
            v[corner(cellX + 1, cellY, cellZ, cornersY)],
            v[corner(cellX, cellY + 1, cellZ, cornersY)],
            v[corner(cellX + 1, cellY + 1, cellZ, cornersY)],
            v[corner(cellX, cellY, cellZ + 1, cornersY)],
            v[corner(cellX + 1, cellY, cellZ + 1, cornersY)],
            v[corner(cellX, cellY + 1, cellZ + 1, cornersY)],
            v[corner(cellX + 1, cellY + 1, cellZ + 1, cornersY)]);
    }

    private static void carve(long seed, int chunkX, int chunkZ, int minY, Settings settings, Aquifer aquifer,
        Chunk chunk) {
        CaveCarver carver = new CaveCarver(
            seed,
            minY,
            settings.ceiling(),
            settings.fade,
            settings.extraUndergroundCarver);
        carver.carve(chunkX, chunkZ, aquifer, new CaveCarver.Target() {

            @Override
            public boolean carvable(int lx, int y, int lz) {
                int kind = chunk.kind(lx, y, lz);
                return kind == Chunk.FILL || kind == Chunk.VEIN; // 1.18 carvers do not replace bedrock
            }

            @Override
            public void set(int lx, int y, int lz, byte substance) {
                chunk.setSubstance(lx, y, lz, substance);
            }
        });
    }
}
