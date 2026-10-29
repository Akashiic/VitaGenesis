package com.akashiic.vitagenesis.layer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.vitagenesis.CaveBlocks;
import com.akashiic.vitagenesis.VitaGenesis;
import com.akashiic.vitagenesis.noise.XoroshiroRandom;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The solid deep layer of a chunk: the fill block from the floor up to Y -1 over the 1.18 bedrock floor
 * ({@code verticalGradient("bedrock_floor", aboveBottom(0), aboveBottom(5))}). Chunk-local, a pure function of the
 * seed and the position.
 * <p>
 * Only free positions are written: air, the fill and the base stone (Below Core's provisional stone layer, made while
 * no generator was installed, included), and bedrock in the floor band. Anything else was put there by players or mods
 * (a chunk saved before
 * the deep layer existed may already hold blocks below 0) and is kept.
 */
public final class DeepLayer {

    /** 1.18 bedrock floor: bedrock at the floor, then 80 %, 60 %, 40 %, 20 % up to floor + 4. */
    static final int BEDROCK_GRADIENT = 5;

    public static Block fill = Blocks.stone;
    public static int fillMeta;

    private DeepLayer() {}

    /** After Et Futurum registered its blocks (postInit). */
    public static void resolve() {
        Block deepslate = GameRegistry.findBlock("etfuturum", "deepslate");
        Block fallback = deepslate != null ? deepslate : Blocks.stone;
        String spec = LayerConfig.fillBlock == null ? "auto" : LayerConfig.fillBlock.trim();
        fill = fallback;
        fillMeta = 0;
        if ("auto".equalsIgnoreCase(spec)) return;
        String[] parts = spec.split(":");
        Block b = parts.length >= 2 ? GameRegistry.findBlock(parts[0].trim(), parts[1].trim()) : null;
        int meta = 0;
        if (parts.length >= 3) {
            try {
                meta = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                meta = -1;
            }
        }
        // the whole layer is made of it: no air, no tile entity, nothing that falls, metadata that fits 4 bits
        if (b == null || b.getMaterial() == Material.air
            || b.hasTileEntity(meta)
            || b instanceof BlockFalling
            || meta < 0
            || meta > 15) {
            VitaGenesis.LOG.error("layer.fillBlock '{}' cannot fill the deep layer; using {}", spec, fallback);
            return;
        }
        fill = b;
        fillMeta = meta;
    }

    /** Probability of bedrock at {@code y}: 1 at the floor, 0 from floor + 5 up (1.18 {@code Mth.map}). */
    static float bedrockChance(int y, int minY) {
        if (y <= minY) return 1.0F;
        if (y >= minY + BEDROCK_GRADIENT) return 0.0F;
        return 1.0F - (float) (y - minY) / BEDROCK_GRADIENT;
    }

    /** {@code RandomState.getOrCreateRandomFactory("minecraft:bedrock_floor")}. */
    public static XoroshiroRandom.Positional bedrockRandom(long seed) {
        return new XoroshiroRandom(seed).forkPositional()
            .fromHashOf("minecraft:bedrock_floor")
            .forkPositional();
    }

    static boolean isBedrock(XoroshiroRandom.Positional random, int x, int y, int z, int minY) {
        float p = bedrockChance(y, minY);
        return p >= 1.0F || p > 0.0F && random.at(x, y, z)
            .nextFloat() < p;
    }

    /** Whether the layer may be (re)written at a position that holds {@code b}. */
    static boolean free(Block b, int y, int minY) {
        return b.getMaterial() == Material.air || b == fill
            || CaveBlocks.isBaseStone(b)
            || b == Blocks.bedrock && y < minY + BEDROCK_GRADIENT;
    }

    public static void generate(Chunk chunk, int minY, XoroshiroRandom.Positional bedrock) {
        int bx = chunk.xPosition << 4;
        int bz = chunk.zPosition << 4;
        for (int sy = minY >> 4; sy < 0; sy++) {
            ExtendedBlockStorage ebs = BellowAPI.getDeepSection(chunk, sy);
            if (ebs == null) continue; // Below Core creates every section of the layer before this stage
            for (int ly = 0; ly < 16; ly++) {
                int y = (sy << 4) + ly;
                boolean floorBand = y < minY + BEDROCK_GRADIENT;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        Block b = ebs.getBlockByExtId(lx, ly, lz);
                        if (!free(b, y, minY)) continue;
                        if (floorBand && isBedrock(bedrock, bx + lx, y, bz + lz, minY)) {
                            if (b != Blocks.bedrock) ebs.func_150818_a(lx, ly, lz, Blocks.bedrock);
                            ebs.setExtBlockMetadata(lx, ly, lz, 0);
                        } else if (b != fill || ebs.getExtBlockMetadata(lx, ly, lz) != fillMeta) {
                            ebs.func_150818_a(lx, ly, lz, fill);
                            ebs.setExtBlockMetadata(lx, ly, lz, fillMeta);
                        }
                    }
                }
            }
        }
        chunk.setChunkModified();
    }
}
