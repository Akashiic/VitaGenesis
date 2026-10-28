package com.akashiic.vitagenesis.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DeepCavesTest {

    /** A deep layer as Bellow fills it: bedrock floor with the 1.18 gradient, stone above. */
    static final class FakeChunk implements DeepCaves.Chunk {

        static final byte BLOCK_FILL = 10;
        static final byte BLOCK_BEDROCK = 11;
        static final byte BLOCK_ORE = 20;
        static final byte BLOCK_RAW = 21;
        static final byte BLOCK_FILLER = 22;

        final int minY;
        final byte[] blocks;

        FakeChunk(int minY, long seed, int chunkX, int chunkZ) {
            this.minY = minY;
            this.blocks = new byte[16 * 16 * -minY];
            for (int y = minY; y < 0; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        double p = y == minY ? 1.0D : Math.max(0.0D, 1.0D - (y - minY) / 5.0D);
                        long h = (seed * 31 + (chunkX * 16 + x) * 73856093L ^ y * 19349663L
                            ^ (chunkZ * 16 + z) * 83492791L);
                        boolean bedrock = p >= 1.0D || (Math.floorMod(h, 1000) / 1000.0D) < p;
                        this.blocks[this.index(x, y, z)] = bedrock ? BLOCK_BEDROCK : BLOCK_FILL;
                    }
                }
            }
        }

        int index(int x, int y, int z) {
            return ((y - this.minY) * 16 + z) * 16 + x;
        }

        byte get(int x, int y, int z) {
            return this.blocks[this.index(x, y, z)];
        }

        @Override
        public int kind(int lx, int y, int lz) {
            byte b = this.get(lx, y, lz);
            if (b == BLOCK_FILL) return FILL;
            if (b == BLOCK_BEDROCK) return y == this.minY ? OTHER : BEDROCK;
            if (b >= BLOCK_ORE) return VEIN;
            return OTHER;
        }

        @Override
        public void setSubstance(int lx, int y, int lz, byte substance) {
            this.blocks[this.index(lx, y, lz)] = substance; // AIR 1, WATER 2, LAVA 3
        }

        @Override
        public void setVein(int lx, int y, int lz, byte vein) {
            this.blocks[this.index(lx, y, lz)] = (byte) (BLOCK_ORE + vein - OreVeins.ORE);
        }
    }

    private static final long SEED = 8675309L;

    static FakeChunk generate(int minY, int cx, int cz, DeepCaves.Settings settings) {
        FakeChunk chunk = new FakeChunk(minY, SEED, cx, cz);
        DeepCaves.generate(noise(), cx, cz, minY, settings, chunk);
        return chunk;
    }

    private static CaveNoise cached;

    static synchronized CaveNoise noise() {
        if (cached == null) cached = new CaveNoise(SEED);
        return cached;
    }

    @Test
    void layerKeepsFloorAndSealAndLavaStaysDeep() {
        for (int minY : new int[] { -64, -128 }) {
            DeepCaves.Settings s = new DeepCaves.Settings();
            Map<Byte, int[]> perBand = new HashMap<>();
            int[] airByY = new int[-minY];
            int total = 0;
            // chunks spread over ~6000 blocks: the cave noises vary on scales of hundreds of blocks
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    FakeChunk c = generate(minY, i * 53 - 180, j * 59 - 200, s);
                    for (int y = minY; y < 0; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                byte b = c.get(x, y, z);
                                if (y == minY) assertEquals(FakeChunk.BLOCK_BEDROCK, b, "floor touched");
                                if (y >= s.ceiling() + 1) {
                                    assertTrue(
                                        b == FakeChunk.BLOCK_FILL || b >= FakeChunk.BLOCK_ORE,
                                        "seal broken at " + y);
                                }
                                if (b == Aquifer.LAVA) assertTrue(y < -10, "lava at Y " + y);
                                if (b == Aquifer.AIR || b == Aquifer.WATER || b == Aquifer.LAVA) {
                                    airByY[y - minY]++;
                                    total++;
                                }
                                perBand.computeIfAbsent(b, k -> new int[1])[0]++;
                            }
                        }
                    }
                }
            }
            int volume = 49 * 256 * -minY;
            double open = total / (double) volume;
            System.out.printf(
                "minY %d: open %.3f, air %.3f, water %.3f, lava %.3f, ore %d, raw %d, tuff %d%n",
                minY,
                open,
                count(perBand, Aquifer.AIR) / (double) volume,
                count(perBand, Aquifer.WATER) / (double) volume,
                count(perBand, Aquifer.LAVA) / (double) volume,
                count(perBand, FakeChunk.BLOCK_ORE),
                count(perBand, FakeChunk.BLOCK_RAW),
                count(perBand, FakeChunk.BLOCK_FILLER));
            StringBuilder profile = new StringBuilder();
            for (int y = minY; y < 0; y += 8) {
                int sum = 0;
                for (int k = 0; k < 8; k++) sum += airByY[y - minY + k];
                profile.append(String.format("%d:%.2f ", y, sum / (8.0D * 49 * 256)));
            }
            System.out.println("  open by Y: " + profile);
            assertTrue(open > 0.04D && open < 0.35D, "open fraction " + open);
            assertTrue(count(perBand, Aquifer.LAVA) > 0, "no lava at the bottom");
            assertTrue(count(perBand, FakeChunk.BLOCK_ORE) > 0, "no iron vein");
        }
    }

    private static int count(Map<Byte, int[]> m, byte b) {
        int[] v = m.get(b);
        return v == null ? 0 : v[0];
    }

    @Test
    void chunkBordersAreContinuous() {
        // the same world column seen from two chunks must agree: open cells right across a border are as correlated
        // as open cells one block apart inside a chunk
        DeepCaves.Settings s = new DeepCaves.Settings();
        int minY = -64;
        int borderSame = 0;
        int borderCells = 0;
        int innerSame = 0;
        int innerCells = 0;
        for (int cx = 0; cx < 6; cx++) {
            FakeChunk a = generate(minY, cx, 0, s);
            FakeChunk b = generate(minY, cx + 1, 0, s);
            for (int y = minY + 1; y < -2; y++) {
                for (int z = 0; z < 16; z++) {
                    borderSame += open(a.get(15, y, z)) == open(b.get(0, y, z)) ? 1 : 0;
                    borderCells++;
                    innerSame += open(a.get(7, y, z)) == open(a.get(8, y, z)) ? 1 : 0;
                    innerCells++;
                }
            }
        }
        double border = borderSame / (double) borderCells;
        double inner = innerSame / (double) innerCells;
        System.out.printf("agreement of neighbours: border %.3f, inner %.3f%n", border, inner);
        assertTrue(Math.abs(border - inner) < 0.03D, "border " + border + " vs inner " + inner);
    }

    private static boolean open(byte b) {
        return b == Aquifer.AIR || b == Aquifer.WATER || b == Aquifer.LAVA;
    }

    @Test
    void aquiferCoversEveryCellOfTheLayer() {
        // the grid lookup reads floorDiv(y + 1, 12) + 1: the top cell of the layer must be inside the cache
        for (int minY : new int[] { -64, -128, -512 }) {
            Aquifer aquifer = new Aquifer(noise(), 3, -7, minY, -1, true);
            for (int y = minY; y < 0; y++) {
                for (int x = 48; x < 64; x += 5) {
                    for (int z = -112; z < -96; z += 5) aquifer.compute(x, y, z, -0.5D);
                }
            }
        }
    }

    @Test
    void sameInputSameOutput() {
        DeepCaves.Settings s = new DeepCaves.Settings();
        FakeChunk a = generate(-64, 5, -9, s);
        FakeChunk b = generate(-64, 5, -9, s);
        for (int i = 0; i < a.blocks.length; i++) assertEquals(a.blocks[i], b.blocks[i]);
    }

    @Test
    void costPerChunk() {
        DeepCaves.Settings s = new DeepCaves.Settings();
        for (int i = 0; i < 20; i++) generate(-64, i, 100, s); // warm up
        for (int minY : new int[] { -64, -128 }) {
            long t0 = System.nanoTime();
            int n = 100;
            for (int i = 0; i < n; i++) generate(minY, i, 200, s);
            double ms = (System.nanoTime() - t0) / 1.0e6 / n;
            System.out.printf("terrain stage, floor %d: %.2f ms/chunk%n", minY, ms);
            assertTrue(ms < 50.0D, "too slow: " + ms + " ms per chunk");
        }
    }
}
