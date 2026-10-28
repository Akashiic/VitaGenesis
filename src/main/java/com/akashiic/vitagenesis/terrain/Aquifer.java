package com.akashiic.vitagenesis.terrain;

import java.util.Arrays;

import com.akashiic.vitagenesis.noise.Mth;
import com.akashiic.vitagenesis.noise.XoroshiroRandom;

/**
 * 1.18 {@code NoiseBasedAquifer} for one chunk: what fills a cell whose density is not solid. Aquifer centres sit on a
 * 16 x 12 x 16 grid, each with its own fluid level and type; between two centres with different levels a barrier
 * (controlled by the barrier noise and a pressure term) stays solid so water stands at several levels.
 * <p>
 * Below Y 0 the surface is always far above (the world above is the 1.7.10 one and is not read), so the preliminary
 * surface level is the constant {@link #SURFACE}: no cell is near the surface and no aquifer is forced open by an ocean
 * above. Everything else, including the global lava below {@code floor + 10} and lava aquifers below Y -10, is 1.18.
 * Not thread-safe: one instance per chunk being generated.
 */
public final class Aquifer {

    public static final byte SOLID = 0;
    public static final byte AIR = 1;
    public static final byte WATER = 2;
    public static final byte LAVA = 3;

    /** {@code DimensionType.WAY_BELOW_MIN_Y}. */
    static final int WAY_BELOW_MIN_Y = Integer.MIN_VALUE + 1;
    static final int SEA_LEVEL = 63;
    /** Preliminary surface level assumed above every deep column. */
    static final int SURFACE = 64;

    private static final int X_SPACING = 16;
    private static final int Y_SPACING = 12;
    private static final int Z_SPACING = 16;
    private static final int X_RANGE = 10;
    private static final int Y_RANGE = 9;
    private static final int Z_RANGE = 10;

    private final CaveNoise noise;
    private final boolean enabled;
    /** Global lava below this Y (1.18: -54, ten above the floor). */
    private final int lavaLevel;

    private final int minGridX;
    private final int minGridY;
    private final int minGridZ;
    private final int gridSizeX;
    private final int gridSizeZ;
    private final long[] locationCache;
    private final int[] statusLevel;
    private final byte[] statusType;
    private final boolean[] statusKnown;

    private double barrier;
    private boolean barrierKnown;

    /**
     * @param enabled false: every non-solid cell is air above the lava level (1.18 with aquifers off)
     */
    public Aquifer(CaveNoise noise, int chunkX, int chunkZ, int minY, int maxY, boolean enabled) {
        this.noise = noise;
        this.enabled = enabled;
        this.lavaLevel = minY + 10;
        this.minGridX = gridX(chunkX << 4) - 1;
        int maxGridX = gridX((chunkX << 4) + 15) + 1;
        this.gridSizeX = maxGridX - this.minGridX + 1;
        this.minGridY = gridY(minY) - 1;
        // the lookup reads floorDiv(y + 1, 12) + 1: size by the exclusive top, as 1.18 does
        int maxGridY = gridY(maxY + 1) + 1;
        int gridSizeY = maxGridY - this.minGridY + 1;
        this.minGridZ = gridZ(chunkZ << 4) - 1;
        int maxGridZ = gridZ((chunkZ << 4) + 15) + 1;
        this.gridSizeZ = maxGridZ - this.minGridZ + 1;
        int size = this.gridSizeX * gridSizeY * this.gridSizeZ;
        this.locationCache = new long[size];
        Arrays.fill(this.locationCache, Long.MAX_VALUE);
        this.statusLevel = new int[size];
        this.statusType = new byte[size];
        this.statusKnown = new boolean[size];
    }

    private static int gridX(int x) {
        return Math.floorDiv(x, X_SPACING);
    }

    private static int gridY(int y) {
        return Math.floorDiv(y, Y_SPACING);
    }

    private static int gridZ(int z) {
        return Math.floorDiv(z, Z_SPACING);
    }

    private int index(int gx, int gy, int gz) {
        return ((gy - this.minGridY) * this.gridSizeZ + gz - this.minGridZ) * this.gridSizeX + gx - this.minGridX;
    }

    /** Global fluid: lava up to the lava level, water up to sea level. Returns the level; the type is lava below. */
    private boolean globalIsLava(int y) {
        return y < Math.min(this.lavaLevel, SEA_LEVEL);
    }

