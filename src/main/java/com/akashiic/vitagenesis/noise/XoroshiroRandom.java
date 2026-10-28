package com.akashiic.vitagenesis.noise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 1.18 {@code XoroshiroRandomSource}: Xoroshiro128++ with the seed upgrade of {@code RandomSupport} and the
 * positional factory ({@code forkPositional}, {@code fromHashOf}, {@code at}) the noise router is built from.
 */
public final class XoroshiroRandom {

    static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    static final long SILVER_RATIO_64 = 7640891576956012809L;

    private long seedLo;
    private long seedHi;

    /** {@code new XoroshiroRandomSource(seed)}: the 64-bit seed is upgraded to 128 bits. */
    public XoroshiroRandom(long seed) {
        long lo = seed ^ SILVER_RATIO_64;
        long hi = lo + GOLDEN_RATIO_64;
        this.init(mixStafford13(lo), mixStafford13(hi));
    }

    public XoroshiroRandom(long lo, long hi) {
        this.init(lo, hi);
    }

    private void init(long lo, long hi) {
        if ((lo | hi) == 0L) {
            lo = GOLDEN_RATIO_64;
            hi = SILVER_RATIO_64;
        }
        this.seedLo = lo;
        this.seedHi = hi;
    }

    static long mixStafford13(long z) {
        z = (z ^ z >>> 30) * -4658895280553007687L;
        z = (z ^ z >>> 27) * -7723592293110705685L;
        return z ^ z >>> 31;
    }

    public long nextLong() {
        long lo = this.seedLo;
        long hi = this.seedHi;
        long result = Long.rotateLeft(lo + hi, 17) + lo;
        hi ^= lo;
        this.seedLo = Long.rotateLeft(lo, 49) ^ hi ^ hi << 21;
        this.seedHi = Long.rotateLeft(hi, 28);
        return result;
    }

    public int nextInt() {
        return (int) this.nextLong();
    }

    /** Lemire's unbiased bounded int, exactly as 1.18. */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        long l = Integer.toUnsignedLong(this.nextInt());
        long m = l * bound;
        long n = m & 0xFFFFFFFFL;
        if (n < bound) {
            for (int threshold = Integer.remainderUnsigned(~bound + 1, bound); n < threshold; n = m & 0xFFFFFFFFL) {
                l = Integer.toUnsignedLong(this.nextInt());
                m = l * bound;
            }
        }
        return (int) (m >> 32);
    }

    private long nextBits(int bits) {
        return this.nextLong() >>> 64 - bits;
    }

    public float nextFloat() {
        return this.nextBits(24) * 5.9604645E-8F;
    }

    public double nextDouble() {
        return this.nextBits(53) * 0x1.0p-53;
    }

    public Positional forkPositional() {
        return new Positional(this.nextLong(), this.nextLong());
    }

    /** {@code XoroshiroPositionalRandomFactory}. */
    public static final class Positional {

        private final long seedLo;
        private final long seedHi;

        public Positional(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        public XoroshiroRandom at(int x, int y, int z) {
            return new XoroshiroRandom(Mth.getSeed(x, y, z) ^ this.seedLo, this.seedHi);
        }

        /** Keys are resource locations as text, e.g. {@code minecraft:cave_cheese}. */
        public XoroshiroRandom fromHashOf(String name) {
            byte[] md5 = md5(name);
            return new XoroshiroRandom(bigEndian(md5, 0) ^ this.seedLo, bigEndian(md5, 8) ^ this.seedHi);
        }
    }

    private static byte[] md5(String text) {
        try {
            return MessageDigest.getInstance("MD5")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required by every Java runtime", e);
        }
    }

    private static long bigEndian(byte[] b, int off) {
        long v = 0L;
        for (int i = 0; i < 8; i++) v = v << 8 | b[off + i] & 0xFFL;
        return v;
    }
}
