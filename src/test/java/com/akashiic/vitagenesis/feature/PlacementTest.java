package com.akashiic.vitagenesis.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class PlacementTest {

    @Test
    void scaledCountKeepsTheDensityPerBlock() {
        Random r = new Random(1L);
        for (int minY : new int[] { -64, -128, -512 }) {
            double sum = 0.0D;
            int n = 200000;
            for (int i = 0; i < n; i++) sum += Placement.scaledCount(r, 125, minY, 256);
            double expected = 125.0D * (256 - minY + 1) / (256 + 64 + 1);
            assertEquals(expected, sum / n, expected * 0.01D, "floor " + minY);
        }
        assertEquals(125, Placement.scaledCount(new Random(2L), 125, -64, 256), "exact at -64");
    }

    @Test
    void veryBiasedToBottomStaysInRangeAndFavoursTheBottom() {
        Random r = new Random(3L);
        int below = 0;
        int n = 100000;
        for (int i = 0; i < n; i++) {
            int y = Placement.veryBiasedToBottom(r, -64, 311, 8);
            assertTrue(y >= -64 && y <= 311, "out of range: " + y);
            if (y < 0) below++;
        }
        // 1.18 lava springs: most below Y 0
        assertTrue(below > n / 2, "only " + below + " of " + n + " below 0");
    }

    @Test
    void fastInvSqrtIsTheOneNewtonStepApproximation() {
        for (double x = 0.5D; x < 2000.0D; x *= 1.37D) {
            double exact = 1.0D / Math.sqrt(x);
            double fast = Geode.fastInvSqrt(x);
            assertEquals(exact, fast, exact * 0.002D, "x = " + x);
        }
    }

    @Test
    void dripstoneHeightIsHighestAtTheCentreAndZeroAtTheRim() {
        double centre = Dripstone.dripstoneHeight(0.0D, 10.0D, 1.0D, 0.5D);
        double middle = Dripstone.dripstoneHeight(5.0D, 10.0D, 1.0D, 0.5D);
        double rim = Dripstone.dripstoneHeight(10.0D, 10.0D, 1.0D, 0.5D);
        assertTrue(centre > middle && middle > rim, centre + " " + middle + " " + rim);
        assertEquals(0.0D, rim, 0.05D);
    }

    @Test
    void caveVineLengthsFollowTheWeightedList() {
        Random r = new Random(4L);
        double sum = 0.0D;
        int n = 300000;
        for (int i = 0; i < n; i++) {
            int v = Placement.weightedUniform(r, LushCaves.VINES);
            assertTrue(v >= 0 && v <= 19);
            sum += v;
        }
        // (2 * 9.5 + 3 * 1 + 10 * 3) / 15
        assertEquals(52.0D / 15.0D, sum / n, 0.03D);
    }
}
