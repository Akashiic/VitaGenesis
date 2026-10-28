package com.akashiic.vitagenesis.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.util.ForgeDirection;

import com.akashiic.vitagenesis.CaveBlocks;

/**
 * The lush caves of 1.18 with the blocks Et Futurum has: moss patches on floors (with azalea, flowering azalea, moss
 * carpet and grass) and ceilings (with cave vines), clay patches and clay pools, cave vines with glow berries, and
 * vines.
 * Dripleaves, spore blossoms and rooted azalea trees have no Et Futurum block (the tree also grows on the surface,
 * which
 * belongs to the 1.7.10 world) and are left out; their patches still form.
 */
final class LushCaves {

    private LushCaves() {}

    /** {@code #moss_replaceable}: base stone, cave vines, dirt. */
    static boolean mossReplaceable(Block b) {
        return CaveBlocks.isBaseStone(b) || b == Blocks.dirt
            || b == Blocks.grass
            || b != null && (b == CaveBlocks.caveVine || b == CaveBlocks.caveVinePlant);
    }

    /** {@code #lush_ground_replaceable}: moss replaceable, clay, gravel, sand. */
    static boolean lushGroundReplaceable(Block b) {
        return mossReplaceable(b) || b == Blocks.clay || b == Blocks.gravel || b == Blocks.sand;
    }

    interface Replaceable {

        boolean test(Block b);
    }

    interface Vegetation {

        /** Places the vegetation at the block in front of a ground block. */
        void place(Level lv, Random r, int x, int y, int z);
    }

