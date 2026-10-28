package com.akashiic.vitagenesis;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = VitaGenesis.MODID, category = "features")
@Config.Comment({ "1.18 cave features in the deep layer, placed once a chunk and its 8 neighbours have their caves.",
    "Features whose Et Futurum blocks are missing are skipped (see the log at startup)." })
public class FeatureConfig {

    @Config.Comment("Amethyst geodes (amethyst, budding amethyst, calcite, smooth basalt).")
    @Config.DefaultBoolean(true)
    public static boolean geodes;

    @Config.Comment("Dripstone caves: large dripstone, dripstone clusters, pointed dripstone (EFR: enableDripstone).")
    @Config.DefaultBoolean(true)
    public static boolean dripstoneCaves;

    @Config.Comment("Lush caves: moss, azalea, moss carpet, cave vines with glow berries, clay pools (EFR: enableMossAzalea).")
    @Config.DefaultBoolean(true)
    public static boolean lushCaves;

    @Config.Comment("Glow lichen on cave ceilings and walls.")
    @Config.DefaultBoolean(true)
    public static boolean glowLichen;

    @Config.Comment("Magma blocks in the floor of underwater caves.")
    @Config.DefaultBoolean(true)
    public static boolean underwaterMagma;

    @Config.Comment("Dungeons in the deep layer (1.18 monster_room_deep), with the pack's dungeon mobs and loot.")
    @Config.DefaultBoolean(true)
    public static boolean monsterRooms;

    @Config.Comment("Water and lava springs in cave walls.")
    @Config.DefaultBoolean(true)
    public static boolean springs;

    @Config.Comment({ "Set fluids that touch open air flowing once the chunks around them are loaded, like 1.18 does",
        "(springs, aquifer edges). Off: they stay still until something next to them changes." })
    @Config.DefaultBoolean(true)
    public static boolean flowingFluids;

    @Config.Comment("Humidity from which a column has lush caves (1.18: 0.7). Lower: more lush caves.")
    @Config.DefaultDouble(0.7)
    @Config.RangeDouble(min = -1.0, max = 2.0)
    public static double lushHumidity;

    @Config.Comment({
        "Weight of the rainfall of the 1.7.10 biome above in the humidity. In 1.18 the humidity of the lush",
        "caves is that of the land above; here the land is 1.7.10 and carries its humidity in the biome rainfall.",
        "1.0: lush caves under ~11% of jungles and swamps, ~5% of forests, almost none under plains and deserts.",
        "0: 1.18 noise only (lush caves under ~0.2% of the world, wherever the noise is humid)." })
    @Config.DefaultDouble(1.0)
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double lushRainfallWeight;

    @Config.Comment("Continentalness from which a column has dripstone caves (1.18: 0.8). Lower: more dripstone caves.")
    @Config.DefaultDouble(0.8)
    @Config.RangeDouble(min = -1.0, max = 2.0)
    public static double dripstoneContinentalness;
}
