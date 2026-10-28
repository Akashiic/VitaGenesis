package com.akashiic.vitagenesis.feature;

import java.util.Random;

import net.minecraft.block.Block;

import com.akashiic.vitagenesis.CaveBlocks;

/**
 * 1.18 {@code MultifaceGrowthFeature} with the {@code glow_lichen} configuration: on ceilings and walls (not floors) of
 * stone, deepslate, tuff, calcite and dripstone blocks. From an origin with no face to grow on, the search walks up to
 * 20
 * blocks through air in each direction, as {@code searchRange} intends (1.18.2 re-tests the direct neighbour 20 times;
 * later versions walk). Et Futurum keeps the faces in the tile entity ({@code State}: bit {@code 1 << ForgeDirection}
 * of each
 * supporting block); the 1.18 spread to more faces is reduced to a second face on the same block.
 */
final class GlowLichen {

    /** Ceiling and the four walls, as ForgeDirection indices of the supporting block. */
    private static final int[] DIRECTIONS = { 1, 2, 3, 4, 5 };

    private static final int SEARCH_RANGE = 20;

    private GlowLichen() {}

    private static boolean canBePlacedOn(Block b) {
        return CaveBlocks.isBaseStone(b) || b != null && (b == CaveBlocks.calcite || b == CaveBlocks.dripstoneBlock);
    }

    static void place(Level lv, Random r, int x, int y, int z) {
        if (!lv.air(x, y, z)) return;
        int[] dirs = shuffled(r, -1);
        if (grow(lv, r, x, y, z, dirs)) return;
        for (int d : dirs) {
            int[] others = shuffled(r, d ^ 1);
            int px = x;
            int py = y;
            int pz = z;
            for (int i = 0; i < SEARCH_RANGE; i++) {
                px += Geode.DX[d];
                py += Geode.DY[d];
                pz += Geode.DZ[d];
                if (!lv.air(px, py, pz)) break;
                if (grow(lv, r, px, py, pz, others)) return;
            }
        }
    }

    /** The valid directions in random order, without {@code except} (the direction back to the origin). */
    private static int[] shuffled(Random r, int except) {
        int[] out = new int[DIRECTIONS.length];
        int n = 0;
        for (int d : DIRECTIONS) {
            if (d != except) out[n++] = d;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    private static boolean grow(Level lv, Random r, int x, int y, int z, int[] dirs) {
        for (int d : dirs) {
            if (!canBePlacedOn(lv.block(x + Geode.DX[d], y + Geode.DY[d], z + Geode.DZ[d]))) continue;
            int faces = 1 << d;
            if (r.nextFloat() < 0.5F) {
                int other = dirs[r.nextInt(dirs.length)];
                if (canBePlacedOn(lv.block(x + Geode.DX[other], y + Geode.DY[other], z + Geode.DZ[other]))) {
                    faces |= 1 << other;
                }
            }
            if (lv.set(x, y, z, CaveBlocks.glowLichen, d)) Level.setTileInt(lv.tile(x, y, z), "State", faces);
            return true;
        }
        return false;
    }
}