    /**
     * {@code VegetationPatchFeature}: replaces the floor (dy = -1) or ceiling (dy = +1) around the origin with
     * {@code ground}, {@code depth} blocks deep, then puts vegetation on some of it.
     *
     * @return the ground positions
     */
    static Set<Long> vegetationPatch(Level lv, Random r, int ox, int oy, int oz, int dy, Replaceable replaceable,
        Block ground, int depth, float extraBottomChance, int verticalRange, float vegetationChance,
        float extraEdgeChance, Vegetation vegetation, boolean waterlogged) {
        int rx = Placement.uniform(r, 4, 7) + 1;
        int rz = Placement.uniform(r, 4, 7) + 1;
        Set<Long> placed = new HashSet<>();
        List<long[]> order = new ArrayList<>();
        ForgeDirection toSurface = dy < 0 ? ForgeDirection.DOWN : ForgeDirection.UP;
        for (int x = -rx; x <= rx; x++) {
            boolean edgeX = x == -rx || x == rx;
            for (int z = -rz; z <= rz; z++) {
                boolean edgeZ = z == -rz || z == rz;
                boolean corner = edgeX && edgeZ;
                boolean edge = (edgeX || edgeZ) && !corner;
                if (corner || edge && (extraEdgeChance == 0.0F || r.nextFloat() > extraEdgeChance)) continue;
                int px = ox + x;
                int pz = oz + z;
                int py = oy;
                for (int k = 0; lv.air(px, py, pz) && k < verticalRange; k++) py += dy;
                for (int k = 0; !lv.air(px, py, pz) && k < verticalRange; k++) py -= dy;
                int sy = py + dy;
                if (!lv.air(px, py, pz) || !lv.sturdy(px, sy, pz, toSurface.getOpposite())) continue;
                int d = depth + (extraBottomChance > 0.0F && r.nextFloat() < extraBottomChance ? 1 : 0);
                if (placeGround(lv, px, sy, pz, dy, d, ground, replaceable)) {
                    long key = key(px, sy, pz);
                    if (placed.add(key)) order.add(new long[] { px, sy, pz });
                }
            }
        }
        List<long[]> result = order;
        if (waterlogged) {
            // WaterloggedVegetationPatchFeature: enclosed ground positions become water
            List<long[]> water = new ArrayList<>();
            for (long[] p : order) {
                int x = (int) p[0];
                int y = (int) p[1];
                int z = (int) p[2];
                if (!exposed(lv, x, y, z)) water.add(p);
            }
            for (long[] p : water) lv.set((int) p[0], (int) p[1], (int) p[2], Blocks.water, 0);
            result = water;
        }
        if (vegetation != null && vegetationChance > 0.0F) {
            for (long[] p : result) {
                if (r.nextFloat() < vegetationChance) vegetation.place(lv, r, (int) p[0], (int) p[1] - dy, (int) p[2]);
            }
        }
        Set<Long> keys = new HashSet<>();
        for (long[] p : result) keys.add(key((int) p[0], (int) p[1], (int) p[2]));
        return keys;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    private static boolean placeGround(Level lv, int x, int y, int z, int dy, int depth, Block ground,
        Replaceable replaceable) {
        for (int i = 0; i < depth; i++) {
            Block current = lv.block(x, y, z);
            if (current != ground) {
                if (!replaceable.test(current)) return i != 0;
                lv.set(x, y, z, ground, 0);
            }
            y += dy;
        }
        return true;
    }

    private static boolean exposed(Level lv, int x, int y, int z) {
        return !lv.sturdy(x, y, z - 1, ForgeDirection.SOUTH) || !lv.sturdy(x + 1, y, z, ForgeDirection.WEST)
            || !lv.sturdy(x, y, z + 1, ForgeDirection.NORTH)
            || !lv.sturdy(x - 1, y, z, ForgeDirection.EAST)
            || !lv.sturdy(x, y - 1, z, ForgeDirection.UP);
    }

    // ------------------------------------------------------------------------------------------- features

    /** {@code lush_caves_ceiling_vegetation}: moss on the ceiling, cave vines (0..3 + tip) on 8% of it. */
    static void ceilingVegetation(Level lv, Random r, int x, int y, int z) {
        vegetationPatch(
            lv,
            r,
            x,
            y,
            z,
            1,
            LushCaves::mossReplaceable,
            CaveBlocks.mossBlock,
            1,
            0.0F,
            5,
            0.08F,
            0.3F,
            (l, rr, a, b, c) -> caveVines(l, rr, a, b, c, 0, 3),
            false);
    }

    /** {@code lush_caves_vegetation}: moss on the floor, plants on 80% of it. */
    static void floorVegetation(Level lv, Random r, int x, int y, int z) {
        vegetationPatch(
            lv,
            r,
            x,
            y,
            z,
            -1,
            LushCaves::mossReplaceable,
            CaveBlocks.mossBlock,
            1,
            0.0F,
            5,
            0.8F,
            0.3F,
            LushCaves::mossVegetation,
            false);
    }

    /** {@code lush_caves_clay}: a clay patch or a clay pool (their dripleaves have no Et Futurum block). */
    static void clay(Level lv, Random r, int x, int y, int z) {
        if (r.nextBoolean()) {
            vegetationPatch(
                lv,
                r,
                x,
                y,
                z,
                -1,
                LushCaves::lushGroundReplaceable,
                Blocks.clay,
                3,
                0.8F,
                2,
                0.0F,
                0.7F,
                null,
                false);
        } else {
            vegetationPatch(
                lv,
                r,
                x,
                y,
                z,
                -1,
                LushCaves::lushGroundReplaceable,
                Blocks.clay,
                3,
                0.8F,
                5,
                0.0F,
                0.7F,
                null,
                true);
        }
    }

    /** {@code moss_vegetation}: flowering azalea 4, azalea 7, moss carpet 25, grass 50, tall grass 10. */
    static void mossVegetation(Level lv, Random r, int x, int y, int z) {
        if (!lv.air(x, y, z)) return;
        int w = r.nextInt(96);
        if (w < 11) {
            if (CaveBlocks.azalea != null) placePlant(lv, x, y, z, CaveBlocks.azalea, w < 4 ? 1 : 0);
        } else if (w < 36) {
            if (CaveBlocks.mossCarpet != null) placePlant(lv, x, y, z, CaveBlocks.mossCarpet, 0);
        } else if (w < 86) {
            placePlant(lv, x, y, z, Blocks.tallgrass, 1);
        } else if (lv.air(x, y + 1, z) && Blocks.double_plant.canBlockStay(lv.world, x, y, z)) {
            lv.set(x, y, z, Blocks.double_plant, 2);
            lv.set(x, y + 1, z, Blocks.double_plant, 8);
        }
    }

    /** {@code simple_block}: only where the block can stay. */
    private static void placePlant(Level lv, int x, int y, int z, Block block, int meta) {
        if (block.canBlockStay(lv.world, x, y, z)) lv.set(x, y, z, block, meta);
    }

    /** {@code cave_vines}: from below a ceiling, 0..19 vine blocks and a tip, glow berries on one in five. */
    static void caveVinesColumn(Level lv, Random r, int x, int y, int z) {
        caveVines(lv, r, x, y, z, 0, 19);
    }

    /**
     * {@code block_column} of cave vines pointing down, with the tip kept when the column is cut short
     * ({@code prioritizeTip}). The tip's tile entity gets a maximum length of the column plus what the 1.18 tip age
     * (23..25 of 25) leaves to grow.
     */
    static void caveVines(Level lv, Random r, int x, int y, int z, int minBody, int maxBody) {
        if (CaveBlocks.caveVine == null || CaveBlocks.caveVinePlant == null) return;
        int body = Placement.uniform(r, minBody, maxBody);
        int total = body + 1;
        int free = 0;
        while (free < total && lv.air(x, y - free, z)) free++;
        if (free == 0) return;
        if (free < total) body = free - 1; // the tip has priority over the body
        int yy = y;
        for (int i = 0; i < body; i++) {
            lv.set(x, yy--, z, CaveBlocks.caveVinePlant, r.nextInt(5) == 0 ? 1 : 0);
        }
        int berries = r.nextInt(5) == 0 ? 1 : 0;
        int age = Placement.uniform(r, 23, 25);
        if (lv.set(x, yy, z, CaveBlocks.caveVine, berries)) {
            Level.setTileInt(lv.tile(x, yy, z), "MaxLength", body + 1 + (25 - age));
        }
    }

    /** {@code vines}: a vine on an air block next to a solid face (not the floor), vanilla 1.7.10 vine. */
    static void vines(Level lv, Random r, int x, int y, int z) {
        if (!lv.air(x, y, z)) return;
        // 1.18 Direction order without DOWN: up, north, south, west, east; 1.7.10 vine bits: south 1, west 2, north 4,
        // east 8
        if (vineSupport(lv.block(x, y + 1, z))) {
            lv.set(x, y, z, Blocks.vine, 0);
        } else if (vineSupport(lv.block(x, y, z - 1))) {
            lv.set(x, y, z, Blocks.vine, 4);
        } else if (vineSupport(lv.block(x, y, z + 1))) {
            lv.set(x, y, z, Blocks.vine, 1);
        } else if (vineSupport(lv.block(x - 1, y, z))) {
            lv.set(x, y, z, Blocks.vine, 2);
        } else if (vineSupport(lv.block(x + 1, y, z))) {
            lv.set(x, y, z, Blocks.vine, 8);
        }
    }

    /** 1.7.10 {@code BlockVine.func_150093_a}: a full block that blocks movement. */
    private static boolean vineSupport(Block b) {
        return b.renderAsNormalBlock() && b.getMaterial()
            .blocksMovement();
    }
}
