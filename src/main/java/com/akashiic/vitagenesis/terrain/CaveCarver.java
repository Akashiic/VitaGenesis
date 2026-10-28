package com.akashiic.vitagenesis.terrain;

import java.util.Random;

import com.akashiic.vitagenesis.noise.Mth;

/**
 * 1.18 {@code CaveWorldCarver} with the two overworld configurations that reach below Y 0 ({@code cave} and
 * {@code cave_extra_underground}). The canyon carver starts at Y 10..67 and stays above the deep layer, so it is not
 * ported. Tunnels and rooms are started from every chunk within 8 chunks with the random of
 * {@code setLargeFeatureSeed(seed + index, x, z)}, as {@code ChunkGenerator.applyCarvers} does, and only the blocks of
 * the chunk being generated are written. Tunnels started above the layer are walked the same way and clipped, so the
 * layer looks like the bottom of a 1.18 world.
 */
public final class CaveCarver {

    /** Blocks of the chunk being generated, chunk-local x/z and world y. */
    public interface Target {

        /** True for deep stone (not the floor, not already carved). */
        boolean carvable(int lx, int y, int lz);

        void set(int lx, int y, int lz, byte substance);
    }

    /** {@code CaveCarverConfiguration} with its vertical anchors resolved for a floor. */
    static final class Config {

        final float probability;
        final int minY;
        final int maxY;
        final float yScaleMin;
        final float yScaleMax;
        final int lavaLevel;
        final float hMulMin = 0.7F;
        final float hMulMax = 1.4F;
        final float vMulMin = 0.8F;
        final float vMulMax = 1.3F;
        final float floorMin = -1.0F;
        final float floorMax = -0.4F;

        Config(float probability, int minY, int maxY, int lavaLevel) {
            this.probability = probability;
            this.minY = minY;
            this.maxY = maxY;
            this.yScaleMin = 0.1F;
            this.yScaleMax = 0.9F;
            this.lavaLevel = lavaLevel;
        }
    }

    private static final int RANGE_CHUNKS = 8;
    /** {@code getRange() = 4}: tunnels are {@code (4 * 2 - 1) * 16} steps long. */
    private static final int TUNNEL_STEPS = (4 * 2 - 1) * 16;
    private static final int CAVE_BOUND = 15;
    /** Generous bound of the vertical reach of a tunnel ellipsoid (thickness <= 12, multiplier <= 1.3). */
    private static final int MAX_VERTICAL_RADIUS = 20;

    private final long seed;
    private final Config[] configs;
    private final int floor;
    private final int ceiling;
    private final int fade;

    private Target target;
    private Aquifer aquifer;
    private int chunkX;
    private int chunkZ;

    /**
     * @param ceiling highest Y that may be carved
     * @param fade    blocks below the ceiling over which the tunnels close
     */
    public CaveCarver(long seed, int minY, int ceiling, int fade, boolean extraUnderground) {
        this.seed = seed;
        this.floor = minY;
        this.ceiling = ceiling;
        this.fade = Math.max(fade, 1);
        int lava = minY + 8; // aboveBottom(8)
        Config cave = new Config(0.15F, minY + 8, 180, lava);
        Config extra = new Config(0.07F, minY + 8, 47, lava);
        // index = position in the overworld carver list: the large feature seed is seed + index
        this.configs = extraUnderground ? new Config[] { cave, extra } : new Config[] { cave, null };
    }

    public void carve(int chunkX, int chunkZ, Aquifer aquifer, Target target) {
        this.target = target;
        this.aquifer = aquifer;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Random random = new Random();
        try {
            for (int dx = -RANGE_CHUNKS; dx <= RANGE_CHUNKS; dx++) {
                for (int dz = -RANGE_CHUNKS; dz <= RANGE_CHUNKS; dz++) {
                    int sx = chunkX + dx;
                    int sz = chunkZ + dz;
                    for (int k = 0; k < this.configs.length; k++) {
                        Config config = this.configs[k];
                        if (config == null) continue;
                        setLargeFeatureSeed(random, this.seed + k, sx, sz);
                        if (random.nextFloat() <= config.probability) this.carveFrom(config, random, sx, sz);
                    }
                }
            }
        } finally {
            this.target = null;
            this.aquifer = null;
        }
    }

    static void setLargeFeatureSeed(Random random, long seed, int x, int z) {
        random.setSeed(seed);
        long a = random.nextLong();
        long b = random.nextLong();
        random.setSeed(x * a ^ z * b ^ seed);
    }

    private static float uniform(Random r, float min, float max) {
        return r.nextFloat() * (max - min) + min;
    }

    private void carveFrom(Config config, Random random, int startX, int startZ) {
        int count = random.nextInt(random.nextInt(random.nextInt(CAVE_BOUND) + 1) + 1);
        for (int i = 0; i < count; i++) {
            double x = (startX << 4) + random.nextInt(16);
            double y = random.nextInt(config.maxY - config.minY + 1) + config.minY;
            double z = (startZ << 4) + random.nextInt(16);
            double hMul = uniform(random, config.hMulMin, config.hMulMax);
            double vMul = uniform(random, config.vMulMin, config.vMulMax);
            double floorLevel = uniform(random, config.floorMin, config.floorMax);
            // a tunnel moves one block per step at most: one that cannot come down to the layer is skipped, after
            // drawing exactly what 1.18 draws (its own random is seeded from this one)
            boolean reachable = y - TUNNEL_STEPS - MAX_VERTICAL_RADIUS <= this.ceiling;
            int tunnels = 1;
            if (random.nextInt(4) == 0) {
                double yScale = uniform(random, config.yScaleMin, config.yScaleMax);
                float radius = 1.0F + random.nextFloat() * 6.0F;
                double h = 1.5D + Mth.sin((float) Math.PI / 2.0F) * radius;
                if (reachable) this.carveEllipsoid(config, x + 1.0D, y, z, h, h * yScale, floorLevel);
                tunnels += random.nextInt(4);
            }
            for (int t = 0; t < tunnels; t++) {
                float yaw = random.nextFloat() * (float) Math.PI * 2.0F;
                float pitch = (random.nextFloat() - 0.5F) / 4.0F;
                float thickness = thickness(random);
                int steps = TUNNEL_STEPS - random.nextInt(TUNNEL_STEPS / 4);
                long tunnelSeed = random.nextLong();
                if (reachable) {
                    this.tunnel(
                        config,
                        tunnelSeed,
                        x,
                        y,
                        z,
                        hMul,
                        vMul,
                        thickness,
                        yaw,
                        pitch,
                        0,
                        steps,
                        1.0D,
                        floorLevel);
                }
            }
        }
    }

