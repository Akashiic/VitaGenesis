package com.akashiic.vitagenesis.noise;

/** The subset of 1.18 {@code net.minecraft.util.Mth} used by world generation, with the same rounding. */
public final class Mth {

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0D / 65536.0D);
    }

    private Mth() {}

    /** Table sine of 1.18 (and 1.7.10 {@code MathHelper}); carvers depend on its rounding. */
    public static float sin(float v) {
        return SIN[(int) (v * 10430.378F) & 65535];
    }

    public static float cos(float v) {
        return SIN[(int) (v * 10430.378F + 16384.0F) & 65535];
    }

    public static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static long lfloor(double v) {
        long l = (long) v;
        return v < l ? l - 1L : l;
    }

    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static double lerp2(double tx, double ty, double v00, double v10, double v01, double v11) {
        return lerp(ty, lerp(tx, v00, v10), lerp(tx, v01, v11));
    }

    public static double lerp3(double tx, double ty, double tz, double v000, double v100, double v010, double v110,
        double v001, double v101, double v011, double v111) {
        return lerp(tz, lerp2(tx, ty, v000, v100, v010, v110), lerp2(tx, ty, v001, v101, v011, v111));
    }

    public static double smoothstep(double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    public static double inverseLerp(double v, double a, double b) {
        return (v - a) / (b - a);
    }

    public static double clampedLerp(double a, double b, double t) {
        if (t < 0.0D) return a;
        return t > 1.0D ? b : lerp(t, a, b);
    }

    public static double map(double v, double fromA, double fromB, double toA, double toB) {
        return lerp(inverseLerp(v, fromA, fromB), toA, toB);
    }

    public static double clampedMap(double v, double fromA, double fromB, double toA, double toB) {
        return clampedLerp(toA, toB, inverseLerp(v, fromA, fromB));
    }

    public static int quantize(double v, int step) {
        return floor(v / step) * step;
    }

    /** {@code Mth.getSeed(x, y, z)}: the per-position seed of positional random factories. */
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }
}
