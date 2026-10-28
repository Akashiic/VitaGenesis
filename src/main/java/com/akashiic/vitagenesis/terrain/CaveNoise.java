package com.akashiic.vitagenesis.terrain;

import com.akashiic.vitagenesis.noise.Mth;
import com.akashiic.vitagenesis.noise.Noises;
import com.akashiic.vitagenesis.noise.NormalNoise;
import com.akashiic.vitagenesis.noise.XoroshiroRandom;

/**
 * The underground part of the 1.18 overworld noise router ({@code NoiseRouterData}): cheese caves, spaghetti (2D and
 * 3D, with the entrances function), noodles, pillars, the aquifer noises and the ore vein noises, for one world seed.
 * <p>
 * Below Y 0 the terrain density ({@code sloped_cheese}) is always far above the 1.5625 threshold, so the router always
 * takes its {@code underground} branch and the cheese term {@code clamp(1.5 - 0.64 * sloped_cheese, 0, 0.5)} is 0; both
 * are folded in here. Vertical anchors that 1.18 places at the bottom of the world ({@code -64}) follow the floor
 * through {@code yRel = y - (minY + 64)}, so a floor of -64 gives exactly the 1.18 functions and a deeper floor shifts
 * the bottom of the profile down with it. The noises themselves are sampled at the real coordinates.
 * <p>
 * Thread-safe after construction: the noises are immutable.
 */
public final class CaveNoise {

    final NormalNoise caveLayer;
    final NormalNoise caveCheese;
    final NormalNoise caveEntrance;
    final NormalNoise spaghetti2d;
    final NormalNoise spaghetti2dElevation;
    final NormalNoise spaghetti2dModulator;
    final NormalNoise spaghetti2dThickness;
    final NormalNoise spaghetti3d1;
    final NormalNoise spaghetti3d2;
    final NormalNoise spaghetti3dRarity;
    final NormalNoise spaghetti3dThickness;
    final NormalNoise spaghettiRoughness;
    final NormalNoise spaghettiRoughnessModulator;
    final NormalNoise noodle;
    final NormalNoise noodleThickness;
    final NormalNoise noodleRidgeA;
    final NormalNoise noodleRidgeB;
    final NormalNoise pillar;
    final NormalNoise pillarRareness;
    final NormalNoise pillarThickness;

    final NormalNoise aquiferBarrier;
    final NormalNoise aquiferFloodedness;
    final NormalNoise aquiferSpread;
    final NormalNoise aquiferLava;
    final XoroshiroRandom.Positional aquiferRandom;

    final NormalNoise oreVeininess;
    final NormalNoise oreVeinA;
    final NormalNoise oreVeinB;
    final NormalNoise oreGap;
    final XoroshiroRandom.Positional oreRandom;

    public final long seed;

