package com.akashiic.vitagenesis;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.akashiic.vitagenesis.layer.DeepLayer;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Blocks of the cave generator, resolved by registry name after every mod registered its blocks. Et Futurum Requiem
 * provides the 1.17+ blocks; a feature whose blocks are missing (an EFR option turned off) is disabled and logged.
 */
public final class CaveBlocks {

    /** Iron vein ore: Et Futurum's deepslate iron ore, or plain iron ore. */
    public static Block ironOre = Blocks.iron_ore;
    /** Raw iron block ({@code etfuturum:raw_ore_block:1}); null: the vein places ore instead. */
    public static Block rawOreBlock;
    public static final int RAW_IRON_META = 1;
    public static Block tuff;
    public static Block deepslate;
    /** Granite, diorite and andesite. */
    public static Block efrStone;

    public static Block calcite;
    public static Block smoothBasalt;
    public static Block amethystBlock;
    public static Block buddingAmethyst;
    /** Small (meta < 6) and medium (meta >= 6) amethyst buds; the low 3 bits of the meta are the facing. */
    public static Block amethystCluster1;
    /** Large bud (meta < 6) and amethyst cluster (meta >= 6). */
    public static Block amethystCluster2;

    public static Block dripstoneBlock;
    /** Metas 0..4: pointing down (tip, frustum, middle, base, tip merge); 5..9: pointing up. */
    public static Block pointedDripstone;

    public static Block mossBlock;
    public static Block mossCarpet;
    /** Meta 0 azalea, 1 flowering azalea. */
    public static Block azalea;
    /** Tip of a cave vine (has a tile entity); meta 1 bears glow berries. */
    public static Block caveVine;
    public static Block caveVinePlant;
    public static Block glowLichen;
    public static Block magma;

    public static boolean geodes;
    public static boolean dripstone;
    public static boolean lush;

    private CaveBlocks() {}

    public static void resolve() {
        Block deepIron = efr("deepslate_iron_ore");
        ironOre = deepIron != null ? deepIron : Blocks.iron_ore;
        rawOreBlock = efr("raw_ore_block");
        tuff = efr("tuff");
        deepslate = efr("deepslate");
        efrStone = efr("stone");
        calcite = efr("calcite");
        smoothBasalt = efr("smooth_basalt");
        amethystBlock = efr("amethyst_block");
        buddingAmethyst = efr("budding_amethyst");
        amethystCluster1 = efr("amethyst_cluster_1");
        amethystCluster2 = efr("amethyst_cluster_2");
        dripstoneBlock = efr("dripstone_block");
        pointedDripstone = efr("pointed_dripstone");
        mossBlock = efr("moss_block");
        mossCarpet = efr("moss_carpet");
        azalea = efr("azalea");
        caveVine = efr("cave_vine");
        caveVinePlant = efr("cave_vine_plant");
        glowLichen = efr("glow_lichen");
        magma = efr("magma");

        geodes = all(amethystBlock, buddingAmethyst, calcite, smoothBasalt);
        dripstone = all(dripstoneBlock, pointedDripstone);
        lush = all(mossBlock);

        List<String> missing = new ArrayList<>();
        if (!geodes) missing.add("geodes (amethyst, calcite, smooth basalt)");
        if (!dripstone) missing.add("dripstone caves (EFR experiments: enableDripstone)");
        if (!lush) missing.add("lush caves (EFR experiments: enableMossAzalea)");
        if (glowLichen == null) missing.add("glow lichen");
        if (magma == null) missing.add("underwater magma");
        VitaGenesis.LOG.info(
            "Cave blocks: iron vein ore={}, raw iron={}, tuff={}, cave vines={}, azalea={}",
            Block.blockRegistry.getNameForObject(ironOre),
            rawOreBlock != null,
            tuff != null,
            caveVine != null && caveVinePlant != null,
            azalea != null);
        if (!missing.isEmpty()) VitaGenesis.LOG.warn("Disabled for lack of Et Futurum blocks: {}", missing);
    }

    private static Block efr(String name) {
        return GameRegistry.findBlock("etfuturum", name);
    }

    private static boolean all(Block... blocks) {
        for (Block b : blocks) {
            if (b == null) return false;
        }
        return true;
    }

    /**
     * Overworld base stone (1.18 {@code #base_stone_overworld}): stone and Et Futurum's stones, deepslate and tuff, and
     * the block the deep layer is filled with.
     */
    public static boolean isBaseStone(Block b) {
        return b == Blocks.stone || b != null && (b == deepslate || b == tuff || b == efrStone || b == DeepLayer.fill);
    }
}
