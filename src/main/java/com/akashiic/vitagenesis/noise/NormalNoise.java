package com.akashiic.vitagenesis.noise;

/**
 * 1.18 {@code NormalNoise}: two Perlin noises at slightly different frequencies, scaled so that the result has a
 * standard deviation of about 1/3 whatever the number of octaves.
 */
public final class NormalNoise {

    private static final double INPUT_FACTOR = 1.0181268882175227D;
    private static final double TARGET_DEVIATION = 0.3333333333333333D;

    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double valueFactor;
    private final double maxValue;

    public NormalNoise(XoroshiroRandom random, Noises.Params params) {
        this.first = new PerlinNoise(random, params.firstOctave, params.amplitudes);
        this.second = new PerlinNoise(random, params.firstOctave, params.amplitudes);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < params.amplitudes.length; i++) {
            if (params.amplitudes[i] == 0.0D) continue;
            min = Math.min(min, i);
            max = Math.max(max, i);
        }
        this.valueFactor = TARGET_DEVIATION * 0.5D / expectedDeviation(max - min);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    private static double expectedDeviation(int octaves) {
        return 0.1D * (1.0D + 1.0D / (octaves + 1));
    }

    public double getValue(double x, double y, double z) {
        double x2 = x * INPUT_FACTOR;
        double y2 = y * INPUT_FACTOR;
        double z2 = z * INPUT_FACTOR;
        return (this.first.getValue(x, y, z) + this.second.getValue(x2, y2, z2)) * this.valueFactor;
    }

    public double maxValue() {
        return this.maxValue;
    }
}
