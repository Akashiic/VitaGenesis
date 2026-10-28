package com.akashiic.vitagenesis;

/** Time spent in each stage of the cave generator since the server started (main thread only). */
public final class GenStats {

    public static final String[] STAGES = { "terrain", "decoration", "light" };

    private static final long[] NANOS = new long[STAGES.length];
    private static final long[] CALLS = new long[STAGES.length];

    private GenStats() {}

    static void add(int stage, long nanos) {
        NANOS[stage] += nanos;
        CALLS[stage]++;
    }

    /** One line per stage: calls and mean milliseconds per call. */
    public static String report() {
        StringBuilder s = new StringBuilder("cost per chunk:");
        for (int i = 0; i < STAGES.length; i++) {
            s.append(
                String
                    .format(" %s %.2f ms (%d)", STAGES[i], CALLS[i] == 0 ? 0.0D : NANOS[i] / 1e6 / CALLS[i], CALLS[i]));
        }
        return s.toString();
    }
}
