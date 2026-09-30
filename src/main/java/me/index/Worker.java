package me.index;

import me.index.config.Dataset;
import me.index.config.IndexSpec;
import me.index.config.Plan;
import me.index.config.Run;
import me.index.libs.math.Sorted;
import me.index.task.Index;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

public final class Worker {
    static final int QUERIES = 10_000;
    static final int WARMUP_ROUNDS = 20;
    static final int MEASURED_ROUNDS = 10;

    private Worker() {
    }

    static void main(String[] args) throws IOException {
        if (args.length != 6) {
            System.err.println("usage: Worker <config> <sosd-data-dir> <run> <row> <result> <predictions>");
            System.exit(2);
        }
        Run run = Plan.load(Path.of(args[0])).runs().get(Integer.parseInt(args[2]));
        IndexSpec spec = run.indexes().get(Integer.parseInt(args[3]));
        Path result = Path.of(args[4]);
        Path predictions = Path.of(args[5]);

        int[] keys = new Context(run.dataset(), Path.of(args[1])).intKeys();
        Queries queries = queries(run.dataset(), keys);
        Row.started(spec.label()).write(result);
        measure(spec, keys, queries, predictions).write(result);
    }

    private record Queries(int[] keys, int[] expected) {
    }

    private record Timing(double bestNs, double meanNs, long checksum) {
    }

    private static Queries queries(Dataset dataset, int[] keys) {
        int[] asked = new int[QUERIES];
        int[] expected = new int[QUERIES];
        Random rnd = new Random(dataset.seed());
        for (int i = 0; i < QUERIES; i++) {
            int pos = rnd.nextInt(keys.length);
            expected[i] = pos;
            asked[i] = keys[pos];
        }
        return new Queries(asked, expected);
    }

    private static Row measure(IndexSpec spec, int[] keys, Queries queries, Path predictions) throws IOException {
        String id = spec.label();
        try {
            Index index = spec.create();
            long startedAt = System.nanoTime();
            index.build(keys);
            double buildMs = (System.nanoTime() - startedAt) / 1e6;
            id = index.id() + spec.suffix();
            int maxErr = index.maxError();
            long bytes = index.sizeInBytes();

            verify(index, queries, maxErr, keys.length);
            Timing timing = time(index, queries.keys());
            verifyMissingKeys(index, keys, queries.keys());

            Row row = new Row(id, buildMs, maxErr, bytes, timing.bestNs(), timing.meanNs(), timing.checksum(),
                    null, null);
            if (row.plotted()) {
                Row.writePredictions(predictions, predictAll(index, keys));
            }
            return row;
        } catch (UnsupportedOperationException e) {
            return Row.skipped(id, "not implemented");
        } catch (RuntimeException | Error e) {
            return Row.skipped(id, "failed: " + e);
        }
    }

    private static Timing time(Index index, int[] queries) {
        long checksum = 0;
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            checksum += round(index, queries);
        }

        long best = Long.MAX_VALUE;
        long total = 0;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long startedAt = System.nanoTime();
            checksum += round(index, queries);
            long elapsed = System.nanoTime() - startedAt;
            best = Math.min(best, elapsed);
            total += elapsed;
        }
        return new Timing((double) best / QUERIES, (double) total / MEASURED_ROUNDS / QUERIES, checksum);
    }

    private static long round(Index index, int[] queries) {
        long sum = 0;
        for (int i = 0; i < queries.length; i++) {
            sum += index.lower_bound(queries[i]);
        }
        return sum;
    }

    private static void verify(Index index, Queries queries, int maxErr, int n) {
        int[] asked = queries.keys();
        int[] expected = queries.expected();
        for (int i = 0; i < asked.length; i++) {
            int found = index.lower_bound(asked[i]);
            if (found != expected[i]) {
                throw new IllegalStateException("lower_bound(" + asked[i] + ") returned " + found
                        + ", expected " + expected[i]);
            }
            int predicted = index.predict(asked[i]);
            if (predicted < 0 || predicted >= n) {
                throw new IllegalStateException("predict(" + asked[i] + ") returned " + predicted
                        + ", outside [0, " + (n - 1) + "]");
            }
            if (Math.abs(predicted - expected[i]) > maxErr) {
                throw new IllegalStateException("predict(" + asked[i] + ") returned " + predicted
                        + ", off by " + Math.abs(predicted - expected[i]) + " > maxError() = " + maxErr);
            }
        }
    }

    private static void verifyMissingKeys(Index index, int[] keys, int[] queries) {
        verifyLowerBound(index, keys, Integer.MIN_VALUE);
        verifyLowerBound(index, keys, Integer.MAX_VALUE);
        for (int key : queries) {
            if (key > Integer.MIN_VALUE) {
                verifyLowerBound(index, keys, key - 1);
            }
            if (key < Integer.MAX_VALUE) {
                verifyLowerBound(index, keys, key + 1);
            }
        }
    }

    private static void verifyLowerBound(Index index, int[] keys, int key) {
        int expected = Sorted.lowerBound(keys, 0, keys.length, key);
        int found = index.lower_bound(key);
        if (found != expected) {
            throw new IllegalStateException("lower_bound(" + key + ") returned " + found + ", expected " + expected);
        }
    }

    private static int[] predictAll(Index index, int[] keys) {
        int[] predicted = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            predicted[i] = index.predict(keys[i]);
        }
        return predicted;
    }
}