    public CaveNoise(long seed) {
        this.seed = seed;
        Noises n = new Noises(seed);
        this.caveLayer = n.create(Noises.CAVE_LAYER);
        this.caveCheese = n.create(Noises.CAVE_CHEESE);
        this.caveEntrance = n.create(Noises.CAVE_ENTRANCE);
        this.spaghetti2d = n.create(Noises.SPAGHETTI_2D);
        this.spaghetti2dElevation = n.create(Noises.SPAGHETTI_2D_ELEVATION);
        this.spaghetti2dModulator = n.create(Noises.SPAGHETTI_2D_MODULATOR);
        this.spaghetti2dThickness = n.create(Noises.SPAGHETTI_2D_THICKNESS);
        this.spaghetti3d1 = n.create(Noises.SPAGHETTI_3D_1);
        this.spaghetti3d2 = n.create(Noises.SPAGHETTI_3D_2);
        this.spaghetti3dRarity = n.create(Noises.SPAGHETTI_3D_RARITY);
        this.spaghetti3dThickness = n.create(Noises.SPAGHETTI_3D_THICKNESS);
        this.spaghettiRoughness = n.create(Noises.SPAGHETTI_ROUGHNESS);
        this.spaghettiRoughnessModulator = n.create(Noises.SPAGHETTI_ROUGHNESS_MODULATOR);
        this.noodle = n.create(Noises.NOODLE);
        this.noodleThickness = n.create(Noises.NOODLE_THICKNESS);
        this.noodleRidgeA = n.create(Noises.NOODLE_RIDGE_A);
        this.noodleRidgeB = n.create(Noises.NOODLE_RIDGE_B);
        this.pillar = n.create(Noises.PILLAR);
        this.pillarRareness = n.create(Noises.PILLAR_RARENESS);
        this.pillarThickness = n.create(Noises.PILLAR_THICKNESS);
        this.aquiferBarrier = n.create(Noises.AQUIFER_BARRIER);
        this.aquiferFloodedness = n.create(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS);
        this.aquiferSpread = n.create(Noises.AQUIFER_FLUID_LEVEL_SPREAD);
        this.aquiferLava = n.create(Noises.AQUIFER_LAVA);
        this.aquiferRandom = n.positional("aquifer");
        this.oreVeininess = n.create(Noises.ORE_VEININESS);
        this.oreVeinA = n.create(Noises.ORE_VEIN_A);
        this.oreVeinB = n.create(Noises.ORE_VEIN_B);
        this.oreGap = n.create(Noises.ORE_GAP);
        this.oreRandom = n.positional("ore");
    }

    /** Lower bound of the noodle band and of the ore vein interpolation ({@code -60} at a floor of -64). */
    static final int NOODLE_MIN_Y_REL = -60;
    static final int NOODLE_MAX_Y = 320;
    /** Bottom slide: density lerps from 0.1171875 (solid) at the floor to the cave density 24 blocks up. */
    static final int BOTTOM_SLIDE = 24;
    static final double BOTTOM_SLIDE_TARGET = 0.1171875D;

    /**
     * Density before interpolation: {@code slide(underground)} of the 1.18 router. Positive is solid.
     *
     * @param yRel {@code y - (minY + 64)}
     */
    public double base(int x, int y, int z, int yRel) {
        double layer = this.caveLayer.getValue(x, y * 8.0D, z);
        double cheese = Mth.clamp(0.27D + this.caveCheese.getValue(x, y * 0.6666666666666666D, z), -1.0D, 1.0D);
        double cheeseLayered = 4.0D * layer * layer + cheese;
        double roughness = this.roughness(x, y, z);
        double caves = Math.min(
            Math.min(cheeseLayered, this.entrances(x, y, z, roughness)),
            this.spaghetti2d(x, y, z, yRel) + roughness);
        double pillars = this.pillars(x, y, z);
        double underground = Math.max(caves, pillars < 0.03D ? -1000000.0D : pillars);
        double slide = Mth.clampedMap(yRel, -64, -64 + BOTTOM_SLIDE, 0.0D, 1.0D);
        return BOTTOM_SLIDE_TARGET + slide * (underground - BOTTOM_SLIDE_TARGET);
    }

    /** {@code spaghetti_roughness_function}. */
    double roughness(int x, int y, int z) {
        double modulator = -0.05D - 0.05D * this.spaghettiRoughnessModulator.getValue(x, y, z);
        return modulator * (Math.abs(this.spaghettiRoughness.getValue(x, y, z)) - 0.4D);
    }

