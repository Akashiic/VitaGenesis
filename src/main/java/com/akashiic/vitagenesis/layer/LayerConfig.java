package com.akashiic.vitagenesis.layer;

import com.akashiic.vitagenesis.VitaGenesis;
import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = VitaGenesis.MODID, category = "layer")
@Config.Comment({
    "The deep layer itself (Y < 0): what it is made of, its bedrock floor and its ores. VitaGenesis generates",
    "the whole layer; Below Core only stores it. Changes apply to chunks generated afterwards.",
    "While Below Core still places ores of its own, set worldgen.ores=false in config/akashicbellow.cfg." })
public class LayerConfig {

    @Config.Comment({ "Block that fills the deep layer, as modid:name[:meta].",
        "'auto' = etfuturum:deepslate when Et Futurum Requiem provides it, otherwise minecraft:stone." })
    @Config.DefaultString("auto")
    public static String fillBlock;

    @Config.Comment("1.18 ores, tuff and gravel below Y 0 (the 1.18 overworld list; deepslate variants from Et Futurum).")
    @Config.DefaultBoolean(true)
    public static boolean ores;

    @Config.Comment({
        "For floors other than -64: scale the count of ores whose range starts at the bottom of the world",
        "so that their density per block stays the 1.18 one." })
    @Config.DefaultBoolean(true)
    public static boolean scaleBottomAnchoredCounts;

    @Config.Comment("Multiplier of every ore count, the modded ones included (expected value; fractions are stochastic).")
    @Config.DefaultDouble(1.0)
    @Config.RangeDouble(min = 0.0, max = 16.0)
    public static double oreCountMultiplier;

    @Config.Comment({ "Ores of other mods below Y 0, one per line: ore;size;count;minY;maxY[;discardOnAir]",
        "  ore: an ore dictionary name (oreDraconium) or modid:name[:meta] of the ore as its mod generates it; in",
        "       deepslate it becomes Et Futurum's deepslate variant (modcompat.cfg: moddedDeepslateOres), if any.",
        "  size: 1.18 vein size (0..64); count: veins per chunk (fractions allowed);",
        "  minY/maxY: a height (-40) or a height above the floor (bottom+16); uniform between them.",
        "  discardOnAir: chance (0..1) to skip each block touching air (1.18 buried ores use 0.5..1). Default 0.",
        "Defaults: draconium as rare as Draconic Evolution makes it above 0 (2 veins per chunk) but near the floor;",
        "certus quartz about as dense per block as Applied Energistics 2 makes it above 0." })
    @Config.DefaultStringList({ "oreDraconium;4;2;bottom+0;bottom+16", "oreCertusQuartz;4;8;-32;-1",
        "oreChargedCertusQuartz;4;1;-32;-1" })
    public static String[] moddedOres;
}
