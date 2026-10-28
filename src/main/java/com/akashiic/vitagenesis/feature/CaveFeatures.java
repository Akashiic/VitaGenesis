package com.akashiic.vitagenesis.feature;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.vitagenesis.CaveBlocks;
import com.akashiic.vitagenesis.FeatureConfig;
import com.akashiic.vitagenesis.noise.Noises;
import com.akashiic.vitagenesis.noise.NormalNoise;
import com.akashiic.vitagenesis.terrain.DeepCaves;

/**
 * Decoration stage of one chunk: the 1.18 cave features in the order of their generation steps (local modifications,
 * underground structures, ores, underground decoration, fluid springs, vegetal decoration), each with its 1.18
 * placement (count, in square, height range, environment scan, random offset, biome filter). Origins are in the centre
 * chunk; Below Core lets the writes reach the 8 neighbours. Heights above the caves' ceiling are drawn as in 1.18 and
 * dropped (that part of the world belongs to the 1.7.10 generators), and counts over {@code uniform(bottom, top)} are
 * scaled for floors other than -64 so the density per block is the 1.18 one.
 * <p>
 * Each feature draws from its own random, seeded from the world seed, the chunk and the feature, so the result does not
 * depend on the order chunks are decorated in. One instance per world seed; main thread only.
 */
public final class CaveFeatures {

    private final long seed;
    private final CaveBiomes biomes;
    private final NormalNoise geodeNoise;

    public CaveFeatures(long seed) {
        this.seed = seed;
        Noises noises = new Noises(seed);
        this.biomes = new CaveBiomes(noises);
        this.geodeNoise = noises.create(Noises.GEODE);
    }

    /** Cave biome of a column from the world's biome generator (no chunk is loaded); for tools and tests. */
    public int caveBiome(net.minecraft.world.World world, int x, int z) {
        BiomeGenBase biome = world.getWorldChunkManager()
            .getBiomeGenAt(x, z);
        return this.biomes.at(
            x,
            z,
            biome == null ? 0.5F : biome.rainfall,
            FeatureConfig.lushRainfallWeight,
            FeatureConfig.lushHumidity,
            FeatureConfig.dripstoneContinentalness);
    }

    /** Biomes of the 3x3 region, computed on demand. */
    private final class BiomeMap {

        private final IDeepRegion region;
        private final int originX;
        private final int originZ;
        private final byte[] cache = new byte[48 * 48];

        BiomeMap(IDeepRegion region) {
            this.region = region;
            this.originX = (region.centerChunkX() - 1) << 4;
            this.originZ = (region.centerChunkZ() - 1) << 4;
            Arrays.fill(this.cache, (byte) -1);
        }

        int at(int x, int z) {
            int i = x - this.originX;
            int j = z - this.originZ;
            if (i < 0 || j < 0 || i >= 48 || j >= 48) return CaveBiomes.NORMAL;
            int k = j * 48 + i;
            if (this.cache[k] < 0) {
                Chunk chunk = this.region.chunkAt(x, z);
                BiomeGenBase biome = chunk == null ? null
                    : chunk.getBiomeGenForWorldCoords(
                        x & 15,
                        z & 15,
                        this.region.world()
                            .getWorldChunkManager());
                float rainfall = biome == null ? 0.5F : biome.rainfall;
                this.cache[k] = (byte) CaveFeatures.this.biomes.at(
                    x,
                    z,
                    rainfall,
                    FeatureConfig.lushRainfallWeight,
                    FeatureConfig.lushHumidity,
                    FeatureConfig.dripstoneContinentalness);
            }
            return this.cache[k];
        }
    }

    private Random random(int cx, int cz, int feature) {
        long h = mix(this.seed ^ 0x5DEECE66DL * (feature + 1));
        h = mix(h ^ cx * 0x9E3779B97F4A7C15L);
        h = mix(h ^ cz * 0xC2B2AE3D27D4EB4FL);
        return new Random(h);
    }

