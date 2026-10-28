package com.akashiic.vitagenesis.feature;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.vitagenesis.CaveBlocks;

/**
 * What a cave feature sees: the 3x3 region of Below Core's decoration stage. Positions outside the region read as
 * bedrock (solid, never replaced), so a feature behaves at the region edge as it would against unbreakable rock.
 * Writes stop at the ceiling of the caves, so features never cut the seal below Y 0.
 */
final class Level {

    final IDeepRegion region;
    final WorldServer world;
    final int minY;
    final int ceiling;

    Level(IDeepRegion region, int ceiling) {
        this.region = region;
        this.world = region.world();
        this.minY = region.minY();
        this.ceiling = ceiling;
    }

    Block block(int x, int y, int z) {
        Block b = this.region.getBlock(x, y, z);
        return b == null ? Blocks.bedrock : b;
    }

    int meta(int x, int y, int z) {
        return this.region.getMeta(x, y, z);
    }

    boolean inside(int x, int y, int z) {
        return this.region.getBlock(x, y, z) != null;
    }

    boolean air(int x, int y, int z) {
        return this.block(x, y, z)
            .getMaterial() == Material.air;
    }

    boolean water(int x, int y, int z) {
        return this.block(x, y, z)
            .getMaterial() == Material.water;
    }

    boolean lava(int x, int y, int z) {
        return this.block(x, y, z)
            .getMaterial() == Material.lava;
    }

    boolean airOrWater(int x, int y, int z) {
        Material m = this.block(x, y, z)
            .getMaterial();
        return m == Material.air || m == Material.water;
    }

    boolean airOrFluid(int x, int y, int z) {
        Material m = this.block(x, y, z)
            .getMaterial();
        return m == Material.air || m.isLiquid();
    }

    /** 1.18 {@code Material.isSolid}. */
    boolean solid(int x, int y, int z) {
        return this.block(x, y, z)
            .getMaterial()
            .isSolid();
    }

    boolean baseStone(int x, int y, int z) {
        return CaveBlocks.isBaseStone(this.block(x, y, z));
    }

    /** 1.18 {@code isFaceSturdy}: the face of the block at the position that points to {@code side}. */
    boolean sturdy(int x, int y, int z, ForgeDirection side) {
        Block b = this.region.getBlock(x, y, z);
        return b != null && b.getMaterial() != Material.air && b.isSideSolid(this.world, x, y, z, side);
    }

    boolean writable(int x, int y, int z) {
        return y <= this.ceiling && y > this.minY && this.inside(x, y, z);
    }

    /** Replaces the block unless it is bedrock, has a tile entity, or is out of reach. */
    boolean set(int x, int y, int z, Block block, int meta) {
        if (!this.writable(x, y, z)) return false;
        Block old = this.region.getBlock(x, y, z);
        if (old == Blocks.bedrock || old.hasTileEntity(this.region.getMeta(x, y, z))) return false;
        return this.region.setBlock(x, y, z, block, meta);
    }

    TileEntity tile(int x, int y, int z) {
        return this.region.getTileEntity(x, y, z);
    }

    /**
     * Sets one integer of a tile entity through its NBT, so generation never calls code that sends updates or touches
     * the world (the tile entities of Et Futurum mark their block for update when their setters run).
     */
    static void setTileInt(TileEntity te, String key, int value) {
        if (te == null) return;
        NBTTagCompound tag = new NBTTagCompound();
        te.writeToNBT(tag);
        tag.setInteger(key, value);
        te.readFromNBT(tag);
    }
}
