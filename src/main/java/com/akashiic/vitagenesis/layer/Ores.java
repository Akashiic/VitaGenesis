package com.akashiic.vitagenesis.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.oredict.OreDictionary;

import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.vitagenesis.VitaGenesis;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Ores of the deep layer (1.18 {@code UNDERGROUND_ORES}): the 1.18 overworld list below Y 0 and the configured ores of
 * other mods. Each placed feature is {@code count (or rarity) -> in square -> height -> [biome] -> vein}; veins go
 * through Below Core's {@link IDeepRegion#placeOre} (the 1.18 {@code OreFeature}: stone becomes the ore, deepslate and
 * tuff its Et Futurum deepslate variant) and {@link IDeepRegion#placeBlob}, which clip to {@code minY..-1} and the 3x3
 * region. Like 1.18, origins above 0 are drawn and their veins clipped; veins that cannot reach below 0 are skipped.
 */
public final class Ores {

    /** Random salts: 1.18 list from 100, modded list from 200 (CaveFeatures uses 0..99). */
    static final int SALT_VANILLA = 100;
    static final int SALT_MODDED = 200;
    /** 1.18 {@code top()} of the overworld: sets the span of uniform heights, moves no block. */
    static final int TOP = 319;

    /** {@code VerticalAnchor}: absolute, or above the floor. */
    static final class Anchor {

        final boolean aboveBottom;
        final int value;

        Anchor(boolean aboveBottom, int value) {
            this.aboveBottom = aboveBottom;
            this.value = value;
        }

        int resolve(int minY) {
            return this.aboveBottom ? minY + this.value : this.value;
        }

        boolean isBottom() {
            return this.aboveBottom && this.value == 0;
        }

        static Anchor parse(String s) {
            String t = s.trim()
                .toLowerCase();
            if (t.startsWith("bottom")) {
                String rest = t.substring(6)
                    .trim();
                return new Anchor(true, rest.isEmpty() ? 0 : Integer.parseInt(rest.replace("+", "")));
            }
            return new Anchor(false, Integer.parseInt(t));
        }
    }

    static Anchor abs(int y) {
        return new Anchor(false, y);
    }

    static Anchor bottom(int offset) {
        return new Anchor(true, offset);
    }

    /** One placed ore feature. */
    static final class Feature {

        final String name;
        final boolean blob;
        final Block block;
        final int meta;
        final int size;
        final float discardOnAir;
        final Anchor min;
        final Anchor max;
        /** {@code trapezoid} with plateau 0 instead of {@code uniform}. */
        final boolean triangle;
        /** Count per chunk; a fraction adds one Bernoulli draw. Ignored with a rarity. */
        final double countMin;
        final double countMax;
        /** {@code RarityFilter.onAverageOnceEvery}; 0 when unused. */
        final int rarity;
        final boolean mountainsOnly;

        Feature(String name, boolean blob, Block block, int meta, int size, float discardOnAir, Anchor min, Anchor max,
            boolean triangle, double countMin, double countMax, int rarity, boolean mountainsOnly) {
            this.name = name;
            this.blob = blob;
            this.block = block;
            this.meta = meta;
            this.size = size;
            this.discardOnAir = discardOnAir;
            this.min = min;
            this.max = max;
            this.triangle = triangle;
            this.countMin = countMin;
            this.countMax = countMax;
            this.rarity = rarity;
            this.mountainsOnly = mountainsOnly;
        }

        int height(Random r, int minY) {
            int lo = this.min.resolve(minY);
            int hi = this.max.resolve(minY);
            if (lo > hi) return lo; // Minecraft logs "Empty height range" and returns the minimum
            if (!this.triangle) return between(r, lo, hi);
            int k = hi - lo;
            int l = k / 2;
            return lo + between(r, 0, k - l) + between(r, 0, l);
        }

        /**
         * Placement attempts for one chunk, scaled for the floor (bottom-anchored uniform ranges) and the multiplier.
         */
        int attempts(Random r, int minY) {
            double n;
            if (this.rarity > 0) n = r.nextFloat() < 1.0F / this.rarity ? 1 : 0;
            else if (this.countMax > this.countMin) n = between(r, (int) this.countMin, (int) this.countMax);
            else n = this.countMin;
            double scale = LayerConfig.oreCountMultiplier;
            if (LayerConfig.scaleBottomAnchoredCounts && minY != -64 && !this.triangle && this.min.isBottom()) {
                int top = this.max.resolve(minY);
                scale *= (double) (top - minY + 1) / (top + 64 + 1);
            }
            double expected = n * scale;
            int whole = (int) Math.floor(expected);
            double frac = expected - whole;
            return frac > 0.0D && r.nextDouble() < frac ? whole + 1 : whole;
        }
    }

    static int between(Random r, int lo, int hi) {
        return r.nextInt(hi - lo + 1) + lo;
    }

    /** {@code OreFeature} reach above and below its origin (Below Core clips the rest). */
    static int verticalReach(int size) {
        return 2 + (int) Math.ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
    }

    private static List<Feature> vanilla;
    private static List<Feature> modded;

    private Ores() {}

    private static Block block(String modid, String name) {
        return GameRegistry.findBlock(modid, name);
    }

    private static void ore(List<Feature> f, String name, Block block, int size, float discard, int count, Anchor min,
        Anchor max, boolean triangle) {
        if (block != null)
            f.add(new Feature(name, false, block, 0, size, discard, min, max, triangle, count, count, 0, false));
    }

    /** The 1.18 overworld ores that reach below 0 (as Below Core placed them before VitaGenesis took them over). */
    private static List<Feature> vanillaList() {
        List<Feature> f = new ArrayList<>();
        Anchor top = abs(TOP);
        f.add(new Feature("ore_gravel", true, Blocks.gravel, 0, 33, 0.0F, bottom(0), top, false, 14, 14, 0, false));
        Block tuff = block("etfuturum", "tuff");
        if (tuff != null)
            f.add(new Feature("ore_tuff", true, tuff, 0, 64, 0.0F, bottom(0), abs(0), false, 2, 2, 0, false));
        ore(f, "ore_iron_middle", Blocks.iron_ore, 9, 0.0F, 10, abs(-24), abs(56), true);
        ore(f, "ore_iron_small", Blocks.iron_ore, 4, 0.0F, 10, bottom(0), abs(72), false);
        ore(f, "ore_gold", Blocks.gold_ore, 9, 0.5F, 4, abs(-64), abs(32), true);
        f.add(
            new Feature(
                "ore_gold_lower",
                false,
                Blocks.gold_ore,
                0,
                9,
                0.5F,
                abs(-64),
                abs(-48),
                false,
                0,
                1,
                0,
                false));
        ore(f, "ore_redstone", Blocks.redstone_ore, 8, 0.0F, 4, bottom(0), abs(15), false);
        ore(f, "ore_redstone_lower", Blocks.redstone_ore, 8, 0.0F, 8, bottom(-32), bottom(32), true);
        ore(f, "ore_diamond", Blocks.diamond_ore, 4, 0.5F, 7, bottom(-80), bottom(80), true);
        ore(f, "ore_diamond_medium", Blocks.diamond_ore, 8, 0.5F, 2, abs(-64), abs(-4), false);
        f.add(
            new Feature(
                "ore_diamond_large",
                false,
                Blocks.diamond_ore,
                0,
                12,
                0.7F,
                bottom(-80),
                bottom(80),
                true,
                1,
                1,
                9,
                false));
        ore(f, "ore_diamond_buried", Blocks.diamond_ore, 8, 1.0F, 4, bottom(-80), bottom(80), true);
        ore(f, "ore_lapis", Blocks.lapis_ore, 7, 0.0F, 2, abs(-32), abs(32), true);
        ore(f, "ore_lapis_buried", Blocks.lapis_ore, 7, 1.0F, 4, bottom(0), abs(64), false);
        ore(f, "ore_copper", block("etfuturum", "copper_ore"), 10, 0.0F, 16, abs(-16), abs(112), true);
        f.add(
            new Feature(
                "ore_emerald",
                false,
                Blocks.emerald_ore,
                0,
                3,
                0.0F,
                abs(-16),
                abs(480),
                true,
                100,
                100,
                0,
                true));
        return f;
    }

    /** Parses ore lines ({@link #moddedLines()}); entries that do not resolve are logged and skipped. */
    /** {@link LayerConfig#packOres}: measured in the TEOF 4.1 pack (docs/DESIGN.md). */
    static final String[] PACK_ORES = {
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
        "oreOsmium;8;11;bottom+0;-1" };

    /** The built-in list (when enabled) with the lines of {@link LayerConfig#extraOres} replacing or adding ores. */
    static String[] moddedLines() {
        java.util.LinkedHashMap<String, String> lines = new java.util.LinkedHashMap<>();
        if (LayerConfig.packOres) for (String l : PACK_ORES) lines.put(l.split(";")[0].trim(), l);
        if (LayerConfig.extraOres != null) {
            for (String l : LayerConfig.extraOres) {
                if (l != null && !l.trim()
                    .isEmpty()) lines.put(l.split(";")[0].trim(), l);
            }
        }
        List<String> out = new ArrayList<>();
        for (String l : lines.values()) {
            String[] p = l.split(";");
            if (p.length >= 3 && p[2].trim()
                .matches("0+(\\.0+)?")) continue; // count 0: removed
            out.add(l);
        }
        return out.toArray(new String[0]);
    }

    static List<Feature> moddedList(String[] lines) {
        List<Feature> f = new ArrayList<>();
        if (lines == null) return f;
        for (String line : lines) {
            if (line == null || line.trim()
                .isEmpty()) continue;
            String[] p = line.split(";");
            try {
                if (p.length < 5) throw new IllegalArgumentException("expected ore;size;count;minY;maxY");
                Block block;
                int meta = 0;
                String ore = p[0].trim();
                if (ore.contains(":")) {
                    String[] id = ore.split(":");
                    block = block(id[0], id[1]);
                    if (id.length >= 3) meta = Integer.parseInt(id[2].trim());
                } else {
                    ItemStack stack = fromOreDictionary(ore);
                    block = stack == null ? null : Block.getBlockFromItem(stack.getItem());
                    if (stack != null)
                        meta = stack.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0 : stack.getItemDamage();
                }
                if (block == null || block == Blocks.air) {
                    VitaGenesis.LOG.info("layer ores: '{}' is not in this pack, skipped", ore);
                    continue;
                }
                int size = Integer.parseInt(p[1].trim());
                double count = Double.parseDouble(p[2].trim());
                Anchor min = Anchor.parse(p[3]);
                Anchor max = Anchor.parse(p[4]);
                float discard = p.length >= 6 ? Float.parseFloat(p[5].trim()) : 0.0F;
                if (size < 0 || size > 64 || count < 0 || !(discard >= 0.0F && discard <= 1.0F)) {
                    throw new IllegalArgumentException("size 0..64, count >= 0, discardOnAir 0..1");
                }
                if (block.getMaterial() == Material.air || block.hasTileEntity(meta) || meta < 0 || meta > 15) {
                    throw new IllegalArgumentException("not a solid block without a tile entity");
                }
                f.add(new Feature(ore, false, block, meta, size, discard, min, max, false, count, count, 0, false));
            } catch (RuntimeException e) {
                VitaGenesis.LOG.error("layer ores: cannot use '{}' ({}), skipped", line, e.getMessage());
            }
        }
        return f;
    }

    /** The ore as its mod registers it, not an Et Futurum deepslate variant registered under the same name. */
    private static ItemStack fromOreDictionary(String name) {
        // prefer an ore Et Futurum has a deepslate variant of: that variant is what goes into the deepslate
        java.util.Set<Long> mapped = new java.util.HashSet<>();
        for (EfrOre o : etFuturumDeepslateMap()) mapped.add((long) Block.getIdFromBlock(o.from) << 4 | o.fromMeta & 15);
        for (ItemStack s : OreDictionary.getOres(name)) {
            Block b = s == null ? null : Block.getBlockFromItem(s.getItem());
            int meta = s == null || s.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0 : s.getItemDamage();
            if (b != null && b != Blocks.air && mapped.contains((long) Block.getIdFromBlock(b) << 4 | meta & 15)) {
                return s;
            }
        }
        ItemStack any = null;
        for (ItemStack s : OreDictionary.getOres(name)) {
            if (s == null || Block.getBlockFromItem(s.getItem()) == Blocks.air) continue;
            String owner = GameRegistry.findUniqueIdentifierFor(Block.getBlockFromItem(s.getItem())) == null ? ""
                : GameRegistry.findUniqueIdentifierFor(Block.getBlockFromItem(s.getItem())).modId;
            if (!"etfuturum".equals(owner)) return s;
            if (any == null) any = s;
        }
        return any;
    }

    /**
     * Every ore Et Futurum knows a deepslate variant of ({@code DeepslateOreRegistry.getOreMap()}, read by reflection:
     * Et Futurum is optional), as {@code "original -> variant [ore dictionary names]"}; empty without Et Futurum.
     */
    public static List<String> etFuturumDeepslateOres() {
        List<String> out = new ArrayList<>();
        for (EfrOre o : etFuturumDeepslateMap()) {
            out.add(
                GameRegistry.findUniqueIdentifierFor(o.from) + ":"
                    + o.fromMeta
                    + " -> "
                    + GameRegistry.findUniqueIdentifierFor(o.to)
                    + ":"
                    + o.toMeta
                    + " "
                    + o.names);
        }
        Collections.sort(out);
        return out;
    }

    /** One entry of Et Futurum's {@code DeepslateOreRegistry}: an ore and its deepslate variant. */
    public static final class EfrOre {

        public final Block from;
        public final int fromMeta;
        public final Block to;
        public final int toMeta;
        public final List<String> names;

        EfrOre(Block from, int fromMeta, Block to, int toMeta, List<String> names) {
            this.from = from;
            this.fromMeta = fromMeta;
            this.to = to;
            this.toMeta = toMeta;
            this.names = names;
        }
    }

    /** The entries of Et Futurum's {@code DeepslateOreRegistry}; empty without Et Futurum. */
    public static List<EfrOre> etFuturumDeepslateMap() {
        List<EfrOre> out = new ArrayList<>();
        try {
            Class<?> registry = Class.forName("ganymedes01.etfuturum.api.DeepslateOreRegistry");
            Class<?> mapping = Class.forName("ganymedes01.etfuturum.api.mappings.RegistryMapping");
            java.lang.reflect.Method object = mapping.getMethod("getObject");
            java.lang.reflect.Method meta = mapping.getMethod("getMeta");
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) registry.getMethod("getOreMap")
                .invoke(null);
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                Block from = (Block) object.invoke(e.getKey());
                int fromMeta = (Integer) meta.invoke(e.getKey());
                Block to = (Block) object.invoke(e.getValue());
                int toMeta = (Integer) meta.invoke(e.getValue());
                List<String> names = new ArrayList<>();
                ItemStack stack = new ItemStack(from, 1, fromMeta);
                if (stack.getItem() != null) {
                    for (int id : OreDictionary.getOreIDs(stack)) names.add(OreDictionary.getOreName(id));
                }
                out.add(new EfrOre(from, fromMeta, to, toMeta, names));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // no Et Futurum, or another version of its API
        }
        return out;
    }

    /** Resolves both lists (blocks of every mod are registered by then); called once the server starts. */
    public static synchronized void resolve() {
        vanilla = Collections.unmodifiableList(vanillaList());
        modded = Collections.unmodifiableList(moddedList(moddedLines()));
        List<String> names = new ArrayList<>();
        for (Feature f : modded) {
            names.add(f.name + "=" + GameRegistry.findUniqueIdentifierFor(f.block) + ":" + f.meta);
        }
        List<String> efr = etFuturumDeepslateOres();
        VitaGenesis.LOG.info("Et Futurum deepslate ores in this pack ({}):", efr.size());
        for (String line : efr) VitaGenesis.LOG.info("  {}", line);
        VitaGenesis.LOG.info(
            "Deep layer: fill {}:{}, {} ores of 1.18, modded ores {}",
            DeepLayer.fill,
            DeepLayer.fillMeta,
            vanilla.size(),
            names);
    }

    /** Places the ores of the centre chunk of {@code region}; {@code random} gives the feature's own random. */
    public static void place(IDeepRegion region, IntFunction<Random> random) {
        if (vanilla == null) resolve();
        int minY = region.minY();
        if (LayerConfig.ores) placeAll(region, vanilla, SALT_VANILLA, random, minY);
        placeAll(region, modded, SALT_MODDED, random, minY);
    }

    private static void placeAll(IDeepRegion region, List<Feature> features, int salt, IntFunction<Random> random,
        int minY) {
        int bx = region.centerChunkX() << 4;
        int bz = region.centerChunkZ() << 4;
        for (int i = 0; i < features.size(); i++) {
            Feature f = features.get(i);
            Random r = random.apply(salt + i);
            int attempts = f.attempts(r, minY);
            int reach = verticalReach(f.size);
            for (int a = 0; a < attempts; a++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = f.height(r, minY);
                if (f.mountainsOnly && !mountain(region, x, z)) continue;
                if (y - reach > -1 || y + reach < minY) continue;
                if (f.blob) region.placeBlob(r, x, y, z, f.size, f.discardOnAir, f.block, f.meta);
                else region.placeOre(r, x, y, z, f.size, f.discardOnAir, f.block, f.meta);
            }
        }
    }

    private static boolean mountain(IDeepRegion region, int x, int z) {
        Chunk c = region.chunkAt(x, z);
        BiomeGenBase biome = c == null ? null
            : c.getBiomeGenForWorldCoords(
                x & 15,
                z & 15,
                region.world()
                    .getWorldChunkManager());
        return biome != null && BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MOUNTAIN);
    }

    /**
     * The configured ores of other mods that this pack has, each with the block it becomes in deepslate (Et Futurum's
     * variant, or the ore itself), as {@code {name, block registry name, meta}}; for the self test.
     */
    public static List<Object[]> moddedTargets() {
        if (modded == null) resolve();
        List<Object[]> out = new ArrayList<>();
        List<EfrOre> map = etFuturumDeepslateMap();
        for (Feature f : modded) {
            Block to = f.block;
            int toMeta = f.meta;
            for (EfrOre o : map) {
                if (o.from == f.block && o.fromMeta == f.meta) {
                    to = o.to;
                    toMeta = o.toMeta;
                }
            }
            out.add(new Object[] { f.name, Block.blockRegistry.getNameForObject(to), toMeta });
        }
        return out;
    }

    /** For {@code version()}: what shapes the ores of new chunks. */
    public static String describe() {
        StringBuilder s = new StringBuilder();
        s.append(" fill=")
            .append(LayerConfig.fillBlock)
            .append(" ores=")
            .append(LayerConfig.ores)
            .append(" oreCount=")
            .append(LayerConfig.oreCountMultiplier)
            .append(" scaleBottom=")
            .append(LayerConfig.scaleBottomAnchoredCounts);
        for (String m : moddedLines()) s.append(" +")
            .append(m.trim());
        return s.toString();
    }
}