    /**
     * {@code computeSubstance}: {@link #SOLID} keeps the stone (a barrier, or positive density), otherwise air, water
     * or
     * lava.
     */
    public byte compute(int x, int y, int z, double density) {
        if (density > 0.0D) return SOLID;
        if (this.globalIsLava(y)) return LAVA;
        if (!this.enabled) return AIR;
        this.barrierKnown = false;

        int gx = Math.floorDiv(x - 5, X_SPACING);
        int gy = Math.floorDiv(y + 1, Y_SPACING);
        int gz = Math.floorDiv(z - 5, Z_SPACING);
        int d1 = Integer.MAX_VALUE;
        int d2 = Integer.MAX_VALUE;
        int d3 = Integer.MAX_VALUE;
        long p1 = 0L;
        long p2 = 0L;
        long p3 = 0L;
        for (int ox = 0; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = 0; oz <= 1; oz++) {
                    long pos = this.location(gx + ox, gy + oy, gz + oz);
                    int dx = unpackX(pos) - x;
                    int dy = unpackY(pos) - y;
                    int dz = unpackZ(pos) - z;
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d1 >= d) {
                        p3 = p2;
                        p2 = p1;
                        p1 = pos;
                        d3 = d2;
                        d2 = d1;
                        d1 = d;
                    } else if (d2 >= d) {
                        p3 = p2;
                        p2 = pos;
                        d3 = d2;
                        d2 = d;
                    } else if (d3 >= d) {
                        p3 = pos;
                        d3 = d;
                    }
                }
            }
        }

        int i1 = this.status(p1);
        byte at1 = this.at(i1, y);
        double sim12 = similarity(d1, d2);
        if (sim12 <= 0.0D) return at1;
        if (at1 == WATER && this.globalIsLava(y - 1)) return at1;

        int i2 = this.status(p2);
        double pressure12 = sim12 * this.pressure(x, y, z, i1, i2);
        if (density + pressure12 > 0.0D) return SOLID;

        int i3 = this.status(p3);
        double sim13 = similarity(d1, d3);
        if (sim13 > 0.0D && density + sim12 * sim13 * this.pressure(x, y, z, i1, i3) > 0.0D) return SOLID;
        double sim23 = similarity(d2, d3);
        if (sim23 > 0.0D && density + sim12 * sim23 * this.pressure(x, y, z, i2, i3) > 0.0D) return SOLID;
        return at1;
    }

    private static double similarity(int a, int b) {
        return 1.0D - Math.abs(b - a) / 25.0D;
    }

    private byte at(int statusIndex, int y) {
        return y < this.statusLevel[statusIndex] ? this.statusType[statusIndex] : AIR;
    }

    private double pressure(int x, int y, int z, int a, int b) {
        byte sa = this.at(a, y);
        byte sb = this.at(b, y);
        if (sa == LAVA && sb == WATER || sa == WATER && sb == LAVA) return 2.0D;
        int levelA = this.statusLevel[a];
        int levelB = this.statusLevel[b];
        int diff = Math.abs(levelA - levelB);
        if (diff == 0) return 0.0D;
        double mid = 0.5D * (levelA + levelB);
        double offset = y + 0.5D - mid;
        double half = diff / 2.0D;
        double edge = half - Math.abs(offset);
        double gradient;
        if (offset > 0.0D) {
            gradient = edge > 0.0D ? edge / 1.5D : edge / 2.5D;
        } else {
            double e = 3.0D + edge;
            gradient = e > 0.0D ? e / 3.0D : e / 10.0D;
        }
        double barrierValue = 0.0D;
        if (gradient >= -2.0D && gradient <= 2.0D) {
            if (!this.barrierKnown) {
                this.barrier = this.noise.aquiferBarrier.getValue(x, y * 0.5D, z);
                this.barrierKnown = true;
            }
            barrierValue = this.barrier;
        }
        return 2.0D * (barrierValue + gradient);
    }

    private long location(int gx, int gy, int gz) {
        int i = this.index(gx, gy, gz);
        long cached = this.locationCache[i];
        if (cached != Long.MAX_VALUE) return cached;
        XoroshiroRandom r = this.noise.aquiferRandom.at(gx, gy, gz);
        long pos = pack(
            gx * X_SPACING + r.nextInt(X_RANGE),
            gy * Y_SPACING + r.nextInt(Y_RANGE),
            gz * Z_SPACING + r.nextInt(Z_RANGE));
        this.locationCache[i] = pos;
        return pos;
    }

    /** Index of the cached fluid status of the aquifer centred at {@code pos}. */
    private int status(long pos) {
        int x = unpackX(pos);
        int y = unpackY(pos);
        int z = unpackZ(pos);
        int i = this.index(gridX(x), gridY(y), gridZ(z));
        if (!this.statusKnown[i]) {
            this.computeFluid(i, x, y, z);
            this.statusKnown[i] = true;
        }
        return i;
    }

    /** {@code computeFluid} with every surface sample at {@link #SURFACE}. */
    private void computeFluid(int i, int x, int y, int z) {
        boolean globalLava = this.globalIsLava(y);
        // computeSurfaceLevel with surfaceBelow false: f = 0, so the thresholds are 0.8 (global) and 0.4 (random level)
        double floodedness = Mth.clamp(this.noise.aquiferFloodedness.getValue(x, y * 0.67D, z), -1.0D, 1.0D);
        int level;
        if (floodedness - 0.8D > 0.0D) {
            level = globalLava ? this.lavaLevel : SEA_LEVEL;
        } else if (floodedness - 0.4D > 0.0D) {
            level = this.randomizedLevel(x, y, z);
        } else {
            level = WAY_BELOW_MIN_Y;
        }
        byte type = globalLava ? LAVA : WATER;
        if (!globalLava && level <= -10 && level != WAY_BELOW_MIN_Y) {
            double lava = this.noise.aquiferLava
                .getValue(Math.floorDiv(x, 64), Math.floorDiv(y, 40), Math.floorDiv(z, 64));
            if (Math.abs(lava) > 0.3D) type = LAVA;
        }
        this.statusLevel[i] = level;
        this.statusType[i] = type;
    }

    private int randomizedLevel(int x, int y, int z) {
        int cx = Math.floorDiv(x, 16);
        int cy = Math.floorDiv(y, 40);
        int cz = Math.floorDiv(z, 16);
        int mid = cy * 40 + 20;
        double spread = this.noise.aquiferSpread.getValue(cx, cy * 0.7142857142857143D, cz) * 10.0D;
        return Math.min(SURFACE, mid + Mth.quantize(spread, 3));
    }

    // BlockPos.asLong layout: 26 bits x, 12 bits y, 26 bits z
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    static int unpackX(long p) {
        return (int) (p << 0 >> 38);
    }

    static int unpackY(long p) {
        return (int) (p << 52 >> 52);
    }

    static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }
}