    private static float thickness(Random random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        return f;
    }

    private void tunnel(Config config, long seed, double x, double y, double z, double hMul, double vMul,
        float thickness, float yaw, float pitch, int step, int steps, double yScale, double floorLevel) {
        Random random = new Random(seed);
        int split = random.nextInt(steps / 2) + steps / 4;
        boolean steep = random.nextInt(6) == 0;
        float yawDelta = 0.0F;
        float pitchDelta = 0.0F;
        for (int i = step; i < steps; i++) {
            double h = 1.5D + Mth.sin((float) Math.PI * i / steps) * thickness;
            double v = h * yScale;
            float cos = Mth.cos(pitch);
            x += Mth.cos(yaw) * cos;
            y += Mth.sin(pitch);
            z += Mth.sin(yaw) * cos;
            pitch *= steep ? 0.92F : 0.7F;
            pitch += pitchDelta * 0.1F;
            yaw += yawDelta * 0.1F;
            pitchDelta *= 0.9F;
            yawDelta *= 0.75F;
            pitchDelta += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawDelta += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (i == split && thickness > 1.0F) {
                this.tunnel(
                    config,
                    random.nextLong(),
                    x,
                    y,
                    z,
                    hMul,
                    vMul,
                    random.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) Math.PI / 2.0F,
                    pitch / 3.0F,
                    i,
                    steps,
                    1.0D,
                    floorLevel);
                this.tunnel(
                    config,
                    random.nextLong(),
                    x,
                    y,
                    z,
                    hMul,
                    vMul,
                    random.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) Math.PI / 2.0F,
                    pitch / 3.0F,
                    i,
                    steps,
                    1.0D,
                    floorLevel);
                return;
            }
            if (random.nextInt(4) != 0) {
                if (!this.canReach(x, z, i, steps, thickness)) return;
                this.carveEllipsoid(config, x, y, z, h * hMul, v * vMul, floorLevel);
            }
        }
    }

    private boolean canReach(double x, double z, int step, int steps, float thickness) {
        double dx = x - ((this.chunkX << 4) + 8);
        double dz = z - ((this.chunkZ << 4) + 8);
        double remaining = steps - step;
        double r = thickness + 2.0F + 16.0F;
        return dx * dx + dz * dz - remaining * remaining <= r * r;
    }

    /** Extra radius taken away near the ceiling: 0 below the fade band, 1 (nothing carved) at the ceiling. */
    double ceilingFade(int y) {
        if (y > this.ceiling) return 1.0D;
        double t = (y - (this.ceiling - this.fade)) / (double) this.fade;
        if (t <= 0.0D) return 0.0D;
        return t * t;
    }

    private void carveEllipsoid(Config config, double x, double y, double z, double hr, double vr, double floorLevel) {
        double cx = (this.chunkX << 4) + 8;
        double cz = (this.chunkZ << 4) + 8;
        double maxDist = 16.0D + hr * 2.0D;
        if (Math.abs(x - cx) > maxDist || Math.abs(z - cz) > maxDist) return;
        if (y - vr - 1 > this.ceiling || y + vr + 1 < this.floor) return;
        int minX = this.chunkX << 4;
        int minZ = this.chunkZ << 4;
        int x0 = Math.max(Mth.floor(x - hr) - minX - 1, 0);
        int x1 = Math.min(Mth.floor(x + hr) - minX, 15);
        int y0 = Math.max(Mth.floor(y - vr) - 1, this.floor + 1);
        int y1 = Math.min(Mth.floor(y + vr) + 1, this.ceiling);
        int z0 = Math.max(Mth.floor(z - hr) - minZ - 1, 0);
        int z1 = Math.min(Mth.floor(z + hr) - minZ, 15);
        for (int lx = x0; lx <= x1; lx++) {
            double dx = (minX + lx + 0.5D - x) / hr;
            for (int lz = z0; lz <= z1; lz++) {
                double dz = (minZ + lz + 0.5D - z) / hr;
                if (dx * dx + dz * dz >= 1.0D) continue;
                for (int yy = y1; yy > y0; yy--) {
                    double dy = (yy - 0.5D - y) / vr;
                    if (dy <= floorLevel || dx * dx + dy * dy + dz * dz >= 1.0D - this.ceilingFade(yy)) continue;
                    if (!this.target.carvable(lx, yy, lz)) continue;
                    byte substance = yy <= config.lavaLevel ? Aquifer.LAVA
                        : this.aquifer.compute(minX + lx, yy, minZ + lz, 0.0D);
                    if (substance != Aquifer.SOLID) this.target.set(lx, yy, lz, substance);
                }
            }
        }
    }
}
