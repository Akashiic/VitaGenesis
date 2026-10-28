package com.akashiic.vitagenesis;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.api.IDeepGenerator;
import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.vitagenesis.feature.CaveFeatures;
import com.akashiic.vitagenesis.light.DeepLighting;
import com.akashiic.vitagenesis.terrain.CaveNoise;
import com.akashiic.vitagenesis.terrain.DeepCaves;
import com.akashiic.vitagenesis.terrain.SectionTerrain;

/** The deep generator registered with Below Core: caves in the terrain stage, cave features in the decoration stage. */
final class VitaGenerator implements IDeepGenerator {

    /** Noises per world seed (a server can run dimensions with different seeds). Main thread only. */
    private final Map<Long, CaveNoise> noises = new HashMap<>();
    private final Map<Long, CaveFeatures> features = new HashMap<>();

    @Override
    public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
        if (!CaveConfig.enabled || retrogen && !CaveConfig.retrogen) return;
        long t = System.nanoTime();
        CaveNoise noise = this.noises.computeIfAbsent(seed, CaveNoise::new);
        DeepCaves.generate(
            noise,
            chunk.xPosition,
            chunk.zPosition,
            minY,
            CaveConfig.settings(),
            new SectionTerrain(chunk, minY));
        GenStats.add(GenStats.TERRAIN, System.nanoTime() - t);
    }

    @Override
    public void decorate(IDeepRegion region) {
        if (!CaveConfig.enabled) return;
        long t = System.nanoTime();
        this.features.computeIfAbsent(region.seed(), CaveFeatures::new)
            .decorate(region, CaveConfig.settings());
        DeepLighting.markOurs(region.chunkAt(region.centerChunkX() << 4, region.centerChunkZ() << 4));
        GenStats.add(GenStats.DECORATION, System.nanoTime() - t);
    }

    @Override
    public void onDecorated(WorldServer world, Chunk chunk) {
        if (!CaveConfig.enabled) return;
        DeepLighting.onDecorated(world, chunk);
    }
}
