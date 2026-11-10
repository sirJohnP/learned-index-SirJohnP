package me.index.libs.execute.parameters;

public record BenchmarkSummary(
        String id,
        double buildMs,
        int maxErr,
        long bytes,
        double minIterationNs,
        double maxIterationNs,
        double meanIterationNs,
        long checksum,
        String note
) {
    public static BenchmarkSummary skipped(String id, String note) {
        return new BenchmarkSummary(id, Double.NaN, -1, -1,
                Double.NaN, Double.NaN, Double.NaN, 0, note);
    }

    public boolean built() {
        return note == null;
    }

    public boolean shouldPlot() {
        return built() && maxErr > 0;
    }

    public BenchmarkSummary failed(String problem) {
        return skipped(id, "failed: " + problem);
    }
}