    private static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    public void decorate(IDeepRegion region, DeepCaves.Settings settings) {
        Level lv = new Level(region, settings.ceiling());
        BiomeMap biomes = new BiomeMap(region);
        int cx = region.centerChunkX();
        int cz = region.centerChunkZ();
        int bx = cx << 4;
        int bz = cz << 4;
        int minY = lv.minY;
        int top = Placement.MAX_TERRAIN_HEIGHT;

        // LOCAL_MODIFICATIONS: amethyst geode, large dripstone
        if (FeatureConfig.geodes && CaveBlocks.geodes) {
            Random r = this.random(cx, cz, 0);
            if (r.nextFloat() < 1.0F / 24.0F) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Placement.uniform(r, minY + 6, 30);
                if (y + Geode.TOP_REACH <= lv.ceiling) {
                    Geode.place(lv, r, x, y, z, this.geodeNoise);
                }
            }
        }
        boolean dripstone = FeatureConfig.dripstoneCaves && CaveBlocks.dripstone;
        if (dripstone) {
            Random r = this.random(cx, cz, 1);
            int count = Placement.scaledCount(r, Placement.uniform(r, 10, 48), minY, top);
            for (int i = 0; i < count; i++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Placement.uniform(r, minY, top);
                if (y <= lv.ceiling && biomes.at(x, z) == CaveBiomes.DRIPSTONE) Dripstone.large(lv, r, x, y, z);
            }
        }

        // UNDERGROUND_STRUCTURES: monster_room_deep
        if (FeatureConfig.monsterRooms) {
            Random r = this.random(cx, cz, 2);
            for (int i = 0; i < 4; i++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Placement.uniform(r, minY + 6, -1);
                MonsterRoom.place(lv, r, x, y, z);
            }
        }

        // UNDERGROUND_ORES (Below Core places the ores themselves): underwater magma
        if (FeatureConfig.underwaterMagma && CaveBlocks.magma != null) {
            Random r = this.random(cx, cz, 3);
            int count = Placement.scaledCount(r, Placement.uniform(r, 44, 52), minY, top);
            for (int i = 0; i < count; i++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Placement.uniform(r, minY, top);
                if (y <= lv.ceiling) UnderwaterMagma.place(lv, r, x, y, z);
            }
        }

        // UNDERGROUND_DECORATION: glow lichen, dripstone clusters, pointed dripstone
        if (FeatureConfig.glowLichen && CaveBlocks.glowLichen != null) {
            Random r = this.random(cx, cz, 4);
            int count = Placement.scaledCount(r, Placement.uniform(r, 104, 157), minY, top);
            for (int i = 0; i < count; i++) {
                int y = Placement.uniform(r, minY, top);
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                if (y <= lv.ceiling) GlowLichen.place(lv, r, x, y, z);
            }
        }
        if (dripstone) {
            Random r = this.random(cx, cz, 5);
            int count = Placement.scaledCount(r, Placement.uniform(r, 48, 96), minY, top);
            for (int i = 0; i < count; i++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Placement.uniform(r, minY, top);
                if (y <= lv.ceiling && biomes.at(x, z) == CaveBiomes.DRIPSTONE) Dripstone.cluster(lv, r, x, y, z);
            }
            r = this.random(cx, cz, 6);
            count = Placement.scaledCount(r, Placement.uniform(r, 192, 256), minY, top);
            for (int i = 0; i < count; i++) {
                int x0 = bx + r.nextInt(16);
                int z0 = bz + r.nextInt(16);
                int y0 = Placement.uniform(r, minY, top);
                int n = Placement.uniform(r, 1, 5);
                for (int k = 0; k < n; k++) {
                    int x = x0 + Placement.clampedNormalInt(r, 0.0F, 3.0F, -10, 10);
                    int y = y0 + Placement.clampedNormalInt(r, 0.0F, 0.6F, -2, 2);
                    int z = z0 + Placement.clampedNormalInt(r, 0.0F, 3.0F, -10, 10);
                    if (y > lv.ceiling || y <= minY || biomes.at(x, z) != CaveBiomes.DRIPSTONE) continue;
                    boolean down = r.nextInt(2) == 0;
                    int hit = Placement.scan(lv, x, y, z, down ? -1 : 1, lv::solid, lv::air, 12);
                    if (hit == Integer.MIN_VALUE) continue;
                    Dripstone.pointed(lv, r, x, hit + (down ? 1 : -1), z);
                }
            }
        }

