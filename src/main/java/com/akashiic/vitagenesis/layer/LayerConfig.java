package com.akashiic.vitagenesis.layer;

import com.akashiic.vitagenesis.VitaGenesis;
import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = VitaGenesis.MODID, category = "layer")
@Config.Comment({
    "The deep layer itself (Y < 0): what it is made of, its bedrock floor and its ores. VitaGenesis generates",
    "the whole layer; Below Core only stores it. Changes apply to chunks generated afterwards." })
public class LayerConfig {

    @Config.Comment({ "Block that fills the deep layer, as modid:name[:meta].",
        "'auto' = etfuturum:deepslate when Et Futurum Requiem provides it, otherwise minecraft:stone.",
        "Ores replace only stone, Et Futurum's deepslate and tuff: in another fill block no ore is placed." })
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
        "Defaults: every ore of the TEOF 4.1 pack Et Futurum has a deepslate variant of, as dense per block as the",
        "mod that generates the most of it makes it above 0 (measured in the pack: docs/DESIGN.md), over the whole",
        "layer; draconium near the floor, as Draconic Evolution puts it near bedrock; amber in the upper half.",
        "Ores of mods that are not installed are skipped." })
    @Config.DefaultStringList({
        // Ars Magica 2
        "oreVinteum;4;8;bottom+0;-1", "oreChimerite;4;10;bottom+0;-1", "oreBlueTopaz;4;8;bottom+0;-1",
        // Thaumcraft
        "oreCinnabar;4;9;bottom+0;-1", "oreInfusedAir;4;2;bottom+0;-1", "oreInfusedFire;4;1.5;bottom+0;-1",
        "oreInfusedWater;4;1.6;bottom+0;-1", "oreInfusedEarth;4;3.5;bottom+0;-1", "oreInfusedOrder;4;2.2;bottom+0;-1",
        "oreInfusedEntropy;4;1.8;bottom+0;-1", "oreAmber;4;4.7;-32;-1",
        // Applied Energistics 2, Draconic Evolution
        "oreCertusQuartz;4;10.6;bottom+0;-1", "oreChargedCertusQuartz;4;1.2;bottom+0;-1",
        "oreDraconium;4;3;bottom+0;bottom+16",
        // metals (IC2, Thermal Foundation, Immersive Engineering, Galacticraft, Mekanism, Big Reactors, Industrial
        // Upgrade, ElectriCraft, ReactorCraft, Forestry, Tinkers')
        "oreTin;8;27;bottom+0;-1", "oreAluminum;8;21;bottom+0;-1", "oreLead;8;12;bottom+0;-1",
        "oreNickel;8;10;bottom+0;-1", "oreSilver;8;6;bottom+0;-1", "oreUranium;8;5;bottom+0;-1",
        "orePlatinum;8;3;bottom+0;-1", "oreThorium;8;2.5;bottom+0;-1", "oreTungsten;8;3;bottom+0;-1",
        "oreTitanium;8;3;bottom+0;-1", "oreZinc;8;3;bottom+0;-1", "oreMagnesium;8;3;bottom+0;-1",
        "oreOsmium;8;11;bottom+0;-1" })
    public static String[] moddedOres;
}
