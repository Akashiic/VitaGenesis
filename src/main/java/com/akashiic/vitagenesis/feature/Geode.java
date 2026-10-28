package com.akashiic.vitagenesis.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.akashiic.vitagenesis.CaveBlocks;
import com.akashiic.vitagenesis.noise.NormalNoise;

/**
 * 1.18 {@code GeodeFeature} with the {@code amethyst_geode} configuration: smooth basalt, calcite and amethyst shells
 * around a hollow, budding amethyst with buds and clusters, and usually a crack. Placed with rarity 1/24 at
 * {@code uniform(aboveBottom(6), absolute(30))}; below Y 0 only geodes that fit under the caves' ceiling are kept, so
 * no geode is ever cut in half by Y 0.
 */
final class Geode {

    // GeodeLayerSettings
    private static final double FILLING = 1.7D;
    private static final double INNER = 2.2D;
    private static final double MIDDLE = 3.2D;
    private static final double OUTER = 4.2D;
    // GeodeCrackSettings
    private static final double CRACK_CHANCE = 0.95D;
    private static final double CRACK_SIZE = 2.0D;
    private static final int CRACK_OFFSET = 2;
    // GeodeConfiguration
    private static final double ALTERNATE_CHANCE = 0.083D;
    private static final double PLACEMENT_CHANCE = 0.35D;
    private static final int WALL_MIN = 4;
    private static final int WALL_MAX = 6;
    private static final int MIN_GEN = -16;
    private static final int MAX_GEN = 16;
    private static final double NOISE_MULTIPLIER = 0.05D;
    private static final int INVALID_THRESHOLD = 1;
    /**
     * Highest block a geode can write above its origin: the shell reaches about +14 with four distribution points, the
     * noise adds to it, and the placement loop stops at +16.
     */
    static final int TOP_REACH = MAX_GEN;

    private Geode() {}

    static void place(Level lv, Random r, int ox, int oy, int oz, NormalNoise noise) {
        int distCount = Placement.uniform(r, 3, 4);
        double d0 = distCount / (double) WALL_MAX;
        double fill = 1.0D / Math.sqrt(FILLING);
        double inner = 1.0D / Math.sqrt(INNER + d0);
        double middle = 1.0D / Math.sqrt(MIDDLE + d0);
        double outer = 1.0D / Math.sqrt(OUTER + d0);
        double crack = 1.0D / Math.sqrt(CRACK_SIZE + r.nextDouble() / 2.0D + (distCount > 3 ? d0 : 0.0D));
        boolean cracked = r.nextFloat() < CRACK_CHANCE;

        int[][] points = new int[distCount][];
        int invalid = 0;
        for (int i = 0; i < distCount; i++) {
            int px = ox + Placement.uniform(r, WALL_MIN, WALL_MAX);
            int py = oy + Placement.uniform(r, WALL_MIN, WALL_MAX);
            int pz = oz + Placement.uniform(r, WALL_MIN, WALL_MAX);
            if (lv.air(px, py, pz) || invalidBlock(lv.block(px, py, pz))) {
                if (++invalid > INVALID_THRESHOLD) return;
            }
            points[i] = new int[] { px, py, pz, Placement.uniform(r, 1, 2) };
        }

        List<int[]> cracks = new ArrayList<>();
        if (cracked) {
            int k = r.nextInt(4);
            int l = distCount * 2 + 1;
            int cx = k == 0 || k == 2 ? l : 0;
            int cz = k == 1 || k == 2 ? l : 0;
            for (int dy : new int[] { 7, 5, 1 }) cracks.add(new int[] { ox + cx, oy + dy, oz + cz });
        }

        List<int[]> potential = new ArrayList<>();
        for (int x = ox + MIN_GEN; x <= ox + MAX_GEN; x++) {
            for (int y = oy + MIN_GEN; y <= oy + MAX_GEN; y++) {
                for (int z = oz + MIN_GEN; z <= oz + MAX_GEN; z++) {
                    double n = noise.getValue(x, y, z) * NOISE_MULTIPLIER;
                    double s = 0.0D;
                    double c = 0.0D;
                    for (int[] p : points) s += fastInvSqrt(distSqr(x, y, z, p) + p[3]) + n;
                    for (int[] p : cracks) c += fastInvSqrt(distSqr(x, y, z, p) + CRACK_OFFSET) + n;
                    if (s < outer) continue;
                    if (cracked && c >= crack && s < fill) {
                        if (!lv.block(x, y, z)
                            .getMaterial()
                            .isLiquid()) setSafe(lv, x, y, z, Blocks.air);
                    } else if (s >= fill) {
                        setSafe(lv, x, y, z, Blocks.air);
                    } else if (s >= inner) {
                        boolean alternate = r.nextFloat() < ALTERNATE_CHANCE;
                        setSafe(lv, x, y, z, alternate ? CaveBlocks.buddingAmethyst : CaveBlocks.amethystBlock);
                        // placementsRequireLayer0Alternate: buds grow only on budding amethyst
                        if (alternate && r.nextFloat() < PLACEMENT_CHANCE) potential.add(new int[] { x, y, z });
                    } else if (s >= middle) {
                        setSafe(lv, x, y, z, CaveBlocks.calcite);
                    } else {
                        setSafe(lv, x, y, z, CaveBlocks.smoothBasalt);
                    }
                }
            }
        }

        if (CaveBlocks.amethystCluster1 == null || CaveBlocks.amethystCluster2 == null) return;
        for (int[] p : potential) {
            // small bud, medium bud, large bud, cluster
            int stage = r.nextInt(4);
            Block block = stage < 2 ? CaveBlocks.amethystCluster1 : CaveBlocks.amethystCluster2;
            for (int side = 0; side < 6; side++) {
                int qx = p[0] + DX[side];
                int qy = p[1] + DY[side];
                int qz = p[2] + DZ[side];
                if (lv.air(qx, qy, qz)) {
                    // meta % 6 is the side of the budding block the crystal grows from, +6 for the larger stage
                    lv.set(qx, qy, qz, block, side + (stage % 2) * 6);
                    break;
                }
            }
        }
    }

    /** {@code Direction.values()} order: down, up, north, south, west, east (the ForgeDirection order). */
    static final int[] DX = { 0, 0, 0, 0, -1, 1 };
    static final int[] DY = { -1, 1, 0, 0, 0, 0 };
    static final int[] DZ = { 0, 0, -1, 1, 0, 0 };

    /** {@code #geode_invalid_blocks}: bedrock, water, lava, ice, packed ice, blue ice. */
    private static boolean invalidBlock(Block b) {
        return b == Blocks.bedrock || b.getMaterial()
            .isLiquid() || b == Blocks.ice || b == Blocks.packed_ice;
    }

    /** {@code safeSetBlock} with {@code #features_cannot_replace}: never bedrock, spawners, chests (Level.set). */
    private static void setSafe(Level lv, int x, int y, int z, Block block) {
        lv.set(x, y, z, block, 0);
    }

    private static double distSqr(int x, int y, int z, int[] p) {
        double dx = x - p[0];
        double dy = y - p[1];
        double dz = z - p[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** {@code Mth.fastInvSqrt}: the shells of 1.18 geodes are shaped by this approximation. */
    static double fastInvSqrt(double x) {
        double half = 0.5D * x;
        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);
        return x * (1.5D - half * x * x);
    }
}