        // FLUID_SPRINGS
        if (FeatureConfig.springs) {
            Random r = this.random(cx, cz, 7);
            int count = Placement.scaledCount(r, 25, minY, 192);
            for (int i = 0; i < count; i++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Springs.waterY(r, minY);
                if (y <= lv.ceiling) Springs.place(lv, x, y, z, Blocks.water);
            }
            for (int i = 0; i < 20; i++) {
                int x = bx + r.nextInt(16);
                int z = bz + r.nextInt(16);
                int y = Springs.lavaY(r, minY);
                if (y <= lv.ceiling) Springs.place(lv, x, y, z, Blocks.lava);
            }
        }

        // VEGETAL_DECORATION: lush caves
        if (FeatureConfig.lushCaves && CaveBlocks.lush) this.lush(lv, biomes, cx, cz, bx, bz, minY, top);
    }

    private void lush(Level lv, BiomeMap biomes, int cx, int cz, int bx, int bz, int minY, int top) {
        // lush_caves_ceiling_vegetation: scan up to a solid ceiling, then one below it
        Random r = this.random(cx, cz, 8);
        int count = Placement.scaledCount(r, 125, minY, top);
        for (int i = 0; i < count; i++) {
            int x = bx + r.nextInt(16);
            int z = bz + r.nextInt(16);
            int y = Placement.uniform(r, minY, top);
            if (y > lv.ceiling) continue;
            int hit = Placement.scan(lv, x, y, z, 1, lv::solid, lv::air, 12);
            if (hit != Integer.MIN_VALUE && biomes.at(x, z) == CaveBiomes.LUSH)
                LushCaves.ceilingVegetation(lv, r, x, hit - 1, z);
        }
        // cave_vines: scan up to a block whose bottom face is sturdy
        r = this.random(cx, cz, 9);
        count = Placement.scaledCount(r, 188, minY, top);
        for (int i = 0; i < count; i++) {
            int x = bx + r.nextInt(16);
            int z = bz + r.nextInt(16);
            int y = Placement.uniform(r, minY, top);
            if (y > lv.ceiling) continue;
            int hit = Placement.scan(lv, x, y, z, 1, (a, b, c) -> lv.sturdy(a, b, c, ForgeDirection.DOWN), lv::air, 12);
            if (hit != Integer.MIN_VALUE && biomes.at(x, z) == CaveBiomes.LUSH)
                LushCaves.caveVinesColumn(lv, r, x, hit - 1, z);
        }
        // lush_caves_clay: scan down to a solid floor, then one above it
        r = this.random(cx, cz, 10);
        count = Placement.scaledCount(r, 62, minY, top);
        for (int i = 0; i < count; i++) {
            int x = bx + r.nextInt(16);
            int z = bz + r.nextInt(16);
            int y = Placement.uniform(r, minY, top);
            if (y > lv.ceiling) continue;
            int hit = Placement.scan(lv, x, y, z, -1, lv::solid, lv::air, 12);
            if (hit != Integer.MIN_VALUE && biomes.at(x, z) == CaveBiomes.LUSH) LushCaves.clay(lv, r, x, hit + 1, z);
        }
        // lush_caves_vegetation
        r = this.random(cx, cz, 11);
        count = Placement.scaledCount(r, 125, minY, top);
        for (int i = 0; i < count; i++) {
            int x = bx + r.nextInt(16);
            int z = bz + r.nextInt(16);
            int y = Placement.uniform(r, minY, top);
            if (y > lv.ceiling) continue;
            int hit = Placement.scan(lv, x, y, z, -1, lv::solid, lv::air, 12);
            if (hit != Integer.MIN_VALUE && biomes.at(x, z) == CaveBiomes.LUSH)
                LushCaves.floorVegetation(lv, r, x, hit + 1, z);
        }
        // classic_vines_cave_feature
        r = this.random(cx, cz, 12);
        count = Placement.scaledCount(r, 256, minY, top);
        for (int i = 0; i < count; i++) {
            int x = bx + r.nextInt(16);
            int z = bz + r.nextInt(16);
            int y = Placement.uniform(r, minY, top);
            if (y <= lv.ceiling && biomes.at(x, z) == CaveBiomes.LUSH) LushCaves.vines(lv, r, x, y, z);
        }
    }
}
