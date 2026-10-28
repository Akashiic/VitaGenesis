package com.akashiic.vitagenesis.terrain;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.vitagenesis.CaveBlocks;

/**
 * {@link DeepCaves.Chunk} over the deep sections of a chunk that Below Core has just filled. Writes go straight into
 * the sections, as the terrain stage contract asks: no light, no neighbour updates, no tile entities.
 */
public final class SectionTerrain implements DeepCaves.Chunk {

    private final ExtendedBlockStorage[] sections;
    private final int minY;

    public SectionTerrain(Chunk chunk, int minY) {
        this.minY = minY;
        this.sections = new ExtendedBlockStorage[-minY >> 4];
        for (int sy = minY >> 4; sy < 0; sy++) this.sections[sy - (minY >> 4)] = BellowAPI.getDeepSection(chunk, sy);
    }

    private ExtendedBlockStorage section(int y) {
        return this.sections[(y >> 4) - (this.minY >> 4)];
    }

    @Override
    public int kind(int lx, int y, int lz) {
        ExtendedBlockStorage ebs = this.section(y);
        if (ebs == null) return OTHER;
        Block b = ebs.getBlockByExtId(lx, y & 15, lz);
        Material m = b.getMaterial();
        if (m == Material.air || m.isLiquid()) return OTHER;
        if (b == Blocks.bedrock) return y == this.minY ? OTHER : BEDROCK;
        if (b == CaveBlocks.ironOre || b == CaveBlocks.tuff && CaveBlocks.tuff != null
            || b == CaveBlocks.rawOreBlock && CaveBlocks.rawOreBlock != null) {
            return VEIN;
        }
        return b.hasTileEntity(ebs.getExtBlockMetadata(lx, y & 15, lz)) ? OTHER : FILL;
    }

    @Override
    public void setSubstance(int lx, int y, int lz, byte substance) {
        Block block = substance == Aquifer.WATER ? Blocks.water : substance == Aquifer.LAVA ? Blocks.lava : Blocks.air;
        this.set(lx, y, lz, block, 0);
    }

    @Override
    public void setVein(int lx, int y, int lz, byte vein) {
        switch (vein) {
            case OreVeins.ORE:
                this.set(lx, y, lz, CaveBlocks.ironOre, 0);
                break;
            case OreVeins.RAW_BLOCK:
                if (CaveBlocks.rawOreBlock != null)
                    this.set(lx, y, lz, CaveBlocks.rawOreBlock, CaveBlocks.RAW_IRON_META);
                else this.set(lx, y, lz, CaveBlocks.ironOre, 0);
                break;
            case OreVeins.FILLER:
                if (CaveBlocks.tuff != null) this.set(lx, y, lz, CaveBlocks.tuff, 0);
                break;
            default:
                break;
        }
    }

    private void set(int lx, int y, int lz, Block block, int meta) {
        ExtendedBlockStorage ebs = this.section(y);
        ebs.func_150818_a(lx, y & 15, lz, block);
        ebs.setExtBlockMetadata(lx, y & 15, lz, meta);
    }
}
