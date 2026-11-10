package me.index.libs.view;

import me.index.config.parameters.DataParams;
import me.index.Benchmark;
import me.index.libs.execute.parameters.BenchmarkSummary;

import java.util.List;
import java.util.Locale;

public final class StringReport {
    private static final int PREVIEW = 3;
    private static final String TABLE_FORMAT = "%-40s %10s %8s %10s %12s %12s %12s %14s%n";

    private StringReport() {
    }

    public static String runtime(List<String> options) {
        return "each row runs in its own JVM: " + String.join(" ", options) + System.lineSeparator();
    }

    public static String begin(DataParams data, int[] keys) {
        return String.format(
                Locale.US,
                "%n=== data.keyset=%s  data.size=%s  data.seed=%s ===%n"
                        + "distinct keys: %d%n%s%nqueries: %d per batch, 100%% existing keys, "
                        + "a fresh pregenerated batch for every warmup and measured round%n"
                        + "warmup rounds %d, measured rounds %d%n%n",
                data.keyset(), data.size(), data.seed(),
                keys.length, preview(keys),
                Benchmark.QUERIES, Benchmark.WARMUP_ROUNDS, Benchmark.MEASURED_ROUNDS
        ) + String.format(Locale.US, TABLE_FORMAT,
                "index", "build ms", "maxErr", "size KiB", "min iter ms", "max iter ms", "mean iter ms", "throughput/s");
    }

    public static String row(BenchmarkSummary row) {
        if (!row.built()) return String.format(Locale.US, "%-40s %s%n", row.id(), row.note());
        double throughput = Benchmark.QUERIES * 1e9 / row.meanIterationNs();
        return String.format(Locale.US, TABLE_FORMAT, row.id(),
                String.format(Locale.US, "%.1f", row.buildMs()), Integer.toString(row.maxErr()),
                String.format(Locale.US, "%.1f", row.bytes() / 1024.0),
                String.format(Locale.US, "%.3f", row.minIterationNs() / 1e6),
                String.format(Locale.US, "%.3f", row.maxIterationNs() / 1e6),
                String.format(Locale.US, "%.3f", row.meanIterationNs() / 1e6),
                throughput > 0 && Double.isFinite(throughput)
                        ? String.format(Locale.US, "%.0f", throughput) : "-");
    }

    public static String preview(int[] keys) {
        StringBuilder sb = new StringBuilder();
        if (keys.length <= 2 * PREVIEW) {
            for (int i = 0; i < keys.length; i++) sb.append(i > 0 ? " " : "").append(keys[i]);
            return sb.toString();
        }
        for (int i = 0; i < PREVIEW; i++) sb.append(keys[i]).append(' ');
        sb.append("...");
        for (int i = keys.length - PREVIEW; i < keys.length; i++) sb.append(' ').append(keys[i]);
        return sb.toString();
    }

    public static String finish(long checksum) {
        return String.format(Locale.US, "%nchecksum: %d%n", checksum);
    }
}
