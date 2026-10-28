package com.akashiic.vitagenesis.feature;

import java.util.Random;

import com.akashiic.vitagenesis.CaveBlocks;
import com.akashiic.vitagenesis.noise.Mth;

/**
 * The dripstone caves of 1.18: {@code LargeDripstoneFeature}, {@code DripstoneClusterFeature} and
 * {@code PointedDripstoneFeature} with their {@code dripstone_caves} configurations, plus {@code DripstoneUtils}.
 * Pointed dripstone uses the metadata of Et Futurum: 0..4 point down (tip, frustum, middle, base, tip merge), 5..9 the
 * same pointing up. Et Futurum has no waterlogging, so pointed dripstone is only placed in air.
 */
final class Dripstone {

    static final int TIP = 0;
    static final int FRUSTUM = 1;
    static final int MIDDLE = 2;
    static final int BASE = 3;
    static final int TIP_MERGE = 4;
    static final int UP = 5;

    private Dripstone() {}

    // ---------------------------------------------------------------------------------------------- DripstoneUtils

    static boolean emptyOrWater(Level lv, int x, int y, int z) {
        return lv.airOrWater(x, y, z);
    }

    static boolean emptyOrWaterOrLava(Level lv, int x, int y, int z) {
        return lv.airOrFluid(x, y, z);
    }

    static boolean base(Level lv, int x, int y, int z) {
        return lv.block(x, y, z) == CaveBlocks.dripstoneBlock || lv.baseStone(x, y, z);
    }

    static boolean placeDripstoneBlockIfPossible(Level lv, int x, int y, int z) {
        if (!lv.baseStone(x, y, z)) return false; // #dripstone_replaceable_blocks
        return lv.set(x, y, z, CaveBlocks.dripstoneBlock, 0);
    }

    /**
     * {@code growPointedDripstone}: a column of {@code height} from {@code y} towards {@code dy} (+1 up, -1 down). The
     * thickness of each block is the one Et Futurum's own update ({@code BlockPointedDripstone.setState}) gives it, so
     * a
     * neighbour update never changes it: tip, frustum, then middle by the distance to the tip, the root a base only
     * when the column is at least 4 tall (1.18 also calls the root of a 3-tall column a base).
     */
    static void growPointedDripstone(Level lv, int x, int y, int z, int dy, int height, boolean merge) {
        if (height <= 0 || !base(lv, x, y - dy, z)) return;
        int offset = dy > 0 ? UP : 0;
        for (int i = 0; i < height; i++) {
            int yy = y + i * dy;
            if (!lv.air(x, yy, z)) return;
            int toTip = height - 1 - i;
            int thickness = Math.min(toTip, MIDDLE);
            if (i == 0 && height >= 4) thickness = BASE;
            if (toTip == 0 && merge) thickness = TIP_MERGE;
            lv.set(x, yy, z, CaveBlocks.pointedDripstone, thickness + offset);
        }
    }

    /** {@code DripstoneUtils.getDripstoneHeight}. */
    static double dripstoneHeight(double r, double radius, double scale, double bluntness) {
        if (r < bluntness) r = bluntness;
        double d1 = r / radius * 0.384D;
        double d2 = 0.75D * Math.pow(d1, 1.3333333333333333D);
        double d3 = Math.pow(d1, 0.6666666666666666D);
        double d4 = 0.3333333333333333D * Math.log(d1);
        double d5 = Math.max(scale * (d2 - d3 - d4), 0.0D);
        return d5 / 0.384D * radius;
    }

