package com.akashiic.vitagenesis.feature;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.DungeonHooks;

/**
 * 1.18 {@code MonsterRoomFeature} as {@code monster_room_deep} (4 per chunk at {@code uniform(aboveBottom(6), -1)}):
 * the dungeon of 1.7.10, now also in the deep layer. The spawner and the loot come from Forge's {@code DungeonHooks}
 * and {@code ChestGenHooks}, so the dungeon mobs and loot configured by the pack apply here too.
 */
final class MonsterRoom {

    private MonsterRoom() {}

    private static boolean solid(Level lv, int x, int y, int z) {
        return lv.block(x, y, z)
            .getMaterial()
            .isSolid();
    }

    static void place(Level lv, Random r, int ox, int oy, int oz) {
        int rx = r.nextInt(2) + 2;
        int x0 = -rx - 1;
        int x1 = rx + 1;
        int rz = r.nextInt(2) + 2;
        int z0 = -rz - 1;
        int z1 = rz + 1;
        if (oy + 4 > lv.ceiling || oy - 1 <= lv.minY) return;
        int openings = 0;
        for (int dx = x0; dx <= x1; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = z0; dz <= z1; dz++) {
                    int x = ox + dx;
                    int y = oy + dy;
                    int z = oz + dz;
                    if (!lv.inside(x, y, z)) return;
                    boolean s = solid(lv, x, y, z);
                    if (dy == -1 && !s) return;
                    if (dy == 4 && !s) return;
                    if ((dx == x0 || dx == x1 || dz == z0 || dz == z1) && dy == 0
                        && lv.air(x, y, z)
                        && lv.air(x, y + 1, z)) {
                        openings++;
                    }
                }
            }
        }
        if (openings < 1 || openings > 5) return;

        for (int dx = x0; dx <= x1; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = z0; dz <= z1; dz++) {
                    int x = ox + dx;
                    int y = oy + dy;
                    int z = oz + dz;
                    boolean wall = dx == x0 || dy == -1 || dz == z0 || dx == x1 || dy == 4 || dz == z1;
                    if (!wall) {
                        if (lv.block(x, y, z) != Blocks.chest && lv.block(x, y, z) != Blocks.mob_spawner)
                            lv.set(x, y, z, Blocks.air, 0);
                    } else if (y > lv.minY && !solid(lv, x, y - 1, z)) {
                        lv.set(x, y, z, Blocks.air, 0);
                    } else if (solid(lv, x, y, z) && lv.block(x, y, z) != Blocks.chest) {
                        if (dy == -1 && r.nextInt(4) != 0) lv.set(x, y, z, Blocks.mossy_cobblestone, 0);
                        else lv.set(x, y, z, Blocks.cobblestone, 0);
                    }
                }
            }
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            for (int tries = 0; tries < 3; tries++) {
                int x = ox + r.nextInt(rx * 2 + 1) - rx;
                int z = oz + r.nextInt(rz * 2 + 1) - rz;
                if (!lv.air(x, oy, z)) continue;
                int walls = 0;
                int facing = 0;
                // chest meta = the side it faces, away from its single wall: 2 north, 3 south, 4 west, 5 east
                if (solid(lv, x, oy, z - 1)) {
                    walls++;
                    facing = 3;
                }
                if (solid(lv, x, oy, z + 1)) {
                    walls++;
                    facing = 2;
                }
                if (solid(lv, x - 1, oy, z)) {
                    walls++;
                    facing = 5;
                }
                if (solid(lv, x + 1, oy, z)) {
                    walls++;
                    facing = 4;
                }
                if (walls != 1) continue;
                if (lv.set(x, oy, z, Blocks.chest, facing)) {
                    TileEntity te = lv.tile(x, oy, z);
                    if (te instanceof IInventory) {
                        ChestGenHooks info = ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST);
                        WeightedRandomChestContent
                            .generateChestContents(r, info.getItems(r), (IInventory) te, info.getCount(r));
                    }
                }
                break;
            }
        }

        if (lv.set(ox, oy, oz, Blocks.mob_spawner, 0)) {
            TileEntity te = lv.tile(ox, oy, oz);
            if (te instanceof TileEntityMobSpawner) {
                ((TileEntityMobSpawner) te).func_145881_a()
                    .setEntityName(DungeonHooks.getRandomDungeonMob(r));
            }
        }
    }
}
