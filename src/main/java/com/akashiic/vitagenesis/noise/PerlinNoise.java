package com.akashiic.vitagenesis.noise;

/** 1.18 {@code PerlinNoise} with the modern (positional) octave initialization. */
public final class PerlinNoise {

    private final ImprovedNoise[] levels;
    private final double[] amplitudes;
    private final double lowestFreqInputFactor;
    private final double lowestFreqValueFactor;
    private final double maxValue;

    public PerlinNoise(XoroshiroRandom random, int firstOctave, double[] amplitudes) {
        this.amplitudes = amplitudes.clone();
        int n = amplitudes.length;
        this.levels = new ImprovedNoise[n];
        XoroshiroRandom.Positional factory = random.forkPositional();
        for (int k = 0; k < n; k++) {
            if (amplitudes[k] != 0.0D)
                this.levels[k] = new ImprovedNoise(factory.fromHashOf("octave_" + (firstOctave + k)));
        }
        this.lowestFreqInputFactor = Math.pow(2.0D, firstOctave);
        this.lowestFreqValueFactor = Math.pow(2.0D, n - 1) / (Math.pow(2.0D, n) - 1.0D);
        this.maxValue = this.edgeValue(2.0D);
    }

    public double getValue(double x, double y, double z) {
        double total = 0.0D;
        double input = this.lowestFreqInputFactor;
        double value = this.lowestFreqValueFactor;
        for (int i = 0; i < this.levels.length; i++) {
            ImprovedNoise level = this.levels[i];
            if (level != null) {
                total += this.amplitudes[i] * level.noise(wrap(x * input), wrap(y * input), wrap(z * input)) * value;
            }
            input *= 2.0D;
            value /= 2.0D;
        }
        return total;
    }

    public double maxValue() {
        return this.maxValue;
    }

    private double edgeValue(double v) {
        double total = 0.0D;
        double value = this.lowestFreqValueFactor;
        for (int i = 0; i < this.levels.length; i++) {
            if (this.levels[i] != null) total += this.amplitudes[i] * v * value;
            value /= 2.0D;
        }
        return total;
    }

    /** Keeps coordinates in the range where doubles still have sub-block precision. */
    public static double wrap(double v) {
        return v - Mth.lfloor(v / 3.3554432E7D + 0.5D) * 3.3554432E7D;
    }
}
