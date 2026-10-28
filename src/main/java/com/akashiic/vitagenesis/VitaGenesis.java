package com.akashiic.vitagenesis;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.vitagenesis.light.DeepLighting;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * 1.18+ world generation backported to 1.7.10. This first part shapes the deep layer of Below Core: noise caves,
 * aquifers, carvers, ore veins, lush and dripstone caves, geodes and the smaller cave features, with the blocks of Et
 * Futurum Requiem. Server side only: it adds no block, item or packet.
 */
@Mod(
    modid = VitaGenesis.MODID,
    name = "VitaGenesis",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:akashicbellow;after:etfuturum")
public class VitaGenesis {

    public static final String MODID = "vitagenesis";
    public static final Logger LOG = LogManager.getLogger("VitaGenesis");

    /** Below Core API with deep generators. */
    static final int REQUIRED_BELLOW_API = 2;

    static {
        try {
            ConfigurationManager.registerConfig(CaveConfig.class);
            ConfigurationManager.registerConfig(FeatureConfig.class);
        } catch (ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

    @Mod.EventHandler
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        if (event.getSide()
            .isClient() && Boolean.getBoolean("vitagenesis.clienttest")) {
            com.akashiic.vitagenesis.client.ClientSelfTest.register();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new DeepLighting.Events());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        int api;
        try {
            api = BellowAPI.apiVersion();
        } catch (NoSuchMethodError e) {
            api = 1;
        }
        if (api < REQUIRED_BELLOW_API) {
            throw new IllegalStateException(
                "VitaGenesis needs Below Core with deep generator API " + REQUIRED_BELLOW_API
                    + " or newer (found "
                    + api
                    + "): update Below Core");
        }
        CaveBlocks.resolve();
        BellowAPI.registerGenerator(MODID + ":caves", new VitaGenerator());
    }

    /** Headless verification: {@code -Dvitagenesis.selftest=true [-Dvitagenesis.selftest.exit=true]}. */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (Boolean.getBoolean("vitagenesis.selftest")) com.akashiic.vitagenesis.selftest.SelfTest.start();
    }
}
