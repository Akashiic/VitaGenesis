package com.akashiic.vitagenesis.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseTest {

    @Test
    void xoroshiroIsDeterministicAndBounded() {
        XoroshiroRandom a = new XoroshiroRandom(12345L);
        XoroshiroRandom b = new XoroshiroRandom(12345L);
        int[] buckets = new int[10];
        for (int i = 0; i < 100000; i++) {
            long la = a.nextLong();
            assertEquals(la, b.nextLong());
            int n = a.nextInt(10);
            b.nextInt(10);
            assertTrue(n >= 0 && n < 10);
            buckets[n]++;
            double d = a.nextDouble();
            b.nextDouble();
            assertTrue(d >= 0.0D && d < 1.0D);
        }
        for (int count : buckets) assertEquals(10000, count, 500, "nextInt(10) is not uniform");
        assertNotEquals(new XoroshiroRandom(1L).nextLong(), new XoroshiroRandom(2L).nextLong());
    }

    @Test
    void positionalFactoryDependsOnNameAndPosition() {
        XoroshiroRandom.Positional p = new XoroshiroRandom(42L).forkPositional();
        assertEquals(
            p.fromHashOf("minecraft:cave_cheese")
                .nextLong(),
            p.fromHashOf("minecraft:cave_cheese")
                .nextLong());
        assertNotEquals(
            p.fromHashOf("minecraft:cave_cheese")
                .nextLong(),
            p.fromHashOf("minecraft:cave_layer")
                .nextLong());
        assertEquals(
            p.at(1, 2, 3)
                .nextLong(),
            p.at(1, 2, 3)
                .nextLong());
        assertNotEquals(
            p.at(1, 2, 3)
                .nextLong(),
            p.at(1, 3, 2)
                .nextLong());
    }

    @Test
    void normalNoiseHasTheTargetDeviationAndStaysBelowItsMaximum() {
        Noises noises = new Noises(7L);
        for (Noises.Params params : new Noises.Params[] { Noises.CAVE_CHEESE, Noises.CAVE_LAYER, Noises.PILLAR,
            Noises.AQUIFER_BARRIER }) {
            NormalNoise noise = noises.create(params);
            double sum = 0.0D;
            double sq = 0.0D;
            int n = 0;
            for (int x = 0; x < 2000; x += 7) {
                for (int z = 0; z < 2000; z += 7) {
                    double v = noise.getValue(x, (x ^ z) % 97, z);
                    assertTrue(Math.abs(v) <= noise.maxValue(), params.key + " exceeds its maximum");
                    sum += v;
                    sq += v * v;
                    n++;
                }
            }
            double mean = sum / n;
            double sd = Math.sqrt(sq / n - mean * mean);
            // NormalNoise is scaled to a deviation of 1/3 (low-octave noises over a finite area deviate more)
            assertEquals(1.0D / 3.0D, sd, 0.12D, params.key + " deviation");
        }
    }

    @Test
    void tableSineMatchesMath() {
        for (float v = -10.0F; v < 10.0F; v += 0.01F) {
            assertEquals(Math.sin(v), Mth.sin(v), 1.0e-3);
            assertEquals(Math.cos(v), Mth.cos(v), 1.0e-3);
        }
    }
}
