package com.akashiic.vitagenesis.noise;

/** 1.18 {@code ImprovedNoise}: Perlin's improved gradient noise with a random offset and permutation. */
public final class ImprovedNoise {

    private static final int[][] GRADIENT = { { 1, 1, 0 }, { -1, 1, 0 }, { 1, -1, 0 }, { -1, -1, 0 }, { 1, 0, 1 },
        { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 }, { 0, 1, 1 }, { 0, -1, 1 }, { 0, 1, -1 }, { 0, -1, -1 }, { 1, 1, 0 },
        { 0, -1, 1 }, { -1, 1, 0 }, { 0, -1, -1 } };

    private final byte[] p = new byte[256];
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(XoroshiroRandom random) {
        this.xo = random.nextDouble() * 256.0D;
        this.yo = random.nextDouble() * 256.0D;
        this.zo = random.nextDouble() * 256.0D;
        for (int i = 0; i < 256; i++) this.p[i] = (byte) i;
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            byte b = this.p[i];
            this.p[i] = this.p[i + j];
            this.p[i + j] = b;
        }
    }

    public double noise(double x, double y, double z) {
        double dx = x + this.xo;
        double dy = y + this.yo;
        double dz = z + this.zo;
        int ix = Mth.floor(dx);
        int iy = Mth.floor(dy);
        int iz = Mth.floor(dz);
        return this.sampleAndLerp(ix, iy, iz, dx - ix, dy - iy, dz - iz);
    }

    private int p(int i) {
        return this.p[i & 255] & 255;
    }

    private double sampleAndLerp(int x, int y, int z, double fx, double fy, double fz) {
        int a = this.p(x);
        int b = this.p(x + 1);
        int aa = this.p(a + y);
        int ab = this.p(a + y + 1);
        int ba = this.p(b + y);
        int bb = this.p(b + y + 1);
        double v000 = gradDot(this.p(aa + z), fx, fy, fz);
        double v100 = gradDot(this.p(ba + z), fx - 1.0D, fy, fz);
        double v010 = gradDot(this.p(ab + z), fx, fy - 1.0D, fz);
        double v110 = gradDot(this.p(bb + z), fx - 1.0D, fy - 1.0D, fz);
        double v001 = gradDot(this.p(aa + z + 1), fx, fy, fz - 1.0D);
        double v101 = gradDot(this.p(ba + z + 1), fx - 1.0D, fy, fz - 1.0D);
        double v011 = gradDot(this.p(ab + z + 1), fx, fy - 1.0D, fz - 1.0D);
        double v111 = gradDot(this.p(bb + z + 1), fx - 1.0D, fy - 1.0D, fz - 1.0D);
        return Mth.lerp3(
            Mth.smoothstep(fx),
            Mth.smoothstep(fy),
            Mth.smoothstep(fz),
            v000,
            v100,
            v010,
            v110,
            v001,
            v101,
            v011,
            v111);
    }

    private static double gradDot(int hash, double x, double y, double z) {
        int[] g = GRADIENT[hash & 15];
        return g[0] * x + g[1] * y + g[2] * z;
    }
}
