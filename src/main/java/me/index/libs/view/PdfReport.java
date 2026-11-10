package me.index.libs.view;

public final class PdfReport {
    private static final int MAX_POINTS = 100_000;
    private static final Plot.Color[] PALETTE = {
            Plot.RED, Plot.GREEN, Plot.ORANGE, Plot.PURPLE, Plot.BROWN, Plot.GRAY};

    private PdfReport() {
    }

    public static Plot plot(long[] keys) {
        long[] positions = new long[Math.min(keys.length, MAX_POINTS)];
        for (int i = 0; i < positions.length; i++) positions[i] = sourceIndex(i, keys.length, positions.length);
        return new Plot(900, 600)
                .title("Keys - Positions")
                .xlabel("keys")
                .ylabel("positions")
                .squareMarkers(keys.length >= 20_000)
                .scatter(sample(keys), positions, Plot.BLUE, "true_positions", 0.1);
    }

    public static void addPrediction(Plot plot, long[] keys, long[] positions, String label, int series) {
        if (keys.length != positions.length) {
            throw new IllegalArgumentException("keys and positions must have the same length");
        }
        plot.scatter(sample(keys), sample(positions), PALETTE[series % PALETTE.length], label, 0.1);
    }

    private static long[] sample(long[] values) {
        if (values.length <= MAX_POINTS) return values;
        long[] sampled = new long[MAX_POINTS];
        for (int i = 0; i < sampled.length; i++) {
            sampled[i] = values[sourceIndex(i, values.length, sampled.length)];
        }
        return sampled;
    }

    private static int sourceIndex(int point, int sourceSize, int sampleSize) {
        return sampleSize < 2 ? 0 : (int) ((long) point * (sourceSize - 1) / (sampleSize - 1));
    }
}
