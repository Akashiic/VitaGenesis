package com.akashiic.vitagenesis.feature;

import java.util.Random;

import com.akashiic.vitagenesis.CaveBlocks;

/**
 * 1.18 {@code UnderwaterMagmaFeature} ({@code underwater_magma}: floor search 5, radius 1, probability 0.5): magma
 * blocks in the floor under the water of aquifers, where they are enclosed on every side.
 */
final class UnderwaterMagma {

    private UnderwaterMagma() {}

    static void place(Level lv, Random r, int x, int y, int z) {
        Placement.Column col = Placement.Column
            .scan(x, y, z, 5, (a, b, c) -> lv.water(a, b, c), (a, b, c) -> !lv.water(a, b, c));
        if (col == null || !col.hasFloor()) return;
        int floor = col.floor;
        for (int px = x - 1; px <= x + 1; px++) {
            for (int py = floor - 1; py <= floor + 1; py++) {
                for (int pz = z - 1; pz <= z + 1; pz++) {
                    if (r.nextFloat() < 0.5F && valid(lv, px, py, pz)) lv.set(px, py, pz, CaveBlocks.magma, 0);
                }
            }
        }
    }

    private static boolean valid(Level lv, int x, int y, int z) {
        if (lv.airOrWater(x, y, z) || lv.airOrWater(x, y - 1, z)) return false;
        return !lv.airOrWater(x, y, z - 1) && !lv.airOrWater(x + 1, y, z)
            && !lv.airOrWater(x, y, z + 1)
            && !lv.airOrWater(x - 1, y, z);
    }
}
