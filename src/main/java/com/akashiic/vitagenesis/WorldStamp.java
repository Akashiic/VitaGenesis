package com.akashiic.vitagenesis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraftforge.common.DimensionManager;

import com.akashiic.vitagenesis.terrain.DeepCaves;

/**
 * Remembers, in the save, the terrain settings the deep layer was generated with. Chunks are a function of (seed,
 * position, settings): a chunk generated after a change does not match its older neighbours at the border, and a cave
 * full of water next to an empty one floods it once the fluids start flowing. The change is allowed (the config says
 * it only applies to new chunks), but it is logged loudly, once, when the world starts with other settings.
 */
public final class WorldStamp {

    /** Bumped whenever the same settings would generate different terrain. */
    static final int GENERATOR = 1;
    private static final String FILE = "vitagenesis.txt";

    private WorldStamp() {}

    static String describe(DeepCaves.Settings s) {
        return "generator=" + GENERATOR
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
            + s.fade;
    }

    static void check() {
        File root = DimensionManager.getCurrentSaveRootDirectory();
        if (root == null || !CaveConfig.enabled) return;
        File file = new File(root, FILE);
        String now = describe(CaveConfig.settings());
        try {
            if (file.isFile()) {
                String before = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (before.equals(now)) return;
                VitaGenesis.LOG.warn(
                    "The deep layer of this world was generated with [{}] and is now generated with [{}]. New chunks "
                        + "will not match the old ones at the borders: caves may end in flat walls, and water or lava "
                        + "from one side may flood the other. Revert config/vitagenesis.cfg unless this is intended.",
                    before,
                    now);
            }
            Files.write(file.toPath(), now.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            VitaGenesis.LOG.warn("Could not read or write {}", file, e);
        }
    }
}
