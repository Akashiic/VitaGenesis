package com.akashiic.vitagenesis.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import org.apache.commons.io.FileUtils;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.vitagenesis.CaveBlocks;
import com.akashiic.vitagenesis.VitaGenesis;
import com.akashiic.vitagenesis.feature.CaveBiomes;
import com.akashiic.vitagenesis.feature.CaveFeatures;
import com.akashiic.vitagenesis.selftest.SelfTest;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Headless client verification ({@code tools/clienttest.sh}, under Xvfb): a new singleplayer world, then for a lush
 * area and a dripstone area the camera is put inside a cave (the spot with the most cave blocks in view). The client
 * must hold the same blocks, block light and glow lichen faces as the server (light written by the light stage and
 * resent, tile entity data sent with the chunks); screenshots are taken with the natural light and with night vision.
 * Report: {@code run/client/vitagenesis-clienttest.txt}, screenshots in {@code run/client/screenshots/}.
 */
public final class ClientSelfTest {

    private static final String SAVE = "vitagenesis-clienttest";
    private static final int LOADED = 5;
    /** Half size of the compared box around the camera. */
    private static final int BOX_XZ = 10;
    private static final int BOX_Y = 6;

    private static final class Target {

        final String name;
        final int kind;
        final Block[] markers;
        volatile boolean requested;
        volatile boolean ready;
        volatile String failure;
        int x, y, z;
        float yaw, pitch;
        int score;
        int[] light;
        int[] blocks;
        int[] lichen;

        Target(String name, int kind, Block... markers) {
            this.name = name;
            this.kind = kind;
            this.markers = markers;
        }

        boolean marker(Block b) {
            for (Block m : this.markers) {
                if (m != null && b == m) return true;
            }
            return false;
        }
    }

