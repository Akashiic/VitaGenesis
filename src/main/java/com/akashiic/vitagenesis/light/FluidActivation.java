package com.akashiic.vitagenesis.light;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Starts the fluids of a deep chunk that 1.18 would have given a fluid tick: still water and lava sources with open air
 * beside or below them (springs, aquifer and pool edges). They get the neighbour notification that makes a still fluid
 * flow in vanilla ({@code BlockStaticLiquid.onNeighborBlockChange}), from the light stage, when the chunks around are
 * loaded and the resulting block and light updates are not skipped.
 */
final class FluidActivation {

    private FluidActivation() {}

    static void run(WorldServer world, Chunk chunk, int minY) {
        int bx = chunk.xPosition << 4;
        int bz = chunk.zPosition << 4;
        for (int sy = minY >> 4; sy < 0; sy++) {
            ExtendedBlockStorage ebs = BellowAPI.getDeepSection(chunk, sy);
            if (ebs == null || ebs.isEmpty()) continue;
            for (int ly = 0; ly < 16; ly++) {
                int y = (sy << 4) + ly;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        Block b = ebs.getBlockByExtId(lx, ly, lz);
                        if (b != Blocks.water && b != Blocks.lava || ebs.getExtBlockMetadata(lx, ly, lz) != 0) continue;
                        int x = bx + lx;
                        int z = bz + lz;
                        if (open(world, x, y - 1, z, minY) || open(world, x - 1, y, z, minY)
                            || open(world, x + 1, y, z, minY)
                            || open(world, x, y, z - 1, minY)
                            || open(world, x, y, z + 1, minY)) {
                            b.onNeighborBlockChange(world, x, y, z, Blocks.air);
                        }
                    }
                }
            }
        }
    }

    private static boolean open(WorldServer world, int x, int y, int z, int minY) {
        return y >= minY && y < 0
            && world.getBlock(x, y, z)
                .getMaterial() == Material.air;
    }
}
