package me.index;

import me.index.config.parameters.IndexParams;
import me.index.config.parameters.PiecewiseLinearParams;
import me.index.libs.execute.parameters.BenchmarkArguments;
import me.index.libs.execute.parameters.BenchmarkQueries;
import me.index.libs.execute.parameters.BenchmarkResult;
import me.index.libs.execute.parameters.BenchmarkSummary;
import me.index.libs.execute.JvmRunner;
import me.index.libs.math.Sorted;
import me.index.task.Index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public final class Benchmark {
    public static final List<String> JVM_OPTIONS = List.of(
            "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-Xms8g", "-Xmx8g", "-XX:+AlwaysPreTouch");
    public static final int QUERIES = 100_000;
    public static final int WARMUP_ROUNDS = 20;
    public static final int MEASURED_ROUNDS = 10;

    private Benchmark() {
    }

    public static void main(String[] args) {
        PrintStream err = System.err;
        BenchmarkArguments job;
        try {
            job = BenchmarkArguments.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            err.println("usage: Benchmark --keyset <name> --size <name> --data-seed <long>"
                    + " --data-dir <path> --index <name> --result <path> --plot <true|false>"
                    + " [--eps <int>] [--layers <none|sizes> --loss <name> [--loss-parameter <double>]"
                    + " --learning-rate <double> --epochs <int> --batch-size <int>"
                    + " --model-seed <long> --suffix <text>]");
            System.exit(2);
            return;
        }
        try {
            run(job, System::nanoTime, err);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    public static BenchmarkResult fork(BenchmarkArguments job, JvmRunner runner, Path java, String classpath,
                                       Duration timeout,
                                       PrintStream out, PrintStream err) throws IOException {
        BenchmarkSummary fallback = BenchmarkSummary.skipped(job.index().label(), "no result");
        try {
            Files.deleteIfExists(job.resultFile());
            JvmRunner.Exit exit = runner.run(command(java, classpath, job), timeout, out, err);
            if (exit.timedOut()) {
                String limit = timeout.toMillis() % 60_000 == 0
                        ? timeout.toMinutes() + " min" : timeout.toMillis() + " ms";
                return new BenchmarkResult(fallback.failed("timed out after " + limit), null);
            }
            if (exit.code() == 3) return new BenchmarkResult(fallback.failed("out of memory"), null);
            if (exit.code() != 0)
                return new BenchmarkResult(fallback.failed("JVM exited with code " + exit.code()), null);
            try (InputStream input = new BufferedInputStream(Files.newInputStream(job.resultFile()))) {
                int limit = job.plot() ? (int) Math.min(job.dataset().size().size,
                        Files.size(job.resultFile()) / Integer.BYTES) : 0;
                return BenchmarkResult.read(input, limit);
            } catch (IOException e) {
                return new BenchmarkResult(fallback.failed("cannot read result: " + e.getMessage()), null);
            }
        } finally {
            Files.deleteIfExists(job.resultFile());
        }
    }

    private static List<String> command(Path java, String classpath, BenchmarkArguments job) {
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.addAll(JVM_OPTIONS);
        command.add("-cp");
        command.add(classpath);
        command.add(Benchmark.class.getName());
        command.addAll(job.arguments());
        return List.copyOf(command);
    }

    private static void run(BenchmarkArguments job, LongSupplier clock, PrintStream err) throws IOException {
        int[] keys = Context.load(job.dataset(), job.dataDir()).intKeys();
        BenchmarkResult result = measure(job.index(), keys, job.dataset().seed(), job.plot(), clock);
        String warning = warning(job.index(), result.summary());
        if (!warning.isEmpty()) err.println(warning);
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(job.resultFile()))) {
            result.write(output);
        }
    }

    public static String warning(IndexParams spec, BenchmarkSummary summary) {
        if (spec instanceof PiecewiseLinearParams pla && summary.built() && summary.maxErr() > pla.err().value) {
            return "[WARNING] index " + summary.id() + " prediction error " + summary.maxErr()
                    + " exceeds requested " + pla.err().value
                    + "; check segmentation and floating-point rounding. PlaIndex uses the measured error for lookup.";
        }
        return "";
    }

    private record Timing(long minNs, long maxNs, double meanNs, long checksum) {
    }

    public static BenchmarkResult measure(IndexParams spec, int[] keys, long querySeed, boolean plot,
                                          LongSupplier clock) {
        List<BenchmarkQueries> batches = BenchmarkQueries.createBatches(
                keys, QUERIES, WARMUP_ROUNDS + MEASURED_ROUNDS, querySeed);
        String id = spec.label();
        try {
            Index index = spec.createIndex();
            long startedAt = clock.getAsLong();
            index.build(keys);
            double buildMs = (clock.getAsLong() - startedAt) / 1e6;
            id = index.id() + spec.suffix();
            int maxErr = index.maxError();
            long bytes = index.sizeInBytes();
            boolean exactSearch = spec.type().isSearch();
            if (maxErr < 0 || bytes < 0) {
                throw new IllegalStateException("negative index metadata: maxError=" + maxErr + ", bytes=" + bytes);
            }
            if (exactSearch && (maxErr != 0 || bytes != 0)) {
                throw new IllegalStateException("search indexes must report maxError=0 and sizeInBytes=0");
            }

            Timing timing = time(index, batches, clock);
            for (int i = 0; i < batches.size(); i++) {
                BenchmarkQueries batch = batches.get(i);
                verify(index, batch, maxErr, keys, exactSearch);
                if (i >= WARMUP_ROUNDS) verifyMissingKeys(index, keys, batch.keys(), exactSearch);
            }

            BenchmarkSummary summary = new BenchmarkSummary(id, buildMs, maxErr, bytes,
                    timing.minNs(), timing.maxNs(), timing.meanNs(), timing.checksum(), null);
            return new BenchmarkResult(summary, plot && summary.shouldPlot() ? predictAll(index, keys) : null);
        } catch (UnsupportedOperationException e) {
            return new BenchmarkResult(BenchmarkSummary.skipped(id, "not implemented"), null);
        } catch (RuntimeException | Error e) {
            return new BenchmarkResult(BenchmarkSummary.skipped(id, "failed: " + e), null);
        }
    }

    private static Timing time(Index index, List<BenchmarkQueries> batches, LongSupplier clock) {
        long checksum = 0;
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            checksum += round(index, batches.get(round).keys());
        }

        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        long total = 0;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            int[] queries = batches.get(WARMUP_ROUNDS + round).keys();
            long startedAt = clock.getAsLong();
            checksum += round(index, queries);
            long elapsed = clock.getAsLong() - startedAt;
            best = Math.min(best, elapsed);
            worst = Math.max(worst, elapsed);
            total += elapsed;
        }
        return new Timing(best, worst, (double) total / MEASURED_ROUNDS, checksum);
    }

    private static long round(Index index, int[] queries) {
        long sum = 0;
        for (int i = 0; i < queries.length; i++) {
            sum += index.lower_bound(queries[i]);
        }
        return sum;
    }

    private static void verify(Index index, BenchmarkQueries queries, int maxErr, int[] keys, boolean exactSearch) {
        int n = keys.length;
        int[] asked = queries.keys();
        int[] expected = queries.expected();
        for (int i = 0; i < asked.length; i++) {
            int found = index.lower_bound(asked[i]);
            if (found != expected[i]) {
                throw new IllegalStateException("lower_bound(" + asked[i] + ") returned " + found
                        + ", expected " + expected[i]);
            }
            int predicted = checkedPrediction(index, asked[i], n);
            if (expected[i] < n && keys[expected[i]] == asked[i] && Math.abs(predicted - expected[i]) > maxErr) {
                throw new IllegalStateException("predict(" + asked[i] + ") returned " + predicted
                        + ", off by " + Math.abs(predicted - expected[i]) + " > maxError() = " + maxErr);
            }
            if (exactSearch && predicted != Math.min(expected[i], n - 1)) {
                throw new IllegalStateException("predict(" + asked[i] + ") returned " + predicted
                        + ", expected " + Math.min(expected[i], n - 1) + " for a search index");
            }
        }
    }

    private static void verifyMissingKeys(Index index, int[] keys, int[] queries, boolean exactSearch) {
        verifyQuery(index, keys, Integer.MIN_VALUE, exactSearch);
        verifyQuery(index, keys, Integer.MAX_VALUE, exactSearch);
        for (int key : queries) {
            if (key > Integer.MIN_VALUE) {
                verifyQuery(index, keys, key - 1, exactSearch);
            }
            if (key < Integer.MAX_VALUE) {
                verifyQuery(index, keys, key + 1, exactSearch);
            }
        }
    }

    private static void verifyQuery(Index index, int[] keys, int key, boolean exactSearch) {
        int expected = Sorted.lowerBound(keys, 0, keys.length, key);
        int found = index.lower_bound(key);
        if (found != expected) {
            throw new IllegalStateException("lower_bound(" + key + ") returned " + found + ", expected " + expected);
        }
        int predicted = checkedPrediction(index, key, keys.length);
        int expectedPrediction = Math.min(expected, keys.length - 1);
        if (exactSearch && predicted != expectedPrediction) {
            throw new IllegalStateException("predict(" + key + ") returned " + predicted
                    + ", expected " + expectedPrediction + " for a search index");
        }
    }

    private static int checkedPrediction(Index index, int key, int n) {
        int predicted = index.predict(key);
        if (predicted < 0 || predicted >= n) {
            throw new IllegalStateException("predict(" + key + ") returned " + predicted
                    + ", outside [0, " + (n - 1) + "]");
        }
        return predicted;
    }

    private static int[] predictAll(Index index, int[] keys) {
        int[] predicted = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            predicted[i] = index.predict(keys[i]);
        }
        return predicted;
    }
}
