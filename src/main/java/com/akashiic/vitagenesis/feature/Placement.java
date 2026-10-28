package com.akashiic.vitagenesis.feature;

import java.util.Random;

/** 1.18 placement modifiers used by the cave features, for one feature origin at a time. */
final class Placement {

    /** Top of {@code RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT}: {@code uniform(bottom, absolute(256))}. */
    static final int MAX_TERRAIN_HEIGHT = 256;
    /** {@code belowTop(8)} of the 1.18 overworld (top 319). */
    static final int BELOW_TOP_8 = 311;

    private Placement() {}

    /** {@code UniformInt.of(min, max)} / {@code Mth.randomBetweenInclusive}. */
    static int uniform(Random r, int min, int max) {
        return r.nextInt(max - min + 1) + min;
    }

    static float uniform(Random r, float min, float max) {
        return r.nextFloat() * (max - min) + min;
    }

    /** {@code Mth.nextInt}: inclusive, and {@code min} when the range is empty. */
    static int nextInt(Random r, int min, int max) {
        return min >= max ? min : r.nextInt(max - min + 1) + min;
    }

    /** {@code ClampedNormalFloat.sample}. */
    static float clampedNormal(Random r, float mean, float deviation, float min, float max) {
        float v = mean + (float) r.nextGaussian() * deviation;
        return v < min ? min : Math.min(v, max);
    }

    /** {@code ClampedNormalInt.sample}. */
    static int clampedNormalInt(Random r, float mean, float deviation, int min, int max) {
        return (int) clampedNormal(r, mean, deviation, min, max);
    }

    /** {@code VeryBiasedToBottomHeight.sample}. */
    static int veryBiasedToBottom(Random r, int min, int max, int inner) {
        if (max - min - inner + 1 <= 0) return min;
        int k = nextInt(r, min + inner, max);
        int l = nextInt(r, min, k - 1);
        return nextInt(r, min, l - 1 + inner);
    }

    /**
     * Count of a feature placed {@code uniform(bottom, top)}, scaled for a floor other than -64 so its density per
     * block stays the 1.18 one (the same rule Below Core uses for its ores). Fractions are rounded stochastically.
     */
    static int scaledCount(Random r, int count, int minY, int top) {
        double scaled = count * (double) (top - minY + 1) / (top + 64 + 1);
        int whole = (int) scaled;
        return whole + (r.nextDouble() < scaled - whole ? 1 : 0);
    }

    interface Test {

        boolean test(int x, int y, int z);
    }

    /**
     * {@code EnvironmentScanPlacement}: from the origin, step towards {@code dy} while {@code allowed} holds until
     * {@code target} holds, at most {@code maxSteps} times. Returns the target Y, or {@link Integer#MIN_VALUE}.
     */
    static int scan(Level lv, int x, int y, int z, int dy, Test target, Test allowed, int maxSteps) {
        if (!allowed.test(x, y, z)) return Integer.MIN_VALUE;
        for (int i = 0; i < maxSteps; i++) {
            if (target.test(x, y, z)) return y;
            y += dy;
            if (y < lv.minY || y > 255) return Integer.MIN_VALUE;
            if (!allowed.test(x, y, z)) break;
        }
        return target.test(x, y, z) ? y : Integer.MIN_VALUE;
    }

    /** {@code Column.scan}: the floor and ceiling (the first non-inside blocks) around an inside origin. */
    static final class Column {

        static final int NONE = Integer.MIN_VALUE;

        int floor = NONE;
        int ceiling = NONE;

        boolean hasFloor() {
            return this.floor != NONE;
        }

        boolean hasCeiling() {
            return this.ceiling != NONE;
        }

        /** Height of a range (both edges present): blocks strictly between floor and ceiling. */
        int height() {
            return this.ceiling - this.floor - 1;
        }

        /** Null when the origin is not inside the column. */
        static Column scan(int x, int y, int z, int maxDistance, Test inside, Test edge) {
            if (!inside.test(x, y, z)) return null;
            Column c = new Column();
            c.ceiling = scanDirection(x, y, z, maxDistance, inside, edge, 1);
            c.floor = scanDirection(x, y, z, maxDistance, inside, edge, -1);
            return c;
        }

        private static int scanDirection(int x, int y, int z, int maxDistance, Test inside, Test edge, int dy) {
            int yy = y;
            for (int i = 1; i < maxDistance && inside.test(x, yy, z); i++) yy += dy;
            return edge.test(x, yy, z) ? yy : NONE;
        }
    }
}