    private final List<String> out = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private int failures;
    private int state;
    private int timer;
    private int current;
    private int shotIndex;
    private volatile boolean nightVisionRequested;
    private volatile boolean nightVisionReady;

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientSelfTest());
    }

    private void check(boolean ok, String what) {
        if (!ok) this.failures++;
        this.out.add((ok ? "PASS " : "FAIL ") + what);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            this.step(mc);
        } catch (Exception e) {
            VitaGenesis.LOG.error("[clienttest] crashed", e);
            this.check(false, "exception: " + e);
            this.finish(mc);
        }
    }

    private void step(Minecraft mc) throws Exception {
        switch (this.state) {
            case 0:
                if (mc.currentScreen instanceof GuiMainMenu) {
                    FileUtils.deleteDirectory(new File(mc.mcDataDir, "saves/" + SAVE));
                    mc.gameSettings.renderDistanceChunks = 6;
                    mc.gameSettings.hideGUI = true;
                    mc.gameSettings.showInventoryAchievementHint = false;
                    mc.launchIntegratedServer(
                        SAVE,
                        SAVE,
                        new WorldSettings(
                            "vitagenesis".hashCode(),
                            WorldSettings.GameType.CREATIVE,
                            true,
                            false,
                            WorldType.DEFAULT));
                    this.state = 1;
                }
                break;
            case 1:
                if (mc.theWorld != null && mc.thePlayer != null && ++this.timer > 40) {
                    if (CaveBlocks.mossBlock != null) {
                        this.targets.add(
                            new Target(
                                "lush",
                                CaveBiomes.LUSH,
                                CaveBlocks.mossBlock,
                                CaveBlocks.mossCarpet,
                                CaveBlocks.azalea,
                                CaveBlocks.caveVine,
                                CaveBlocks.caveVinePlant,
                                CaveBlocks.glowLichen));
                    }
                    if (CaveBlocks.dripstoneBlock != null) {
                        this.targets.add(
                            new Target(
                                "dripstone",
                                CaveBiomes.DRIPSTONE,
                                CaveBlocks.dripstoneBlock,
                                CaveBlocks.pointedDripstone));
                    }
                    this.check(!this.targets.isEmpty(), "lush or dripstone blocks registered by Et Futurum");
                    this.next(mc);
                }
                break;
            case 2: { // server finds the spot and moves the player there
                Target t = this.targets.get(this.current);
                if (t.failure != null) {
                    this.check(false, t.name + ": " + t.failure);
                    this.current++;
                    this.next(mc);
                } else if (t.ready && ++this.timer > 400) { // chunks sent and rendered (llvmpipe is slow)
                    this.verify(mc, t);
                    this.shot(mc, "vita-" + t.name + "-natural");
                    this.nightVisionReady = false;
                    this.nightVisionRequested = true;
                    this.state = 3;
                    this.timer = 0;
                    this.shotIndex = 0;
                }
                break;
            }
            case 3: {
                Target t = this.targets.get(this.current);
                if (!this.nightVisionReady || ++this.timer < 40) break;
                this.timer = 0;
                if (this.shotIndex < 4) {
                    mc.thePlayer.rotationYaw = t.yaw + this.shotIndex * 90.0F;
                    mc.thePlayer.rotationPitch = this.shotIndex == 0 ? t.pitch : 10.0F;
                    this.state = 4;
                } else {
                    this.current++;
                    this.next(mc);
                }
                break;
            }
            case 4:
                if (++this.timer > 20) {
                    Target t = this.targets.get(this.current);
                    this.shot(mc, "vita-" + t.name + "-" + this.shotIndex++);
                    this.timer = 0;
                    this.state = 3;
                }
                break;
            default:
                break;
        }
    }

    private void next(Minecraft mc) {
        this.timer = 0;
        this.nightVisionRequested = false;
        if (this.current >= this.targets.size()) {
            this.finish(mc);
            return;
        }
        this.targets.get(this.current).requested = true;
        this.state = 2;
    }

    // ------------------------------------------------------------------------------------------- server thread

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MinecraftServer.getServer() == null) return;
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (world == null || world.playerEntities.isEmpty()) return;
        EntityPlayerMP player = (EntityPlayerMP) world.playerEntities.get(0);
        for (Target t : this.targets) {
            if (t.requested && !t.ready && t.failure == null) {
                t.requested = false;
                this.prepare(world, player, t);
            }
        }
        if (this.nightVisionRequested) {
            this.nightVisionRequested = false;
            player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 20 * 600, 0));
            this.nightVisionReady = true;
        }
    }

    private void prepare(WorldServer world, EntityPlayerMP player, Target t) {
        player.removePotionEffect(Potion.nightVision.id);
        int[] area = SelfTest.findArea(world, new CaveFeatures(world.getSeed()), t.kind);
        if (area == null) {
            t.failure = "no area found";
            return;
        }
        SelfTest.load(world, area, LOADED);
        int minY = BellowAPI.getMinY(world);
        int x0 = (area[0] - 1) << 4;
        int z0 = (area[1] - 1) << 4;
        for (int x = x0; x < x0 + 48; x += 2) {
            for (int z = z0; z < z0 + 48; z += 2) {
                for (int y = minY + 6; y <= -10; y += 2) {
                    if (!air(world, x, y, z) || !air(world, x, y + 1, z)) continue;
                    int score = 0;
                    double sx = 0, sy = 0, sz = 0;
                    for (int dx = -8; dx <= 8; dx += 2) {
                        for (int dy = -4; dy <= 4; dy += 2) {
                            for (int dz = -8; dz <= 8; dz += 2) {
                                if (!t.marker(world.getBlock(x + dx, y + dy, z + dz))) continue;
                                score++;
                                sx += dx;
                                sy += dy;
                                sz += dz;
                            }
                        }
                    }
                    if (score > t.score) {
                        t.score = score;
                        t.x = x;
                        t.y = y;
                        t.z = z;
                        t.yaw = (float) Math.toDegrees(Math.atan2(-sx, sz));
                        t.pitch = (float) -Math.toDegrees(Math.atan2(sy / score - 1.6D, Math.hypot(sx, sz) / score));
                    }
                }
            }
        }
        if (t.score == 0) {
            t.failure = "no " + t.name + " cave block found around chunk " + area[0] + "," + area[1];
            return;
        }
        this.snapshot(world, t);
        player.capabilities.isFlying = true;
        player.sendPlayerAbilities();
        player.playerNetServerHandler.setPlayerLocation(t.x + 0.5D, t.y, t.z + 0.5D, t.yaw, t.pitch);
        this.out.add(
            String.format(
                "%s: camera at %d %d %d (chunk %d,%d), %d cave blocks in view",
                t.name,
                t.x,
                t.y,
                t.z,
                area[0],
                area[1],
                t.score));
        t.ready = true;
    }

    private static boolean air(World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .getMaterial() == Material.air;
    }

    private static int index(int dx, int dy, int dz) {
        return ((dy + BOX_Y) * (2 * BOX_XZ + 1) + dz + BOX_XZ) * (2 * BOX_XZ + 1) + dx + BOX_XZ;
    }

    private void snapshot(World world, Target t) {
        int n = (2 * BOX_XZ + 1) * (2 * BOX_XZ + 1) * (2 * BOX_Y + 1);
        t.light = new int[n];
        t.blocks = new int[n];
        t.lichen = new int[n];
        this.visit(world, t, t.light, t.blocks, t.lichen);
    }

    private void visit(World world, Target t, int[] light, int[] blocks, int[] lichen) {
        for (int dx = -BOX_XZ; dx <= BOX_XZ; dx++) {
            for (int dy = -BOX_Y; dy <= BOX_Y; dy++) {
                for (int dz = -BOX_XZ; dz <= BOX_XZ; dz++) {
                    int x = t.x + dx;
                    int y = t.y + dy;
                    int z = t.z + dz;
                    int i = index(dx, dy, dz);
                    Block b = world.getBlock(x, y, z);
                    blocks[i] = Block.getIdFromBlock(b);
                    light[i] = world.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
                    lichen[i] = -1;
                    if (b == CaveBlocks.glowLichen && CaveBlocks.glowLichen != null) {
                        TileEntity te = world.getTileEntity(x, y, z);
                        if (te != null) {
                            NBTTagCompound tag = new NBTTagCompound();
                            te.writeToNBT(tag);
                            lichen[i] = tag.getInteger("State");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------- client thread

    private void verify(Minecraft mc, Target t) {
        int n = t.light.length;
        int[] light = new int[n];
        int[] blocks = new int[n];
        int[] lichen = new int[n];
        this.visit(mc.theWorld, t, light, blocks, lichen);
        int blockDiff = 0;
        int lightDiff = 0;
        int lichenCount = 0;
        int lichenDiff = 0;
        int lit = 0;
        // the server's light now (racy read of the integrated server, for the diagnosis only)
        int[] now = new int[n];
        WorldServer server = MinecraftServer.getServer()
            .worldServerForDimension(0);
        this.visit(server, t, now, new int[n], new int[n]);
        int matchNow = 0;
        List<String> sample = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (blocks[i] == t.blocks[i] && light[i] != t.light[i]) {
                if (light[i] == now[i]) matchNow++;
                if (sample.size() < 8)
                    sample.add("#" + i + " client " + light[i] + " snapshot " + t.light[i] + " server now " + now[i]);
            }
        }
        if (!sample.isEmpty())
            this.out.add(t.name + ": light cells that differ (" + matchNow + " match the server now): " + sample);
        for (int i = 0; i < n; i++) {
            if (blocks[i] != t.blocks[i]) blockDiff++;
            else if (light[i] != t.light[i]) lightDiff++;
            if (t.light[i] > 0) lit++;
            if (t.lichen[i] >= 0) {
                lichenCount++;
                if (lichen[i] != t.lichen[i]) lichenDiff++;
            }
        }
        // a few cells may change between the snapshot and now (flowing fluids)
        this.check(
            blockDiff <= n / 100,
            t.name + ": client has the server's blocks: " + blockDiff + " of " + n + " differ");
        this.check(
            lightDiff <= n / 100,
            t.name + ": client has the server's block light: "
                + lightDiff
                + " of "
                + n
                + " differ ("
                + lit
                + " lit cells)");
        this.check(
            lichenDiff == 0,
            t.name + ": glow lichen faces reached the client: " + (lichenCount - lichenDiff) + "/" + lichenCount);
    }

    private void shot(Minecraft mc, String name) {
        ScreenShotHelper
            .saveScreenshot(mc.mcDataDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        this.out.add("SHOT screenshots/" + name + ".png");
    }

    private void finish(Minecraft mc) {
        this.out.add(this.failures == 0 ? "RESULT PASS" : "RESULT FAIL (" + this.failures + ")");
        for (String line : this.out) VitaGenesis.LOG.info("[clienttest] {}", line);
        try {
            Files
                .write(new File(mc.mcDataDir, "vitagenesis-clienttest.txt").toPath(), this.out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            VitaGenesis.LOG.error("Cannot write the client test report", e);
        }
        this.state = 99;
        mc.shutdown();
    }
}