    /** {@code caves/entrances}. Its y gradient is anchored to the surface (absolute), not to the floor. */
    double entrances(int x, int y, int z, double roughness) {
        double rarity = rarity3d(this.spaghetti3dRarity.getValue(x * 2.0D, y, z * 2.0D));
        double thickness = -0.0765D - 0.0115D * this.spaghetti3dThickness.getValue(x, y, z);
        double s1 = rarity * Math.abs(this.spaghetti3d1.getValue(x / rarity, y / rarity, z / rarity));
        double s2 = rarity * Math.abs(this.spaghetti3d2.getValue(x / rarity, y / rarity, z / rarity));
        double spaghetti3d = Mth.clamp(Math.max(s1, s2) + thickness, -1.0D, 1.0D);
        double entrance = this.caveEntrance.getValue(x * 0.75D, y * 0.5D, z * 0.75D) + 0.37D
            + Mth.clampedMap(y, -10, 30, 0.3D, 0.0D);
        return Math.min(entrance, roughness + spaghetti3d);
    }

    /** {@code caves/spaghetti_2d}: horizontal tunnels at an elevation that varies in x/z. */
    double spaghetti2d(int x, int y, int z, int yRel) {
        double rarity = rarity2d(this.spaghetti2dModulator.getValue(x * 2.0D, y, z * 2.0D));
        double s = rarity * Math.abs(this.spaghetti2d.getValue(x / rarity, y / rarity, z / rarity));
        double elevation = 8.0D * this.spaghetti2dElevation.getValue(x, 0.0D, z);
        double thickness = -0.95D - 0.35D * this.spaghetti2dThickness.getValue(x * 2.0D, y, z * 2.0D);
        double e = Math.abs(elevation + Mth.clampedMap(yRel, -64, 320, 8.0D, -40.0D)) + thickness;
        return Mth.clamp(Math.max(s + 0.083D * thickness, e * e * e), -1.0D, 1.0D);
    }

    /** {@code caves/pillars}. */
    double pillars(int x, int y, int z) {
        double p = this.pillar.getValue(x * 25.0D, y * 0.3D, z * 25.0D) * 2.0D
            + (-1.0D - this.pillarRareness.getValue(x, y, z));
        double t = 0.55D + 0.55D * this.pillarThickness.getValue(x, y, z);
        return p * t * t * t;
    }

    static double rarity3d(double v) {
        if (v < -0.5D) return 0.75D;
        if (v < 0.0D) return 1.0D;
        return v < 0.5D ? 1.5D : 2.0D;
    }

    static double rarity2d(double v) {
        if (v < -0.75D) return 0.5D;
        if (v < -0.5D) return 0.75D;
        if (v < 0.5D) return 1.0D;
        return v < 0.75D ? 2.0D : 3.0D;
    }

    /** Whether a corner is inside the noodle band; outside, each noodle input takes its fallback value. */
    static boolean inNoodleBand(int y, int yRel) {
        return yRel >= NOODLE_MIN_Y_REL && y <= NOODLE_MAX_Y;
    }

    double noodleToggle(int x, int y, int z) {
        return this.noodle.getValue(x, y, z);
    }

    double noodleThickness(int x, int y, int z) {
        return -0.075D - 0.025D * this.noodleThickness.getValue(x, y, z);
    }

    double noodleRidgeA(int x, int y, int z) {
        return this.noodleRidgeA.getValue(x * 2.6666666666666665D, y * 2.6666666666666665D, z * 2.6666666666666665D);
    }

    double noodleRidgeB(int x, int y, int z) {
        return this.noodleRidgeB.getValue(x * 2.6666666666666665D, y * 2.6666666666666665D, z * 2.6666666666666665D);
    }

    /** The noodle density from its four interpolated inputs ({@code rangeChoice(toggle, .., 0, 64, ...)}). */
    static double noodle(double toggle, double thickness, double ridgeA, double ridgeB) {
        if (toggle < 0.0D) return 64.0D;
        return thickness + 1.5D * Math.max(Math.abs(ridgeA), Math.abs(ridgeB));
    }

    /** {@code DensityFunctions.squeeze} after the 0.64 factor of {@code postProcess}. */
    static double postProcess(double interpolated) {
        double c = Mth.clamp(interpolated * 0.64D, -1.0D, 1.0D);
        return c / 2.0D - c * c * c / 24.0D;
    }
}
