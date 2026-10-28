package com.akashiic.vitagenesis;

import com.akashiic.vitagenesis.terrain.DeepCaves;
import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = VitaGenesis.MODID, category = "caves")
@Config.Comment({ "1.18 caves in the deep layer of Below Core (Y < 0). Nothing at Y >= 0 is ever changed.",
    "Changes apply to chunks whose deep layer is generated afterwards; generated chunks are never regenerated." })
public class CaveConfig {

    @Config.Comment("Master switch of the cave generator.")
    @Config.DefaultBoolean(true)
    public static boolean enabled;

    @Config.Comment({ "Also carve the deep layer of chunks saved before Below Core was installed (an explored world",
        "getting its deep layer now). Only Y < 0 is touched, which is always freshly generated in those chunks." })
    @Config.DefaultBoolean(true)
    public static boolean retrogen;

    @Config.Comment("Noise caves of 1.18: cheese caverns, spaghetti and noodle tunnels, pillars.")
    @Config.DefaultBoolean(true)
    public static boolean noiseCaves;

    @Config.Comment({ "1.18 aquifers: water and lava pools at their own levels inside noise caves.",
        "Off: no aquifer water (1.18 would flood every cave below sea level instead); lava still fills",
        "everything below floor + 10." })
    @Config.DefaultBoolean(true)
    public static boolean aquifers;

    @Config.Comment("1.18 cave carvers (round tunnels and rooms), including the extra underground carver.")
    @Config.DefaultBoolean(true)
    public static boolean carvers;

    @Config.Comment("1.18 iron ore veins: deepslate iron ore, raw iron blocks and tuff along long ridges.")
    @Config.DefaultBoolean(true)
    public static boolean oreVeins;

    @Config.Comment({ "Solid layers always kept right below Y 0, between the deep caves and the 1.7.10 world above",
        "(whose layer at Y 0 is solid too). In 1.7.10 fire spreads up to 4 blocks up through solid blocks: with 4",
        "layers, fire in the deep caves (lava next to vines, moss, azalea) can never reach Y 0." })
    @Config.DefaultInt(4)
    @Config.RangeInt(min = 1, max = 16)
    public static int seal;

    @Config.Comment("Blocks below the seal over which the caves close smoothly instead of ending in a flat ceiling.")
    @Config.DefaultInt(8)
    @Config.RangeInt(min = 1, max = 32)
    public static int fade;

    public static DeepCaves.Settings settings() {
        DeepCaves.Settings s = new DeepCaves.Settings();
        s.noiseCaves = noiseCaves;
        s.aquifers = aquifers;
        s.carvers = carvers;
        s.extraUndergroundCarver = carvers;
        s.oreVeins = oreVeins;
        s.seal = seal;
        s.fade = fade;
        return s;
    }
}