    static boolean circleMostlyEmbeddedInStone(Level lv, int x, int y, int z, int radius) {
        if (emptyOrWaterOrLava(lv, x, y, z)) return false;
        float step = 6.0F / radius;
        for (float a = 0.0F; a < (float) Math.PI * 2.0F; a += step) {
            int i = (int) (Mth.cos(a) * radius);
            int j = (int) (Mth.sin(a) * radius);
            if (emptyOrWaterOrLava(lv, x + i, y, z + j)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ LargeDripstoneFeature

    /** {@code large_dripstone}: search range 30, column radius 3..19, height scale 0.4..2, radius/height 0.33. */
    static void large(Level lv, Random r, int x, int y, int z) {
        if (!emptyOrWater(lv, x, y, z)) return;
        Placement.Column col = Placement.Column.scan(
            x,
            y,
            z,
            30,
            (a, b, c) -> emptyOrWater(lv, a, b, c),
            (a, b, c) -> base(lv, a, b, c) || lv.lava(a, b, c));
        if (col == null || !col.hasFloor() || !col.hasCeiling()) return;
        int height = col.height();
        if (height < 4) return;
        int maxRadius = Mth.clamp((int) (height * 0.33F), 3, 19);
        int radius = Placement.uniform(r, 3, maxRadius);
        Large stalactite = new Large(
            x,
            col.ceiling - 1,
            z,
            false,
            radius,
            Placement.uniform(r, 0.3F, 0.9F),
            Placement.uniform(r, 0.4F, 2.0F));
        Large stalagmite = new Large(
            x,
            col.floor + 1,
            z,
            true,
            radius,
            Placement.uniform(r, 0.4F, 1.0F),
            Placement.uniform(r, 0.4F, 2.0F));
        Wind wind = stalactite.suitableForWind() && stalagmite.suitableForWind() ? new Wind(y, r) : Wind.NONE;
        boolean a = stalactite.settle(lv, wind);
        boolean b = stalagmite.settle(lv, wind);
        if (a) stalactite.place(lv, r, wind);
        if (b) stalagmite.place(lv, r, wind);
    }

    private static final class Large {

        int x;
        int y;
        int z;
        final boolean up;
        int radius;
        final double bluntness;
        final double scale;

        Large(int x, int y, int z, boolean up, int radius, double bluntness, double scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.up = up;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        int heightAt(float r) {
            return (int) dripstoneHeight(r, this.radius, this.scale, this.bluntness);
        }

        boolean suitableForWind() {
            return this.radius >= 4 && this.bluntness >= 0.6D;
        }

        /** {@code moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary}. */
        boolean settle(Level lv, Wind wind) {
            while (this.radius > 1) {
                int yy = this.y;
                int steps = Math.min(10, this.heightAt(0.0F));
                for (int j = 0; j < steps; j++) {
                    if (lv.lava(this.x, yy, this.z)) return false;
                    if (circleMostlyEmbeddedInStone(lv, wind.x(this.x, yy), yy, wind.z(this.z, yy), this.radius)) {
                        this.y = yy;
                        return true;
                    }
                    yy += this.up ? -1 : 1;
                }
                this.radius /= 2;
            }
            return false;
        }

        void place(Level lv, Random r, Wind wind) {
            for (int i = -this.radius; i <= this.radius; i++) {
                for (int j = -this.radius; j <= this.radius; j++) {
                    float d = (float) Math.sqrt(i * i + j * j);
                    if (d > this.radius) continue;
                    int h = this.heightAt(d);
                    if (h <= 0) continue;
                    if (r.nextFloat() < 0.2D) h = (int) (h * Placement.uniform(r, 0.8F, 1.0F));
                    int yy = this.y;
                    boolean placed = false;
                    for (int k = 0; k < h; k++) {
                        int px = wind.x(this.x + i, yy);
                        int pz = wind.z(this.z + j, yy);
                        if (emptyOrWaterOrLava(lv, px, yy, pz)) {
                            placed = true;
                            lv.set(px, yy, pz, CaveBlocks.dripstoneBlock, 0);
                        } else if (placed && lv.baseStone(px, yy, pz)) {
                            break;
                        }
                        yy += this.up ? 1 : -1;
                    }
                }
            }
        }
    }

    /** {@code WindOffsetter}: leans large dripstone sideways by a constant speed per block from the origin. */
    private static final class Wind {

        static final Wind NONE = new Wind();

        private final int originY;
        private final double vx;
        private final double vz;
        private final boolean active;

        Wind(int originY, Random r) {
            this.originY = originY;
            float speed = Placement.uniform(r, 0.0F, 0.3F);
            float angle = Placement.uniform(r, 0.0F, (float) Math.PI);
            this.vx = Mth.cos(angle) * speed;
            this.vz = Mth.sin(angle) * speed;
            this.active = true;
        }

        private Wind() {
            this.originY = 0;
            this.vx = 0.0D;
            this.vz = 0.0D;
            this.active = false;
        }

        // BlockPos.offset(double, double, double) floors each coordinate
        int x(int x, int y) {
            return this.active ? Mth.floor(x + this.vx * (this.originY - y)) : x;
        }

        int z(int z, int y) {
            return this.active ? Mth.floor(z + this.vz * (this.originY - y)) : z;
        }
    }

    // --------------------------------------------------------------------------------------- DripstoneClusterFeature

    /**
     * {@code dripstone_cluster}: search 12, height 3..6, radius 2..8, wetness N(0.1, 0.3) in 0.1..0.9, density
     * 0.3..0.7.
     */
    static void cluster(Level lv, Random r, int x, int y, int z) {
        if (!emptyOrWater(lv, x, y, z)) return;
        int height = Placement.uniform(r, 3, 6);
        float wetness = Placement.clampedNormal(r, 0.1F, 0.3F, 0.1F, 0.9F);
        float density = Placement.uniform(r, 0.3F, 0.7F);
        int rx = Placement.uniform(r, 2, 8);
        int rz = Placement.uniform(r, 2, 8);
        for (int i = -rx; i <= rx; i++) {
            for (int j = -rz; j <= rz; j++) {
                double chance = columnChance(rx, rz, i, j);
                clusterColumn(lv, r, x + i, y, z + j, i, j, wetness, chance, height, density);
            }
        }
    }

    private static double columnChance(int rx, int rz, int x, int z) {
        int edge = Math.min(rx - Math.abs(x), rz - Math.abs(z));
        // chance 0.1 at the edge, 1 from 3 blocks inside
        return Mth.clampedMap(edge, 0.0D, 3.0D, 0.1D, 1.0D);
    }

    private static void clusterColumn(Level lv, Random r, int x, int y, int z, int dx, int dz, float wetness,
        double chance, int height, float density) {
        Placement.Column col = Placement.Column
            .scan(x, y, z, 12, (a, b, c) -> emptyOrWater(lv, a, b, c), (a, b, c) -> !emptyOrWater(lv, a, b, c));
        if (col == null || !col.hasCeiling() && !col.hasFloor()) return;
        int ceiling = col.ceiling;
        int floor = col.floor;
        boolean wet = r.nextFloat() < wetness;
        if (wet && col.hasFloor() && canPlacePool(lv, x, floor, z)) {
            lv.set(x, floor, z, net.minecraft.init.Blocks.water, 0);
            floor = floor - 1;
        }
        boolean hasFloor = floor != Placement.Column.NONE;
        int stalactite;
        if (col.hasCeiling() && r.nextDouble() < chance && !lv.lava(x, ceiling, z)) {
            replaceWithDripstoneBlocks(lv, x, ceiling, z, Placement.uniform(r, 2, 4), 1);
            int limit = hasFloor ? Math.min(height, ceiling - floor) : height;
            stalactite = columnHeight(r, dx, dz, density, limit);
        } else {
            stalactite = 0;
        }
        int stalagmite;
        if (hasFloor && r.nextDouble() < chance && !lv.lava(x, floor, z)) {
            replaceWithDripstoneBlocks(lv, x, floor, z, Placement.uniform(r, 2, 4), -1);
            stalagmite = col.hasCeiling() ? Math.max(0, stalactite + Placement.uniform(r, -1, 1))
                : columnHeight(r, dx, dz, density, height);
        } else {
            stalagmite = 0;
        }
        int top;
        int bottom;
        if (col.hasCeiling() && hasFloor && ceiling - stalactite <= floor + stalagmite) {
            int lo = Math.max(ceiling - stalactite, floor + 1);
            int hi = Math.min(floor + stalagmite, ceiling - 1);
            int meet = Placement.uniform(r, lo, hi + 1);
            top = ceiling - meet;
            bottom = meet - 1 - floor;
        } else {
            top = stalactite;
            bottom = stalagmite;
        }
        int columnHeight = col.hasCeiling() && hasFloor ? ceiling - floor - 1 : -1;
        boolean merge = r.nextBoolean() && top > 0 && bottom > 0 && top + bottom == columnHeight;
        if (col.hasCeiling()) growPointedDripstone(lv, x, ceiling - 1, z, -1, top, merge);
        if (hasFloor) growPointedDripstone(lv, x, floor + 1, z, 1, bottom, merge);
    }

    private static int columnHeight(Random r, int dx, int dz, float density, int maxHeight) {
        if (r.nextFloat() > density) return 0;
        int d = Math.abs(dx) + Math.abs(dz);
        float mean = (float) Mth.clampedMap(d, 0.0D, 8.0D, maxHeight / 2.0D, 0.0D);
        return (int) Placement.clampedNormal(r, mean, 3.0F, 0.0F, maxHeight);
    }

    private static boolean canPlacePool(Level lv, int x, int y, int z) {
        if (lv.water(x, y, z) || lv.block(x, y, z) == CaveBlocks.dripstoneBlock
            || lv.block(x, y, z) == CaveBlocks.pointedDripstone) {
            return false;
        }
        if (lv.water(x, y + 1, z)) return false;
        return adjacentToWaterOk(lv, x + 1, y, z) && adjacentToWaterOk(lv, x - 1, y, z)
            && adjacentToWaterOk(lv, x, y, z + 1)
            && adjacentToWaterOk(lv, x, y, z - 1)
            && adjacentToWaterOk(lv, x, y - 1, z);
    }

    private static boolean adjacentToWaterOk(Level lv, int x, int y, int z) {
        return lv.baseStone(x, y, z) || lv.water(x, y, z);
    }

    private static void replaceWithDripstoneBlocks(Level lv, int x, int y, int z, int thickness, int dy) {
        for (int i = 0; i < thickness; i++) {
            if (!placeDripstoneBlockIfPossible(lv, x, y + i * dy, z)) return;
        }
    }

    // ---------------------------------------------------------------------------------------- PointedDripstoneFeature

    /** {@code pointed_dripstone}: taller 0.2, directional spread 0.7, radius 2 / 3 spread 0.5. */
    static void pointed(Level lv, Random r, int x, int y, int z) {
        boolean up = base(lv, x, y + 1, z);
        boolean down = base(lv, x, y - 1, z);
        int dy;
        if (up && down) {
            dy = r.nextBoolean() ? -1 : 1;
        } else if (up) {
            dy = -1;
        } else if (down) {
            dy = 1;
        } else {
            return;
        }
        int rootY = y - dy;
        patch(lv, r, x, rootY, z);
        int height = r.nextFloat() < 0.2F && emptyOrWater(lv, x, y + dy, z) ? 2 : 1;
        growPointedDripstone(lv, x, y, z, dy, height, false);
    }

    private static final int[][] HORIZONTAL = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }; // N, E, S, W

    private static void patch(Level lv, Random r, int x, int y, int z) {
        placeDripstoneBlockIfPossible(lv, x, y, z);
        for (int[] d : HORIZONTAL) {
            if (r.nextFloat() > 0.7F) continue;
            int px = x + d[0];
            int pz = z + d[1];
            placeDripstoneBlockIfPossible(lv, px, y, pz);
            if (r.nextFloat() > 0.5F) continue;
            int s = r.nextInt(6);
            int qx = px + Geode.DX[s];
            int qy = y + Geode.DY[s];
            int qz = pz + Geode.DZ[s];
            placeDripstoneBlockIfPossible(lv, qx, qy, qz);
            if (r.nextFloat() > 0.5F) continue;
            int t = r.nextInt(6);
            placeDripstoneBlockIfPossible(lv, qx + Geode.DX[t], qy + Geode.DY[t], qz + Geode.DZ[t]);
        }
    }
}
