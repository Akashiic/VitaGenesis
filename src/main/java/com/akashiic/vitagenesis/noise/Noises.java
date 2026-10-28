package com.akashiic.vitagenesis.noise;

/**
 * The 1.18 noise parameters ({@code NoiseData}) used below Y 0, and their instantiation for a world seed the way
 * {@code RandomState} does it: a Xoroshiro positional factory forked from the seed, one stream per noise keyed by
 * the MD5 of its resource location.
 */
public final class Noises {

    /** {@code NormalNoise.NoiseParameters}: first octave and the amplitude of each octave. */
    public static final class Params {

        public final String key;
        public final int firstOctave;
        public final double[] amplitudes;

        Params(String key, int firstOctave, double... amplitudes) {
            this.key = key;
            this.firstOctave = firstOctave;
            this.amplitudes = amplitudes;
        }
    }

    // caves
    public static final Params CAVE_CHEESE = new Params("cave_cheese", -8, 0.5, 1, 2, 1, 2, 1, 0, 2, 0);
    public static final Params CAVE_LAYER = new Params("cave_layer", -8, 1);
    public static final Params CAVE_ENTRANCE = new Params("cave_entrance", -7, 0.4, 0.5, 1);
    public static final Params SPAGHETTI_2D = new Params("spaghetti_2d", -7, 1);
    public static final Params SPAGHETTI_2D_ELEVATION = new Params("spaghetti_2d_elevation", -8, 1);
    public static final Params SPAGHETTI_2D_MODULATOR = new Params("spaghetti_2d_modulator", -11, 1);
    public static final Params SPAGHETTI_2D_THICKNESS = new Params("spaghetti_2d_thickness", -11, 1);
    public static final Params SPAGHETTI_3D_1 = new Params("spaghetti_3d_1", -7, 1);
    public static final Params SPAGHETTI_3D_2 = new Params("spaghetti_3d_2", -7, 1);
    public static final Params SPAGHETTI_3D_RARITY = new Params("spaghetti_3d_rarity", -11, 1);
    public static final Params SPAGHETTI_3D_THICKNESS = new Params("spaghetti_3d_thickness", -8, 1);
    public static final Params SPAGHETTI_ROUGHNESS = new Params("spaghetti_roughness", -5, 1);
    public static final Params SPAGHETTI_ROUGHNESS_MODULATOR = new Params("spaghetti_roughness_modulator", -8, 1);
    public static final Params NOODLE = new Params("noodle", -8, 1);
    public static final Params NOODLE_THICKNESS = new Params("noodle_thickness", -8, 1);
    public static final Params NOODLE_RIDGE_A = new Params("noodle_ridge_a", -7, 1);
    public static final Params NOODLE_RIDGE_B = new Params("noodle_ridge_b", -7, 1);
    public static final Params PILLAR = new Params("pillar", -7, 1, 1);
    public static final Params PILLAR_RARENESS = new Params("pillar_rareness", -8, 1);
    public static final Params PILLAR_THICKNESS = new Params("pillar_thickness", -8, 1);

    // aquifers
    public static final Params AQUIFER_BARRIER = new Params("aquifer_barrier", -3, 1);
    public static final Params AQUIFER_FLUID_LEVEL_FLOODEDNESS = new Params("aquifer_fluid_level_floodedness", -7, 1);
    public static final Params AQUIFER_FLUID_LEVEL_SPREAD = new Params("aquifer_fluid_level_spread", -5, 1);
    public static final Params AQUIFER_LAVA = new Params("aquifer_lava", -1, 1);

    // ore veins
    public static final Params ORE_VEININESS = new Params("ore_veininess", -8, 1);
    public static final Params ORE_VEIN_A = new Params("ore_vein_a", -7, 1);
    public static final Params ORE_VEIN_B = new Params("ore_vein_b", -7, 1);
    public static final Params ORE_GAP = new Params("ore_gap", -5, 1);

    // climate, for the selection of cave biomes
    public static final Params SHIFT = new Params("offset", -3, 1, 1, 1, 0);
    public static final Params VEGETATION = new Params("vegetation", -8, 1, 1, 0, 0, 0, 0);
    public static final Params CONTINENTALNESS = new Params("continentalness", -9, 1, 1, 2, 2, 2, 1, 1, 1, 1);

    /** Roughness of geode shells (1.18 makes it from the world seed with first octave -4, amplitude 1). */
    public static final Params GEODE = new Params("vitagenesis/geode", -4, 1);

    private final XoroshiroRandom.Positional root;

    public Noises(long seed) {
        this.root = new XoroshiroRandom(seed).forkPositional();
    }

    public NormalNoise create(Params params) {
        return new NormalNoise(this.root.fromHashOf("minecraft:" + params.key), params);
    }

    /**
     * A positional factory for a named stream, as {@code RandomState} forks "minecraft:aquifer" and "minecraft:ore".
     */
    public XoroshiroRandom.Positional positional(String key) {
        return this.root.fromHashOf("minecraft:" + key)
            .forkPositional();
    }
}
