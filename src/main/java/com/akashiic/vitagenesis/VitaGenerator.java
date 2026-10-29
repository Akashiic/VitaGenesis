package com.akashiic.vitagenesis;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.IDeepGenerator;
import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.vitagenesis.feature.CaveFeatures;
import com.akashiic.vitagenesis.feature.FluidActivation;
import com.akashiic.vitagenesis.layer.DeepLayer;
import com.akashiic.vitagenesis.layer.Ores;
import com.akashiic.vitagenesis.noise.XoroshiroRandom;
import com.akashiic.vitagenesis.terrain.CaveNoise;
import com.akashiic.vitagenesis.terrain.DeepCaves;
import com.akashiic.vitagenesis.terrain.SectionTerrain;

/**
 * The deep generator registered with Below Core: caves in the terrain stage, cave features in the decoration stage,
 * fluids started once Below Core has lit the chunk.
 */
final class VitaGenerator implements IDeepGenerator {

    /** Bumped whenever the same settings would generate different terrain. */
    static final int GENERATOR = 1;
    /** Per-chunk data key: VitaGenesis decorated the chunk (Below Core calls generators that are switched off too). */
    private static final String DECORATED = "D";

    /** Noises per world seed (a server can run dimensions with different seeds). Main thread only. */
    private final Map<Long, CaveNoise> noises = new HashMap<>();
    private final Map<Long, CaveFeatures> features = new HashMap<>();
    private final Map<Long, XoroshiroRandom.Positional> bedrock = new HashMap<>();

    /** Drops the per-seed caches (server stopped: the next world may have another seed, Mystcraft ages add more). */
    void clearCaches() {
        this.noises.clear();
        this.features.clear();
        this.bedrock.clear();
    }

    /**
     * The terrain settings: chunks are a function of (seed, position, settings), and Below Core warns when the version
     * of a dimension's generators changes between sessions (borders between old and new chunks would not match).
     */
    @Override
    public String version() {
        DeepCaves.Settings s = CaveConfig.settings();
        return GENERATOR + (CaveConfig.enabled ? "" : " off")
            + " noiseCaves="
            + s.noiseCaves
            + " aquifers="
            + s.aquifers
            + " carvers="
            + s.carvers
            + " oreVeins="
            + s.oreVeins
            + " seal="
            + s.seal
            + " fade="
            + s.fade
            + Ores.describe();
    }

    @Override
    public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
        long t = System.nanoTime();
        // the layer itself, always: VitaGenesis is what the deep layer is made of
        DeepLayer.generate(chunk, minY, this.bedrock.computeIfAbsent(seed, DeepLayer::bedrockRandom));
        if (!CaveConfig.enabled || retrogen && !CaveConfig.retrogen) {
            GenStats.add(GenStats.TERRAIN, System.nanoTime() - t);
            return;
        }
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
        long t = System.nanoTime();
        if (!CaveConfig.enabled) {
            this.features.computeIfAbsent(region.seed(), CaveFeatures::new)
                .decorateOres(region);
            GenStats.add(GenStats.DECORATION, System.nanoTime() - t);
            return;
        }
        try {
            this.features.computeIfAbsent(region.seed(), CaveFeatures::new)
                .decorate(region, CaveConfig.settings());
        } finally {
            // ours to start the fluids of, even if a feature failed
            Chunk centre = region.chunkAt(region.centerChunkX() << 4, region.centerChunkZ() << 4);
            BellowAPI.getGeneratorData(centre, VitaGenesis.GENERATOR_ID)
                .setBoolean(DECORATED, true);
            centre.setChunkModified();
        }
        GenStats.add(GenStats.DECORATION, System.nanoTime() - t);
    }

    @Override
    public void onLit(WorldServer world, Chunk chunk) {
        if (!CaveConfig.enabled || !FeatureConfig.flowingFluids) return;
        NBTTagCompound data = BellowAPI.getGeneratorData(chunk, VitaGenesis.GENERATOR_ID);
        // decorated while VitaGenesis was off: what is there may be what players built
        if (!data.getBoolean(DECORATED)) return;
        long t = System.nanoTime();
        FluidActivation.run(world, chunk, BellowAPI.getMinY(world));
        GenStats.add(GenStats.FLUIDS, System.nanoTime() - t);
    }
}
