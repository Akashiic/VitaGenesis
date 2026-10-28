package com.akashiic.vitagenesis.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.akashiic.vitagenesis.CaveBlocks;

/**
 * 1.18 {@code SpringFeature} for water and lava: a source in a wall with rock above, below and on three sides and one
 * open side. The source is placed still; the light stage sets it flowing once the chunks around it are loaded (1.18
 * schedules its fluid tick at once).
 */
final class Springs {

    private Springs() {}

    /** {@code validBlocks}: stone, granite, diorite, andesite, deepslate, tuff, calcite, dirt. */
    private static boolean valid(Level lv, int x, int y, int z) {
        Block b = lv.block(x, y, z);
        return CaveBlocks.isBaseStone(b) || b == Blocks.dirt || b != null && b == CaveBlocks.calcite;
    }

    static void place(Level lv, int x, int y, int z, Block fluid) {
        if (!valid(lv, x, y + 1, z) || !valid(lv, x, y - 1, z)) return;
        if (!lv.air(x, y, z) && !valid(lv, x, y, z)) return;
        int rock = 0;
        int hole = 0;
        int[][] sides = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { 0, -1, 0 } };
        for (int[] s : sides) {
            if (valid(lv, x + s[0], y + s[1], z + s[2])) rock++;
            if (lv.air(x + s[0], y + s[1], z + s[2])) hole++;
        }
        if (rock == 4 && hole == 1) lv.set(x, y, z, fluid, 0);
    }

    static int lavaY(Random r, int minY) {
        return Placement.veryBiasedToBottom(r, minY, Placement.BELOW_TOP_8, 8);
    }

    static int waterY(Random r, int minY) {
        return Placement.uniform(r, minY, 192);
    }
}
