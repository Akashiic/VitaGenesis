package com.akashiic.vitagenesis.feature;

import com.akashiic.vitagenesis.noise.Noises;
import com.akashiic.vitagenesis.noise.NormalNoise;

/**
 * Cave biomes of 1.18 below Y 0, chosen from the 1.18 climate noises: lush caves where the humidity
 * ({@code vegetation})
 * is at least 0.7, dripstone caves where the continentalness is at least 0.8 (far inland). In 1.18 these climate values
 * are also those of the surface biome above, so lush caves lie under humid land; the world above is a 1.7.10 one here,
 * so the rainfall of its biome can be blended in ({@code bias}) to keep that relation. Below 0 the depth parameter of
 * 1.18 is inside the range of both cave biomes, so the choice depends on x and z only.
 */
public final class CaveBiomes {

    public static final int NORMAL = 0;
    public static final int LUSH = 1;
    public static final int DRIPSTONE = 2;

    private final NormalNoise shift;
    private final NormalNoise vegetation;
    private final NormalNoise continentalness;

    CaveBiomes(Noises noises) {
        this.shift = noises.create(Noises.SHIFT);
        this.vegetation = noises.create(Noises.VEGETATION);
        this.continentalness = noises.create(Noises.CONTINENTALNESS);
    }

    /**
     * @param rainfall     rainfall of the 1.7.10 biome of the column (0 desert .. 0.9 jungle)
     * @param lushBias     weight of the rainfall in the humidity
     * @param lushMin      humidity threshold of lush caves (1.18: 0.7)
     * @param dripstoneMin continentalness threshold of dripstone caves (1.18: 0.8)
     */
    int at(int x, int z, float rainfall, double lushBias, double lushMin, double dripstoneMin) {
        // 1.18 shifted_noise: shift_x/shift_z from the offset noise, xz scale 0.25, y ignored
        double sx = this.shift.getValue(x * 0.25D, 0.0D, z * 0.25D) * 4.0D;
        double sz = this.shift.getValue(z * 0.25D, x * 0.25D, 0.0D) * 4.0D;
        double qx = x * 0.25D + sx;
        double qz = z * 0.25D + sz;
        if (this.continentalness.getValue(qx, 0.0D, qz) >= dripstoneMin) return DRIPSTONE;
        double humidity = this.vegetation.getValue(qx, 0.0D, qz) + lushBias * (Math.min(rainfall, 1.0F) - 0.5F);
        return humidity >= lushMin ? LUSH : NORMAL;
    }
}
