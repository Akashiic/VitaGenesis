package com.akashiic.vitagenesis.terrain;

import com.akashiic.vitagenesis.noise.Mth;
import com.akashiic.vitagenesis.noise.XoroshiroRandom;

/**
 * 1.18 {@code OreVeinifier} for solid cells. The copper vein lives at Y 0..50, so below 0 only the iron vein exists:
 * deepslate iron ore, raw iron blocks and tuff along ridged noise. Its band is {@code minY + 4 .. -8} (1.18: -60..-8),
 * so a deeper floor stretches it down.
 */
public final class OreVeins {

    public static final byte NONE = 0;
    public static final byte ORE = 1;
    public static final byte RAW_BLOCK = 2;
    public static final byte FILLER = 3;

    static final int IRON_MAX_Y = -8;
    /** Top of the interpolated vein noises (1.18: {@code yLimitedInterpolatable(.., -60, 51, 0)}). */
    static final int NOISE_MAX_Y = 51;

    private final CaveNoise noise;
    private final int minY;

    public OreVeins(CaveNoise noise, int minY) {
        this.noise = noise;
        this.minY = minY;
    }

    int noiseMinY() {
        return this.minY + 4;
    }

    boolean inNoiseBand(int y) {
        return y >= this.noiseMinY() && y <= NOISE_MAX_Y;
    }

    /** {@code ore_veininess} input, sampled at the interpolation corners. */
    double toggle(int x, int y, int z) {
        return this.inNoiseBand(y) ? this.noise.oreVeininess.getValue(x * 1.5D, y * 1.5D, z * 1.5D) : 0.0D;
    }

    /** {@code -0.08 + max(|ore_vein_a|, |ore_vein_b|)}, sampled at the interpolation corners. */
    double ridged(int x, int y, int z) {
        if (!this.inNoiseBand(y)) return 0.0D;
        double a = Math.abs(this.noise.oreVeinA.getValue(x * 4.0D, y * 4.0D, z * 4.0D));
        double b = Math.abs(this.noise.oreVeinB.getValue(x * 4.0D, y * 4.0D, z * 4.0D));
        return -0.08D + Math.max(a, b);
    }

    /** What a solid cell becomes, from its interpolated toggle and ridged values. */
    byte compute(int x, int y, int z, double toggle, double ridged) {
        if (toggle > 0.0D) return NONE; // copper side: Y 0..50 only
        double strength = Math.abs(toggle);
        int top = IRON_MAX_Y - y;
        int bottom = y - (this.minY + 4);
        if (bottom < 0 || top < 0) return NONE;
        double edge = Mth.clampedMap(Math.min(top, bottom), 0.0D, 20.0D, -0.2D, 0.0D);
        if (strength + edge < 0.4F) return NONE;
        XoroshiroRandom r = this.noise.oreRandom.at(x, y, z);
        if (r.nextFloat() > 0.7F) return NONE;
        if (ridged >= 0.0D) return NONE;
        double richness = Mth.clampedMap(strength, 0.4F, 0.6F, 0.1F, 0.3F);
        if (r.nextFloat() < richness && this.noise.oreGap.getValue(x, y, z) > -0.3F) {
            return r.nextFloat() < 0.02F ? RAW_BLOCK : ORE;
        }
        return FILLER;
    }
}
