package me.index;

import me.index.config.Config;
import me.index.config.parameters.DataParams;
import me.index.config.parameters.IndexParams;
import me.index.config.parameters.NeuralNetworkParams;
import me.index.config.parameters.PiecewiseLinearParams;
import me.index.config.parameters.SearchParams;
import me.index.config.parameters.enums.DataSize;
import me.index.config.parameters.enums.IndexType;
import me.index.config.parameters.enums.Keyset;
import me.index.libs.execute.parameters.BenchmarkArguments;
import me.index.libs.execute.parameters.BenchmarkQueries;
import me.index.libs.execute.JvmRunner;
import me.index.libs.execute.parameters.BenchmarkResult;
import me.index.libs.execute.parameters.BenchmarkSummary;
import me.index.libs.io.CntOutputStream;
import me.index.libs.io.SosdReader;
import me.index.libs.math.Frac128;
import me.index.libs.math.Int128;
import me.index.libs.math.Maths;
import me.index.libs.math.Sorted;
import me.index.libs.math.KeyGenerator;
import me.index.libs.view.pdf.Document;
import me.index.libs.view.pdf.Font;
import me.index.libs.view.pdf.Page;
import me.index.libs.view.Plot;
import me.index.libs.view.PdfReport;
import me.index.libs.view.StringReport;
import me.index.task.Index;
import me.index.task.inet.Init;
import me.index.task.inet.Models;
import me.index.task.inet.NetIndex;
import me.index.task.inet.loss.AbsoluteLoss;
import me.index.task.inet.loss.HuberLoss;
import me.index.task.inet.loss.Loss;
import me.index.task.inet.loss.SquaredLoss;
import me.index.task.inet.model.Model;
import me.index.task.isearch.Searches;
import me.index.task.isegment.CHT;
import me.index.task.isegment.PlaIndex;
import me.index.task.isegment.Window;
import me.index.task.isegment.Windows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
final class AllTests {
    private static final BigInteger MOD = BigInteger.ONE.shiftLeft(128);
    private static volatile double allocationSink;

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(Searches.class)
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void searchesFindInsertionPoints(Searches type) {
        for (int[] keys : datasets()) {
            Index index = type.create();
            String id = index.id();
            index.build(keys);
            assertEquals(id, index.id(), "id must not depend on build");
            assertEquals(0, index.maxError(), "search error");
            assertEquals(0, index.sizeInBytes(), "search size");
            assertIndex(index, keys, true);
        }
        for (int[] bad : new int[][]{{}, {1}, {1, 1}, {2, 1}}) {
            assertThrows(IllegalArgumentException.class, () -> type.create().build(bad));
        }
    }

    @Test
    void sortedSearchHandlesAllWindowsAndSubranges() {
        // Every possible search window, including an incorrect prediction:
        // exercise both fallbacks and their exclusive boundaries independently.
        int[] keys = {-20, -5, 0, 2, 30};
        for (int p = 0; p < keys.length; p++) {
            for (int error : new int[]{0, 1, 2, 4, 8, 16, 31, 32, 33, Integer.MAX_VALUE}) {
                for (int key = -25; key <= 35; key++) {
                    assertEquals(lowerBound(keys, key), Sorted.lowerBoundNear(keys, key, p, error),
                            "near search p=" + p + " e=" + error + " key=" + key);
                }
            }
        }
        for (int from = 0; from <= keys.length; from++) {
            for (int to = from; to <= keys.length; to++) {
                for (int key = -25; key <= 35; key++) {
                    int expected = from;
                    while (expected < to && keys[expected] < key) expected++;
                    assertEquals(expected, Sorted.lowerBound(keys, from, to, key));
                    for (int p : new int[]{Integer.MIN_VALUE, from, (from + to) / 2, to, Integer.MAX_VALUE}) {
                        for (int error : new int[]{0, 1, 31, 32, Integer.MAX_VALUE}) {
                            assertEquals(expected, Sorted.lowerBoundNear(keys, from, to, key, p, error),
                                    "bounded search from=" + from + " to=" + to + " p=" + p
                                            + " e=" + error + " key=" + key);
                        }
                    }
                }
            }
        }
        for (int predicted : new int[]{Integer.MIN_VALUE, -1, keys.length, Integer.MAX_VALUE}) {
            for (int error : new int[]{0, 1, Integer.MAX_VALUE}) {
                for (int key = -25; key <= 35; key++) {
                    assertEquals(lowerBound(keys, key), Sorted.lowerBoundNear(keys, key, predicted, error));
                }
            }
        }
        assertEquals(0, Sorted.lowerBoundNear(new int[0], 7, 0, 0));
        int[] wide = new int[201];
        Arrays.setAll(wide, i -> 4 * i - 400);
        for (int error : new int[]{0, 1, 16, 31, 32, 33, Integer.MAX_VALUE}) {
            for (int key = -405; key <= 405; key++) {
                int expected = lowerBound(wide, key);
                assertEquals(expected, Sorted.lowerBoundNear(wide, key, 100, error),
                        "wide search e=" + error + " key=" + key);
                assertEquals(Math.clamp(expected, 10, 190),
                        Sorted.lowerBoundNear(wide, 10, 190, key, 100, error));
            }
        }
    }

    @ParameterizedTest(name = "segmentation eps={0}")
    @ValueSource(ints = {0, 1, 2, 4, 8, 16, 32})
    void segmentsRespectExactGeometryAndLookup(int eps) {
        List<int[]> datasets = new ArrayList<>(datasets());
        // Exhaust all nontrivial subsets of nine keys: varied short partitions.
        for (int mask = 0; mask < 1 << 9; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            int[] keys = new int[Integer.bitCount(mask)];
            int next = 0;
            for (int i = 0; i < 9; i++) if ((mask & (1 << i)) != 0) keys[next++] = i * 4 - 16;
            datasets.add(keys);
        }
        for (int[] keys : datasets) {
            int count = checkCht(keys, eps);
            if (keys.length <= 12)
                assertEquals(optimalCount(keys, eps), count, "minimum CHT segment count");
            for (Windows variant : Windows.values()) {
                if (variant != Windows.CONVEX) checkGreedy(keys, eps, variant);
                PlaIndex index = new PlaIndex(variant, eps);
                index.build(keys);
                assertIndex(index, keys, false);
                int measured = 0;
                for (int i = 0; i < keys.length; i++)
                    measured = Math.max(measured, Math.abs(index.predict(keys[i]) - i));
                assertEquals(measured, index.maxError(), "actual rounded error must be reported");
                List<Integer> ends = new ArrayList<>();
                Window.split(variant.window(), keys, eps, (start, end, a, b) -> ends.add(end));
                int[] radix = (int[]) field(index, "radix");
                long radixBytes = radix == null ? 0 : (long) radix.length * Integer.BYTES;
                assertEquals(28L * ends.size() + radixBytes, index.sizeInBytes(), "PLA payload size");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Windows.class)
    void radixLookupHandlesBucketBoundariesGapsAndRebuilds(Windows variant) {
        Random random = new Random(581);
        int[] compact = new int[2048];
        int[] clustered = new int[2048];
        int[] twoClusters = new int[2048];
        for (int i = 1; i < compact.length; i++) compact[i] = compact[i - 1] + 1 + random.nextInt(128);
        for (int i = 0; i < clustered.length; i++) {
            clustered[i] = Integer.MIN_VALUE + i * i;
            int j = i % 1024;
            twoClusters[i] = (i < 1024 ? -1_000_000_000 : 1_000_000_000) + j * j;
        }
        clustered[clustered.length - 1] = Integer.MAX_VALUE;
        int[] scattered = random.ints(2048).distinct().sorted().toArray();
        boolean usedRadix = false;
        for (int eps : new int[]{0, 1, 2, 4, 8, 16, 32}) {
            PlaIndex index = new PlaIndex(variant, eps);
            for (int[] keys : new int[][]{compact, clustered, twoClusters, scattered}) {
                index.build(keys);
                String context = variant + " eps=" + eps;
                for (int i = 0; i < keys.length; i++) {
                    query(index, keys, keys[i], false, context);
                    assertTrue(Math.abs((long) index.predict(keys[i]) - i) <= index.maxError(), context);
                    if (keys[i] > Integer.MIN_VALUE) query(index, keys, keys[i] - 1, false, context);
                    if (keys[i] < Integer.MAX_VALUE) query(index, keys, keys[i] + 1, false, context);
                }
                query(index, keys, Integer.MIN_VALUE, false, context);
                query(index, keys, Integer.MAX_VALUE, false, context);
                int[] radix = (int[]) field(index, "radix");
                if (keys == clustered) assertNull(radix, "one outlier must not force an ineffective table");
                if (radix != null) {
                    usedRadix = true;
                    assertTrue(radix.length <= (1 << 14) + 1, "bounded table size");
                    int shift = (int) field(index, "radixShift");
                    for (int bucket = 0; bucket < radix.length; bucket++) {
                        long boundary = keys[0] + ((long) bucket << shift);
                        for (int offset = -1; offset <= 1; offset++) {
                            long key = boundary + offset;
                            if (key >= Integer.MIN_VALUE && key <= Integer.MAX_VALUE)
                                query(index, keys, (int) key, false, context);
                        }
                    }
                }
                index.build(new int[]{-1, 1});
                assertNull(field(index, "radix"), "rebuilding a small index releases the table");
                assertIndex(index, new int[]{-1, 1}, false);
            }
        }
        assertTrue(usedRadix, "exercise accelerated lookup");
        int[] many = new int[40_000];
        for (int i = 0; i < many.length; i++) many[i] = 3 * i + (i & 1);
        PlaIndex index = new PlaIndex(variant, 0);
        index.build(many);
        assertTrue(((int[]) field(index, "maxKeys")).length > 1 << 14);
        assertTrue(((int[]) field(index, "radix")).length <= (1 << 14) + 1);
        for (int i = 0; i < many.length; i += 257) {
            query(index, many, many[i], false, "capped table");
            query(index, many, many[i] + 1, false, "capped table gap");
        }
    }

    @Test
    void segmentErrorsAreMeasuredSeparately() {
        int[] keys = {0, 49, 100, 101, 102, 200, 201, 202};
        for (Windows variant : Windows.values()) {
            PlaIndex index = new PlaIndex(variant, 0);
            index.build(keys);
            int[] starts = (int[]) field(index, "starts");
            int[] errors = (int[]) field(index, "errors");
            int smallest = Integer.MAX_VALUE;
            int largest = 0;
            for (int s = 0; s < starts.length; s++) {
                int start = starts[s];
                int end = s + 1 < starts.length ? starts[s + 1] : keys.length;
                int measured = 0;
                for (int i = start; i < end; i++) {
                    measured = Math.max(measured, Math.abs(index.predict(keys[i]) - i));
                }
                int error = errors[s];
                assertEquals(measured, error, variant + " segment " + s);
                smallest = Math.min(smallest, error);
                largest = Math.max(largest, error);
            }
            assertEquals(0, smallest, "some segments predict exactly");
            assertEquals(1, largest, "rounding affects only some segments");
            assertEquals(largest, index.maxError());
            for (int key = -1; key <= 203; key++) {
                assertEquals(lowerBound(keys, key), index.lower_bound(key), variant + " key=" + key);
            }
            assertIndex(index, keys, false);
        }
    }

    @Test
    void segmentRoundingPreservesLookupAndGreedyBoundaries() {
        int[] keys = {0, 49};
        PlaIndex index = new PlaIndex(Windows.CONVEX, 0);
        index.build(keys);
        assertEquals(1, index.maxError(), "1/49 rounded to double undershoots rank 1");
        assertEquals(1, index.lower_bound(49), "rounding must not break exact lookup");
        checkCht(keys, 0); // Exact rational certificate still has zero error.
        List<Integer> ends = new ArrayList<>();
        Window.split(Windows.SPLINE.window(), new int[]{0, 10, 20, 30, 40}, 0,
                (start, end, a, b) -> ends.add(end));
        assertEquals(List.of(2, 4, 5), ends, "strict RSpline boundary and singleton tail");
        ends.clear();
        Window.split(Windows.GREEDY.window(), new int[]{0, 10, 30, 31, 32}, 1,
                (start, end, a, b) -> ends.add(end));
        assertEquals(List.of(5), ends, "GreedyWindow fixed slope");
        ends.clear();
        Window.split(Windows.SPLINE.window(), new int[]{0, 10, 30, 31, 32}, 1,
                (start, end, a, b) -> ends.add(end));
        assertEquals(List.of(4, 5), ends, "RSpline need not outgrow GreedyWindow");
        IndexParams spec = new PiecewiseLinearParams(IndexType._pla_convex, me.index.config.parameters.enums.MaxErr._0);
        BenchmarkResult measured = Benchmark.measure(spec, keys, 42, false, () -> 0L);
        assertEquals(1, measured.summary().maxErr());
        assertTrue(Benchmark.warning(spec, measured.summary()).contains("exceeds requested 0"));
    }

    @Test
    void chtGrowsBothHullsAndSegmentStateDoesNotLeak() {
        for (int sign : new int[]{1, -1}) {
            int[] keys = new int[600];
            for (int i = 0; i < keys.length; i++) keys[i] = -1_000_000_000 + 10000 * i + sign * i * i;
            assertEquals(1, checkCht(keys, 32), "long almost-linear segment");
        }
        assertTrue(((long[]) field(CHT.INSTANCE, "LK")).length > 100, "lower hull grew");
        assertTrue(((long[]) field(CHT.INSTANCE, "UK")).length > 100, "upper hull grew");
        assertEquals(1, checkCht(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, 0), "reset after growth");
        for (Windows variant : Windows.values()) {
            int[] keys = {0, 10, 30, 31, 32};
            PlaIndex first = new PlaIndex(variant, 1);
            first.build(keys);
            PlaIndex second = new PlaIndex(variant, 0);
            second.build(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
            assertIndex(first, keys, false); // Building another index must not alter stored segments.
        }
    }

    @Test
    void segmentsRejectInvalidInputAndLongNegationOverflow() {
        CHT.INSTANCE.init_skip(Long.MIN_VALUE, 0);
        assertThrows(IllegalArgumentException.class,
                () -> CHT.INSTANCE.can_expand(Long.MIN_VALUE, 0, 0));
        // The same boundary in the supported int domain is safe after widening.
        assertEquals(1, checkCht(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1}, 0),
                "Integer.MIN_VALUE must remain supported");
        for (Windows variant : Windows.values()) {
            assertThrows(IllegalArgumentException.class, () -> new PlaIndex(variant, -1));
            for (int[] bad : new int[][]{{}, {1}, {2, 2}, {2, 1}}) {
                assertThrows(IllegalArgumentException.class, () -> new PlaIndex(variant, 1).build(bad));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new PlaIndex(null, 1));
        for (double[] bad : new double[][]{null, {}, {1}, {Double.NaN, 0}, {1, Double.POSITIVE_INFINITY}}) {
            Window window = new Window() {
                public int init_skip(long key, long err) {
                    return 1;
                }

                public boolean can_expand(long key, long pos, int err) {
                    return false;
                }

                public double[] get_result() {
                    return bad;
                }
            };
            assertThrows(IllegalStateException.class,
                    () -> Window.split(window, new int[]{1, 2}, 0, (s, e, a, b) -> {
                    }));
        }
        Window empty = new Window() {
            public int init_skip(long key, long err) {
                return 0;
            }

            public boolean can_expand(long key, long pos, int err) {
                return false;
            }

            public double[] get_result() {
                return new double[]{0, 0};
            }
        };
        assertThrows(IllegalStateException.class,
                () -> Window.split(empty, new int[]{1, 2}, 0, (s, e, a, b) -> {
                }));
    }

    @ParameterizedTest
    @EnumSource(Models.class)
    void networksUseCorrectActivationsAndLinearOutput(Models type) {
        Model net = type.create(new int[]{1, 2, 1}, 0.01, 1, new SquaredLoss(), new Random(1));
        double[][][] w = (double[][][]) field(net, "weights");
        double[][] b = (double[][]) field(net, "biases");
        w[0][0][0] = 2;
        w[0][1][0] = -3;
        w[1][0][0] = 0.5;
        w[1][0][1] = -0.4;
        b[0][0] = 0.2;
        b[0][1] = -0.1;
        b[1][0] = 0.7;
        for (double x : new double[]{-3, -0.2, 0, 0.3, 2}) {
            double expected = 0.7 + 0.5 * activation(type, 2 * x + 0.2)
                    - 0.4 * activation(type, -3 * x - 0.1);
            assertEquals(expected, net.predict(x), 1e-14, type + " explicit forward formula");
        }
    }

    @ParameterizedTest
    @EnumSource(Models.class)
    void networksShuffleEveryEpoch(Models type) {
        ShuffleProbe rnd = new ShuffleProbe();
        Model net = type.create(new int[]{1, 1}, 0.1, 1, new SquaredLoss(), rnd);
        double[] xs = {0, 1, 2}, ys = {1, 0, -1};
        net.train(xs, ys, 2);
        assertEquals(List.of(3, 2, 3, 2), rnd.bounds, "Fisher-Yates every epoch using supplied rnd");
        // Choosing j=0 yields [1,2,0], then [2,0,1]. Batch=1 makes order observable.
        double w = 0, b = 0;
        for (int i : new int[]{1, 2, 0, 2, 0, 1}) {
            double gradient = w * xs[i] + b - ys[i];
            w -= 0.1 * gradient * xs[i];
            b -= 0.1 * gradient;
        }
        assertEquals(b, net.predict(0), 1e-14, "shuffled update order");
        assertEquals(w + b, net.predict(1), 1e-14, "shuffled update order");
    }

    @ParameterizedTest
    @EnumSource(Models.class)
    void networksValidateInputsTrainAndIndexExtremeKeys(Models type) {
        for (int[] shape : new int[][]{{1, 1}, {1, 4, 1}, {1, 3, 2, 1}}) {
            Model a = type.create(shape, 0.03, 3, new SquaredLoss(), new Random(123));
            Model b = type.create(shape, 0.03, 3, new SquaredLoss(), new Random(123));
            long size = 0;
            for (int i = 1; i < shape.length; i++) size += (long) shape[i - 1] * shape[i] + shape[i];
            for (int n : shape) size += n;
            assertEquals(size * 8, a.sizeInBytes(), "inference payload size " + type);
            double[] xs = {-1, -0.3, 0.4, 1}, ys = {0, 0.35, 0.7, 1};
            for (double x : xs) assertEquals(a.predict(x), b.predict(x), 0, "seeded initialization");
            if (type != Models.SIGMOID) {
                double before = a.predict(0.3);
                assertEquals(mse(a, xs, ys), a.train(xs, ys, 0), 0, "zero epochs MSE");
                assertEquals(before, a.predict(0.3), 0, "zero epochs leaves weights unchanged");
            } else {
                double before = a.predict(0.3);
                assertTrue(Double.isNaN(a.train(xs, ys, 0)));
                assertEquals(before, a.predict(0.3), 0, "zero epochs leaves weights unchanged");
            }
            assertEquals(a.train(xs, ys, 20), b.train(xs, ys, 20), 0, "seeded training");
            for (double x : xs) assertEquals(a.predict(x), b.predict(x), 0, "reproducible prediction");
            assertTrue(Double.isNaN(a.train(new double[0], new double[0], 1)), "empty training");
            assertThrows(IllegalArgumentException.class, () -> a.train(xs, new double[0], 1));
            assertThrows(IllegalArgumentException.class, () -> a.train(xs, ys, -1));
            assertThrows(IllegalArgumentException.class, () -> a.train(null, ys, 1));
            assertThrows(IllegalArgumentException.class, () -> a.train(xs, null, 1));
        }
        // A simple problem must actually be learned, irrespective of activation.
        Model linear = type.create(new int[]{1, 1}, 0.1, 4, new SquaredLoss(), new Random(4));
        double[] xs = {-1, -0.5, 0.5, 1}, ys = {0, 0.25, 0.75, 1};
        double before = mse(linear, xs, ys);
        assertTrue(linear.train(xs, ys, 200) < before * 1e-6, "linear regression convergence " + type);
        for (int[] bad : new int[][]{null, {1, 0, 1}, {1, -2, 1}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> type.create(bad, 0.01, 2, new SquaredLoss(), new Random(1)));
        }
        if (type != Models.SIGMOID) {
            for (int batch : new int[]{0, -1})
                assertThrows(IllegalArgumentException.class,
                        () -> type.create(new int[]{1, 1}, 0.01, batch, new SquaredLoss(), new Random(1)));
        }
        for (int[] bad : new int[][]{{}, {1}, {2, 1}, {1, 2}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> type.create(bad, 0.01, 2, new SquaredLoss(), new Random(1)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> type.create(new int[]{1, 1}, 0.01, 1, null, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> type.create(new int[]{1, 1}, 0.01, 1, new SquaredLoss(), null));
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> type.create(new int[]{1, 1}, rate, 1, new SquaredLoss(), new Random(1)));
        }
        List<int[]> keysets = new ArrayList<>(List.of(new int[]{Integer.MIN_VALUE, -7, 0, 100, Integer.MAX_VALUE},
                new int[]{-3, -2, -1}, new int[]{Integer.MAX_VALUE - 2, Integer.MAX_VALUE}));
        List<int[]> generated = datasets();
        for (int i = 0; i < generated.size(); i += 20) keysets.add(generated.get(i));
        for (int[] keys : keysets) {
            for (Loss loss : losses()) {
                NetIndex index = new NetIndex(type.create(new int[]{1, 4, 1}, 0.01, 3, loss, new Random(8)), 5);
                String id = index.id();
                index.build(keys);
                assertEquals("net_" + type.name().toLowerCase(Locale.ROOT) + "/" + loss.id(), id);
                assertEquals(id, index.id());
                assertIndex(index, keys, false);
            }
        }
    }

    @Test
    void lossValuesAndGradientsMatchTheirDefinitions() {
        for (Loss loss : losses()) {
            for (double error : new double[]{-2, -0.2, -0.05, 0, 0.05, 0.2, 2}) {
                double value = loss.value(error, 0), grad = loss.gradient(error, 0);
                double expectedValue = loss instanceof SquaredLoss ? error * error / 2
                        : loss instanceof AbsoluteLoss ? Math.abs(error)
                        : Math.abs(error) <= 0.2 ? error * error / 2 : 0.2 * (Math.abs(error) - 0.1);
                double expectedGrad = loss instanceof SquaredLoss ? error
                        : loss instanceof AbsoluteLoss ? Math.signum(error) : Math.max(-0.2, Math.min(0.2, error));
                assertEquals(expectedValue, value, 1e-15, "loss value " + loss.id());
                assertEquals(expectedGrad, grad, 1e-15, "loss gradient " + loss.id());
                double numeric = (loss.value(error + 1e-6, 0) - loss.value(error - 1e-6, 0)) / 2e-6;
                assertEquals(numeric, grad, 1e-6, "numerical loss derivative");
            }
        }
        for (double delta : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new HuberLoss(delta));
        }
        for (double delta : new double[]{Double.MIN_NORMAL, 1e-6, 0.02, 1, 100}) {
            HuberLoss loss = new HuberLoss(delta);
            assertEquals(delta, loss.gradient(2 * delta, 0), 0, "Huber saturation");
        }
        assertEquals(Math.sqrt(2.0 / 3), Init.HE.scale(3, 7), 0, "HE");
        assertEquals(Math.sqrt(2.0 / 10), Init.XAVIER.scale(3, 7), 0, "Xavier");
    }

    @Test
    void neuralIndexNormalizesClampsAndRejectsNaN() {
        ProbeModel model = new ProbeModel();
        NetIndex index = new NetIndex(model, 1);
        int[] keys = {Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
        index.build(keys);
        assertEquals(-1, model.seenX[0], 0, "normalized min");
        assertEquals(1, model.seenX[2], 0, "normalized max");
        assertEquals(0.5, model.seenY[1], 0, "normalized target");
        for (double output : new double[]{-Double.MAX_VALUE, Double.NEGATIVE_INFINITY, -1e20}) {
            model.output = output;
            assertEquals(0, index.predict(0), "clamp before int narrowing (negative)");
        }
        for (double output : new double[]{Double.MAX_VALUE, Double.POSITIVE_INFINITY, 1e20}) {
            model.output = output;
            assertEquals(2, index.predict(0), "clamp before int narrowing (positive)");
        }
        model.output = Double.NaN;
        assertThrows(IllegalStateException.class, () -> index.predict(0));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            model.trainingResult = invalid;
            assertThrows(IllegalStateException.class, () -> new NetIndex(model, 1).build(keys));
        }
        assertThrows(IllegalArgumentException.class, () -> new NetIndex(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new NetIndex(model, 0));
        for (int[] bad : new int[][]{{}, {1}, {1, 1}, {2, 1}}) {
            assertThrows(IllegalArgumentException.class, () -> new NetIndex(model, 1).build(bad));
        }
    }

    @ParameterizedTest
    @EnumSource(Models.class)
    void networkGradientsMatchFiniteDifferences(Models type) {
        for (Loss loss : losses()) gradients(type, loss);
    }

    @ParameterizedTest
    @EnumSource(Models.class)
    void miniBatchesAverageGradientsAndClearState(Models type) {
        for (Loss loss : losses()) {
            for (int batch : new int[]{1, 2, 3, 8}) batches(type, loss, batch);
        }
    }

    @Test
    void configurationExpandsAllIndexFactories() {
        Config config = Config.parse(base());
        assertEquals(1, config.dataParams().size(), "one dataset");
        assertEquals(28, config.indexParams().size(), "search + 7 eps x 3 PLA + 4 nets");
        for (IndexParams spec : config.indexParams()) {
            Index index = spec.createIndex();
            int[] keys = {-50, -10, 0, 10, 50};
            index.build(keys);
            assertIndex(index, keys, spec.type().isSearch());
        }
        Properties varied = base();
        varied.setProperty("net.layers", "none;2,3");
        varied.setProperty("net.train.loss", "_absolute;_huber(0.1)");
        varied.setProperty("data.seed", "1;2");
        varied.setProperty("net.train.rate", "0.01;0.02");
        varied.setProperty("net.train.epochs", "1;2");
        varied.setProperty("net.train.batch.size", "1;3");
        varied.setProperty("net.train.seed", "3;4");
        Config expanded = Config.parse(varied);
        assertEquals(2, expanded.dataParams().size(), "dataset Cartesian product");
        assertEquals(280, expanded.indexParams().size(), "network Cartesian product");
        assertEquals(280, expanded.indexParams().stream().map(IndexParams::label).distinct().count());
        for (IndexParams spec : expanded.indexParams()) {
            assertEquals(spec.label(), spec.createIndex().id() + spec.suffix());
        }
        varied.setProperty("net.train.loss", "_huber");
        NeuralNetworkParams huber = (NeuralNetworkParams) Config.parse(varied).indexParams().getLast();
        assertEquals(0.02, huber.loss().parameter());
        assertEquals(0.02, huber.loss().newLoss().gradient(1, 0));
        for (IndexType type : IndexType.values()) {
            if (!type.isSearch()) assertThrows(IllegalStateException.class, type::search);
            if (!type.isPla()) assertThrows(IllegalStateException.class, type::window);
            if (!type.isNet()) assertThrows(IllegalStateException.class, type::model);
        }
    }

    @Test
    void configurationPreservesDefaultsDatasetOrderAndPlotNames() {
        Properties defaults = new Properties();
        defaults.setProperty("data.keyset", "_gauss_int32");
        defaults.setProperty("data.size", "_1e4");
        defaults.setProperty("index.types", "_binary;_pla_convex;_net_softsign");
        Properties props = new Properties(defaults);
        Config config = Config.parse(props);
        assertTrue(props.isEmpty(), "parsing must not modify the supplied properties");
        assertEquals(List.of(new DataParams(Keyset._gauss_int32, DataSize._1e4, 42)), config.dataParams());
        assertEquals(List.of("binary", "pla_convex(err=32)", "net_softsign/squared"),
                config.indexParams().stream().map(IndexParams::label).toList());
        NeuralNetworkParams net = (NeuralNetworkParams) config.indexParams().getLast();
        assertEquals(List.of(4, 4), net.layers());
        assertEquals(100, net.epochs());
        assertEquals(32, net.batchSize());
        assertEquals(42, net.seed());
        assertEquals(0.05, net.rate());
        assertEquals(Path.of("out"), config.outDir());
        assertEquals(Path.of("out/gauss_int32_1e4.pdf"), config.plotPath(config.dataParams().getFirst()));
        assertThrows(UnsupportedOperationException.class, () -> config.dataParams().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.indexParams().clear());

        props.setProperty("data.keyset", "_gauss_int32;_log_norm_int32");
        props.setProperty("data.size", "_1e4;_1e5");
        props.setProperty("data.seed", "-1;2");
        props.setProperty("out.path", tempDir.resolve("plots with spaces").toString());
        props.setProperty("net.train.loss", "_absolute;_huber(0.1)");
        Config expanded = Config.parse(props);
        List<String> names = List.of("gauss_int32_1e4_seed-1.pdf", "gauss_int32_1e4_seed2.pdf",
                "gauss_int32_1e5_seed-1.pdf", "gauss_int32_1e5_seed2.pdf",
                "log_norm_int32_1e4_seed-1.pdf", "log_norm_int32_1e4_seed2.pdf",
                "log_norm_int32_1e5_seed-1.pdf", "log_norm_int32_1e5_seed2.pdf");
        assertEquals(names.stream().map(name -> tempDir.resolve("plots with spaces").resolve(name)).toList(),
                expanded.dataParams().stream().map(expanded::plotPath).toList());
        assertEquals(List.of(0.005, 0.5), expanded.indexParams().stream()
                .filter(NeuralNetworkParams.class::isInstance)
                .map(NeuralNetworkParams.class::cast).map(NeuralNetworkParams::rate).toList());
        assertEquals(1, config.dataParams().size(), "later property changes must not affect parsed configuration");

        props.setProperty("data.seed", "2;2");
        Config repeated = Config.parse(props);
        assertEquals(8, repeated.dataParams().size(), "repeated values must retain their positions");
        assertEquals(repeated.dataParams().get(0), repeated.dataParams().get(1));
        assertEquals("gauss_int32_1e4_seed2.pdf",
                repeated.plotPath(repeated.dataParams().getFirst()).getFileName().toString());
        props.setProperty("typo", "1");
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> Config.parse(props)).getMessage().contains("typo"));
    }

    @Test
    void configurationRejectsInvalidValues() throws IOException {
        for (String[] invalid : new String[][]{{"typo", "1"}, {"index.types", "_unknown"},
                {"index.err", "_3"}, {"net.train.rate", "NaN"}, {"net.train.batch.size", "0"},
                {"net.layers", "4,0"}, {"net.train.loss", "_huber(-1)"}, {"net.train.loss", "_squared(2)"},
                {"net.train.loss", "_huber(0.1"}, {"net.train.loss", "_huber()"},
                {"net.train.epochs", "-1"}, {"net.layers", "1,"}, {"net.train.seed", "abc"},
                {"net.train.rate", "Infinity"}, {"data.seed", "1;;2"}, {"out.path", "a;b"},
                {"data.size", "_max"}}) {
            Properties props = base();
            props.setProperty(invalid[0], invalid[1]);
            assertThrows(IllegalArgumentException.class, () -> Config.parse(props));
        }
        for (String required : List.of("data.keyset", "data.size", "index.types")) {
            Properties props = base();
            props.remove(required);
            assertThrows(IllegalArgumentException.class, () -> Config.parse(props));
            props.setProperty(required, " ");
            assertThrows(IllegalArgumentException.class, () -> Config.parse(props));
        }
        Path file = tempDir.resolve("bad.properties");
        assertThrows(IOException.class, () -> Config.read(file));
        Files.writeString(file, "data.keyset=_unknown\n");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Config.read(file))
                .getMessage().contains(file.toString()));
    }

    @ParameterizedTest
    @EnumSource(value = Keyset.class, names = ".*uint.*", mode = EnumSource.Mode.MATCH_ALL)
    void sosdTranslatesUnsignedBoundariesAndDeduplicates(Keyset keyset) throws IOException {
        boolean wide = keyset.isLong;
        // Unsigned order, represented by raw bits for uint64. Include both
        // endpoints, neighbors of the sign boundary and repeated endpoints.
        long[] raw = wide
                ? new long[]{0, 0, 1, Long.MAX_VALUE - 1, Long.MAX_VALUE,
                Long.MIN_VALUE, Long.MIN_VALUE + 1, -2, -1, -1}
                : new long[]{0, 0, 1, 2147483646L, 2147483647L,
                2147483648L, 2147483649L, 4294967294L, 4294967295L, 4294967295L};
        long min = wide ? Long.MIN_VALUE : Integer.MIN_VALUE;
        long max = wide ? Long.MAX_VALUE : Integer.MAX_VALUE;
        long[] expected = {min, min + 1, -2, -1, 0, 1, max - 1, max};
        Path file = tempDir.resolve(keyset.fileName());
        ByteBuffer bytes = ByteBuffer.allocate(8 + raw.length * (wide ? 8 : 4))
                .order(ByteOrder.LITTLE_ENDIAN);
        bytes.putLong(raw.length);
        for (long key : raw) {
            if (wide) bytes.putLong(key);
            else bytes.putInt((int) key);
        }
        Files.write(file, bytes.array());
        // Successful decoding also verifies that the count header is not shifted.
        assertArrayEquals(expected, readKeys(file, raw.length, wide),
                keyset + " signed translation and deduplication");
        assertArrayEquals(Arrays.copyOf(expected, 4), readKeys(file, 4, wide),
                keyset + " limit counts distinct keys");
        Context context = Context.load(new DataParams(keyset, DataSize._1e4, 42), tempDir);
        assertArrayEquals(expected, context.keys(), keyset + " configured translation");
        if (wide) {
            // Loading long keys does not extend the int-only Index API.
            assertThrows(IllegalStateException.class, context::intKeys);
        } else {
            int[] keys = context.intKeys();
            assertArrayEquals(Arrays.stream(expected).mapToInt(Math::toIntExact).toArray(), keys,
                    "uint32 full range fits signed int");
            for (IndexParams spec : Config.parse(base()).indexParams()) {
                Index index = spec.createIndex();
                index.build(keys);
                assertIndex(index, keys, spec.type().isSearch());
            }
        }
    }

    @Test
    void int128ArithmeticMatchesBigIntegerIncludingOverflow() {
        List<Int128> values = new ArrayList<>(List.of(Maths.ZERO, Maths.ONE, Maths.U64_MAX,
                Maths.TWO64, Maths.NEG_TWO64, new Int128(-1, -1), new Int128(0, Long.MIN_VALUE),
                new Int128(Long.MIN_VALUE, 0), new Int128(Long.MAX_VALUE, -1)));
        Random random = new Random(128);
        for (int i = 0; i < 5000; i++) values.add(new Int128(random.nextLong(), random.nextLong()));
        for (int i = 0; i < values.size(); i++) {
            Int128 x = values.get(i);
            BigInteger a = integer(x);
            assertEquals(a.signum() < 0, Maths.lessZero(x));
            assertEquals(a.signum() <= 0, Maths.lessOrEqZero(x));
            assertEquals(a.signum() == 0, Maths.eqZero(x));
            assertEquals(a.signum() > 0, Maths.greatZero(x));
            assertEquals(a.signum() >= 0, Maths.greatOrEqZero(x));
            assertEquals(a, new BigInteger(Maths.toByteArray(x)));
            assertEquals(new java.math.BigDecimal(a), Maths.toBigDecimal(x));
            assertInt128(a.negate(), Maths.neg(x));
            // Exhaust the boundary cross-product; pair each random value with the next.
            List<Int128> others = i < 9 ? values.subList(0, 9) : List.of(values.get((i + 1) % values.size()));
            for (Int128 y : others) {
                BigInteger b = integer(y);
                assertInt128(a.add(b), Maths.sum(x, y));
                assertInt128(a.subtract(b), Maths.sub(x, y));
                assertInt128(a.multiply(b), Maths.mul(x, y));
                assertInt128(a.multiply(BigInteger.valueOf(y.lo())), Maths.mul(x, y.lo()));
                assertInt128(BigInteger.valueOf(x.lo()).subtract(BigInteger.valueOf(y.lo())),
                        Maths.sub(x.lo(), y.lo()));
            }
        }
    }

    @Test
    void fractionsNormalizeCompareAndConvertWithoutIntermediateOverflow() {
        Random random = new Random(64);
        for (int i = 0; i < 1000; i++) {
            // Cross-products fit in signed 128 bits, as required by Maths.diff.
            long a = random.nextLong(), b = 1 + random.nextLong(Long.MAX_VALUE);
            long c = i % 10 == 0 ? a : random.nextLong();
            long d = i % 10 == 0 ? b : 1 + random.nextLong(Long.MAX_VALUE);
            Frac128 x = new Frac128(Maths.sub(a, 0), Maths.sub(b, 0));
            Frac128 y = new Frac128(Maths.sub(-c, 0), Maths.sub(-d, 0));
            BigInteger difference = BigInteger.valueOf(a).multiply(BigInteger.valueOf(d))
                    .subtract(BigInteger.valueOf(c).multiply(BigInteger.valueOf(b)));
            int sign = difference.signum();
            assertEquals(sign < 0, Maths.less(x, y));
            assertEquals(sign <= 0, Maths.lessEq(x, y));
            assertEquals(sign == 0, Maths.eq(x, y));
            assertEquals(sign > 0, Maths.great(x, y));
            assertEquals(sign >= 0, Maths.greatEq(x, y));
            assertEquals(difference, integer(Maths.diff(x, y)));
            assertEquals((double) a / b, Maths.toDouble(x), 2 * Math.ulp((double) a / b));
        }
        assertTrue(Maths.eq(new Frac128(Maths.ONE, Maths.sub(2, 0)),
                new Frac128(Maths.sub(2, 0), Maths.sub(4, 0))));
        assertEquals(-1, Maths.toDouble(new Frac128(Maths.ONE, Maths.neg(Maths.ONE))));
        Int128 min = new Int128(Long.MIN_VALUE, 0);
        assertEquals(-Math.scalb(1.0, 127), Maths.toDouble(new Frac128(min, Maths.ONE)));
        assertThrows(IllegalArgumentException.class, () -> new Frac128(Maths.ONE, Maths.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Frac128(Maths.ONE, min));
        assertThrows(IllegalArgumentException.class, () -> new Frac128(min, Maths.neg(Maths.ONE)));
        assertEquals(0, Maths.toDouble(Maths.F_ZERO));
        assertTrue(Maths.less(Maths.INF_NEG, Maths.F_ZERO));
        assertTrue(Maths.great(Maths.INF_POS, Maths.F_ZERO));
    }

    @Test
    void vectorAndMatrixOperationsHaveCorrectValuesAndMutationSemantics() {
        double[] a = {2, -3}, b = {4, 5};
        double[][] matrix = {{1, 2}, {-3, 4}, {0, 5}};
        assertArrayEquals(new double[]{-4, -18, -15}, Maths.mul(matrix, a));
        assertArrayEquals(new double[]{-5, 5}, Maths.mulTransposed(matrix, new double[]{1, 2, -1}));
        assertArrayEquals(new double[]{6, 2}, Maths.add(a, b));
        assertArrayEquals(new double[]{8, -15}, Maths.hadamard(a, b));
        assertArrayEquals(new double[]{4, 9}, Maths.map(a, x -> x * x));
        assertArrayEquals(new double[][]{{8, 10}, {-12, -15}}, Maths.outer(a, b));
        assertArrayEquals(new double[]{2, -3}, a, "out-of-place operations must preserve inputs");
        assertArrayEquals(new double[]{4, 5}, b);
        assertArrayEquals(new double[][]{{1, 2}, {-3, 4}, {0, 5}}, matrix);
        Maths.subtractScaled(a, b, 0.5);
        assertArrayEquals(new double[]{0, -5.5}, a);
        Maths.subtractScaled(matrix, new double[][]{{2, 4}, {2, 4}, {2, 4}}, 0.5);
        assertArrayEquals(new double[][]{{0, 0}, {-4, 2}, {-1, 3}}, matrix);
        assertArrayEquals(new double[0], Maths.mul(new double[0][], new double[0]));
        assertArrayEquals(new double[0], Maths.mulTransposed(new double[0][], new double[0]));
        assertArrayEquals(new double[]{0, 0}, Maths.mul(new double[][]{{}, {}}, new double[0]));

        Random expected = new Random(42);
        double[][] gaussian = Maths.gaussian(2, 3, 0.25, new Random(42));
        for (double[] row : gaussian) {
            for (double value : row) assertEquals(expected.nextGaussian() * 0.25, value);
        }
        assertArrayEquals(new double[0][], Maths.gaussian(0, 3, 1, new Random(1)));
    }

    @Test
    void mathRejectsDimensionMismatchesAndClampsPredictedRanks() {
        for (double[][] ragged : new double[][][]{{{1, 2}, {3}}, {{1}, {2, 3}}}) {
            assertThrows(IllegalArgumentException.class, () -> Maths.mulTransposed(ragged, new double[]{1, 1}));
        }
        assertThrows(IllegalArgumentException.class, () -> Maths.mul(new double[][]{{1}}, new double[2]));
        assertThrows(IllegalArgumentException.class, () -> Maths.mulTransposed(new double[][]{{1}}, new double[2]));
        assertThrows(IllegalArgumentException.class, () -> Maths.add(new double[1], new double[2]));
        assertThrows(IllegalArgumentException.class, () -> Maths.hadamard(new double[1], new double[2]));
        assertThrows(IllegalArgumentException.class, () -> Maths.subtractScaled(new double[1], new double[2], 1));
        assertThrows(IllegalArgumentException.class,
                () -> Maths.subtractScaled(new double[1][1], new double[2][1], 1));
        assertThrows(IllegalArgumentException.class,
                () -> Maths.subtractScaled(new double[1][1], new double[1][2], 1));
        assertEquals(3, Maths.predict(0.5, 0.9, 5), "truncate, do not round");
        assertEquals(0, Maths.predict(1, 0, Long.MIN_VALUE));
        assertEquals(Integer.MAX_VALUE, Maths.predict(1, 0, Long.MAX_VALUE));
        assertEquals(0, Maths.predict(0, -0.5, 0));
    }

    @Test
    void syntheticKeysAreSortedDistinctBoundedAndReproducible() throws IOException {
        for (boolean wide : new boolean[]{false, true}) {
            for (int size : new int[]{-1, 0, 1, 1000}) {
                long[] gaussian = KeyGenerator.gaussian(size, wide, new Random(42));
                long[] lognormal = KeyGenerator.lognormal(size, wide, new Random(42));
                assertArrayEquals(gaussian, KeyGenerator.gaussian(size, wide, new Random(42)));
                assertArrayEquals(lognormal, KeyGenerator.lognormal(size, wide, new Random(42)));
                for (long[] keys : new long[][]{gaussian, lognormal}) {
                    assertTrue(keys.length <= Math.max(0, size));
                    assertEquals(size > 0, keys.length > 0);
                    for (int i = 1; i < keys.length; i++) assertTrue(keys[i - 1] < keys[i]);
                    for (long key : keys) {
                        assertTrue(wide || key >= Integer.MIN_VALUE && key <= Integer.MAX_VALUE);
                        if (keys == lognormal) assertTrue(key >= 1);
                    }
                }
            }
            assertArrayEquals(new long[]{0}, KeyGenerator.gaussian(10, wide, new OrderedRandom()));
            assertArrayEquals(new long[]{1}, KeyGenerator.lognormal(10, wide, new OrderedRandom()));
            for (double outlier : new double[]{-100, 100}) {
                Random fixed = new Random(1) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public double nextGaussian() {
                        return outlier;
                    }
                };
                long[] gaussian = KeyGenerator.gaussian(3, wide, fixed);
                long[] lognormal = KeyGenerator.lognormal(3, wide, fixed);
                assertEquals(1, gaussian.length);
                assertEquals(1, lognormal.length);
                assertEquals(outlier > 0, gaussian[0] > 0);
                assertTrue(lognormal[0] >= 1);
                if (!wide) {
                    assertTrue(Math.abs(gaussian[0]) <= Integer.MAX_VALUE);
                    assertTrue(lognormal[0] <= Integer.MAX_VALUE);
                }
            }
        }
        for (Keyset keyset : Keyset.values()) {
            if (keyset.isSOSD()) continue;
            Context context = Context.load(new DataParams(keyset, DataSize._1e4, 42), tempDir);
            assertTrue(context.keys().length >= 2);
            if (!keyset.isLong) assertEquals(context.keys().length, context.intKeys().length);
            assertThrows(IllegalStateException.class, keyset::fileName);
        }
    }

    @Test
    void sosdRejectsMissingTruncatedAndUnsupportedFiles() throws IOException {
        Path file = tempDir.resolve(Keyset._books_200M_uint32.fileName());
        assertThrows(IOException.class, () -> readKeys(file, 2, false));
        assertThrows(IllegalArgumentException.class, () -> SosdReader.read(InputStream.nullInputStream(), 0, false));
        for (byte[] invalid : new byte[][]{new byte[3],
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(2).array(),
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(-1).array(),
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(1L << 31).array()}) {
            Files.write(file, invalid);
            for (boolean wide : new boolean[]{false, true}) {
                assertThrows(IOException.class, () -> readKeys(file, 2, wide));
            }
        }
        writeUint32(file);
        assertArrayEquals(new long[0], readKeys(file, 1, false));
        assertThrows(IllegalStateException.class,
                () -> Context.load(new DataParams(Keyset._books_200M_uint32, DataSize._1e4, 1), tempDir));
        writeUint32(file, 2, 1);
        assertThrows(IllegalStateException.class,
                () -> Context.load(new DataParams(Keyset._books_200M_uint32, DataSize._1e4, 1), tempDir));
    }

    @Test
    void contextClosesSosdFilesOnSuccessAndFailure() throws IOException {
        var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.UnixOperatingSystemMXBean);
        var unix = (com.sun.management.UnixOperatingSystemMXBean) bean;
        DataParams data = new DataParams(Keyset._books_200M_uint32, DataSize._1e4, 42);
        Path file = tempDir.resolve(data.keyset().fileName());
        writeUint32(file, 1, 2, 3);
        Context.load(data, tempDir);
        Files.write(file, new byte[]{0});
        assertThrows(IOException.class, () -> Context.load(data, tempDir));
        long before = unix.getOpenFileDescriptorCount();
        for (int i = 0; i < 32; i++) {
            writeUint32(file, 1, 2, 3);
            assertEquals(3, Context.load(data, tempDir).keys().length);
            Files.write(file, new byte[]{0});
            assertThrows(IOException.class, () -> Context.load(data, tempDir));
        }
        assertTrue(unix.getOpenFileDescriptorCount() <= before + 4, "SOSD file descriptors leaked");
    }

    @Test
    void measurementsRoundTripAndRejectCorruptResults() throws IOException {
        BenchmarkSummary row = new BenchmarkSummary("index", 1.5, 2, 80, 3.0, 5.0, 4.0, Long.MAX_VALUE, null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (BenchmarkResult value : List.of(new BenchmarkResult(row, new int[]{0, 1, 1}),
                new BenchmarkResult(row, null),
                new BenchmarkResult(BenchmarkSummary.skipped("index", "not implemented"), null),
                new BenchmarkResult(row.failed("test"), null))) {
            output.reset();
            value.write(output);
            BenchmarkResult actual = BenchmarkResult.read(new ByteArrayInputStream(output.toByteArray()), 3);
            assertEquals(value.summary(), actual.summary());
            assertArrayEquals(value.predictions(), actual.predictions());
        }
        output.reset();
        new BenchmarkResult(row, new int[]{0, 1, 1}).write(output);
        byte[] encoded = output.toByteArray();
        for (int length = 0; length < encoded.length; length++) {
            byte[] truncated = Arrays.copyOf(encoded, length);
            assertThrows(IOException.class, () -> BenchmarkResult.read(new ByteArrayInputStream(truncated), 3));
        }
        byte[] badHeader = encoded.clone();
        badHeader[0] ^= 1;
        assertThrows(IOException.class, () -> BenchmarkResult.read(new ByteArrayInputStream(badHeader), 3));
        assertThrows(IOException.class,
                () -> BenchmarkResult.read(new ByteArrayInputStream(Arrays.copyOf(encoded, encoded.length + 1)), 3));
        for (int count : new int[]{-2, Integer.MAX_VALUE}) {
            byte[] badCount = encoded.clone();
            ByteBuffer.wrap(badCount).putInt(encoded.length - 16, count);
            assertThrows(IOException.class, () -> BenchmarkResult.read(new ByteArrayInputStream(badCount), 3));
        }
        for (int position : new int[]{-1, 3}) {
            byte[] badPosition = encoded.clone();
            ByteBuffer.wrap(badPosition).putInt(encoded.length - 12, position);
            assertThrows(IOException.class, () -> BenchmarkResult.read(new ByteArrayInputStream(badPosition), 3));
        }
        assertThrows(IOException.class, () -> BenchmarkResult.read(new ByteArrayInputStream(encoded), 0));
        assertTrue(row.built());
        assertTrue(row.shouldPlot());
        assertFalse(new BenchmarkSummary("exact", 0, 0, 0, 0, 0, 0, 0, null).shouldPlot());
    }

    @Test
    void explicitArgumentsRoundTripAndRejectMissingDuplicateOrIrrelevantOptions() {
        Properties props = base();
        props.setProperty("net.layers", "none;2,3");
        props.setProperty("net.train.loss", "_absolute;_huber(0.1)");
        props.setProperty("net.train.seed", "-7;42");
        Config config = Config.parse(props);
        DataParams data = config.dataParams().getFirst();
        for (IndexParams index : config.indexParams()) {
            BenchmarkArguments job = new BenchmarkArguments(data, index, tempDir.resolve("data with spaces"),
                    tempDir.resolve("result file"), true);
            assertEquals(job, BenchmarkArguments.parse(job.arguments().toArray(String[]::new)));
            List<String> arguments = new ArrayList<>(job.arguments());
            for (int i = 0; i < arguments.size(); i += 2) {
                List<String> missing = new ArrayList<>(arguments);
                missing.subList(i, i + 2).clear();
                assertThrows(IllegalArgumentException.class,
                        () -> BenchmarkArguments.parse(missing.toArray(String[]::new)));
            }
            arguments.addAll(List.of("--index", "_binary"));
            assertThrows(IllegalArgumentException.class,
                    () -> BenchmarkArguments.parse(arguments.toArray(String[]::new)));
        }
        BenchmarkArguments search = new BenchmarkArguments(data, new SearchParams(IndexType._binary), tempDir,
                tempDir.resolve("r"), false);
        for (String[] extra : new String[][]{{"--eps", "1"}, {"--unknown", "1"}, {"--layers", "none"}}) {
            List<String> args = new ArrayList<>(search.arguments());
            args.addAll(List.of(extra));
            assertThrows(IllegalArgumentException.class, () -> BenchmarkArguments.parse(args.toArray(String[]::new)));
        }
        assertThrows(IllegalArgumentException.class, () -> BenchmarkArguments.parse(new String[]{"--index"}));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkArguments.parse(new String[]{"index", "_binary"}));
        BenchmarkArguments net =
                new BenchmarkArguments(data, config.indexParams().getLast(), tempDir, tempDir.resolve("r"), false);
        for (String[] invalid : new String[][]{{"--plot", "maybe"}, {"--learning-rate", "NaN"}, {"--epochs", "0"},
                {"--batch-size", "-1"}, {"--layers", "4,0"}, {"--model-seed", "abc"}, {"--loss-parameter", "-1"}}) {
            List<String> args = new ArrayList<>(net.arguments());
            args.set(args.indexOf(invalid[0]) + 1, invalid[1]);
            assertThrows(IllegalArgumentException.class, () -> BenchmarkArguments.parse(args.toArray(String[]::new)));
        }
    }

    @Test
    @Timeout(5)
    void queryBatchesSampleExistingKeysReproducibly() {
        int[] almostDense = new int[2000];
        for (int i = 0; i < almostDense.length; i++) almostDense[i] = i < 1000 ? i : i + 1;
        for (int[] keys : new int[][]{
                {Integer.MIN_VALUE, Integer.MAX_VALUE},
                {Integer.MIN_VALUE, Integer.MIN_VALUE + 1},
                {Integer.MAX_VALUE - 1, Integer.MAX_VALUE},
                {-2, -1, 0, 1, 2}, {0, 2}, {0, 1, 1000}, almostDense}) {
            for (int count : new int[]{1, 2, 1001, Benchmark.QUERIES}) {
                BenchmarkQueries batch = BenchmarkQueries.create(keys, count, 42);
                BenchmarkQueries repeated = BenchmarkQueries.create(keys, count, 42);
                assertArrayEquals(batch.keys(), repeated.keys());
                assertArrayEquals(batch.expected(), repeated.expected());
                assertEquals(count, batch.keys().length);
                assertEquals(count, batch.expected().length);
                for (int i = 0; i < count; i++) {
                    int key = batch.keys()[i];
                    assertEquals(lowerBound(keys, key), batch.expected()[i]);
                    assertEquals(Arrays.binarySearch(keys, key), batch.expected()[i]);
                    assertEquals(key, keys[batch.expected()[i]]);
                }
                if (count == Benchmark.QUERIES) {
                    assertFalse(Arrays.equals(batch.keys(), BenchmarkQueries.create(keys, count, 43).keys()));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BenchmarkQueries.create(new int[]{0, 1}, 0, 42));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkQueries.create(new int[]{0, 1}, -1, 42));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkQueries.create(new int[]{1, 0}, 2, 42));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkQueries.createBatches(new int[]{0, 1}, 10, 0, 42));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkQueries.createBatches(new int[]{0, 1}, 10, -1, 42));
        int[] uneven = {10, 12, 20};
        int rounds = Benchmark.WARMUP_ROUNDS + Benchmark.MEASURED_ROUNDS;
        List<BenchmarkQueries> batches = BenchmarkQueries.createBatches(uneven, 1000, rounds, 42);
        List<BenchmarkQueries> repeated = BenchmarkQueries.createBatches(uneven, 1000, rounds, 42);
        assertEquals(rounds, batches.size());
        assertArrayEquals(BenchmarkQueries.create(uneven, 1000, 42).keys(), batches.getFirst().keys());
        int[] frequencies = new int[21];
        for (int i = 0; i < batches.size(); i++) {
            BenchmarkQueries batch = batches.get(i);
            assertEquals(1000, batch.keys().length);
            assertEquals(1000, batch.expected().length);
            assertArrayEquals(batch.keys(), repeated.get(i).keys());
            assertArrayEquals(batch.expected(), repeated.get(i).expected());
            for (int j = 0; j < i; j++) assertFalse(Arrays.equals(batch.keys(), batches.get(j).keys()));
            for (int j = 0; j < batch.keys().length; j++) {
                int key = batch.keys()[j];
                int expected = lowerBound(uneven, key);
                assertEquals(expected, batch.expected()[j]);
                assertTrue(expected < uneven.length);
                assertEquals(key, uneven[expected]);
                frequencies[key]++;
            }
        }
        for (int key : uneven) {
            assertTrue(frequencies[key] > 8000 && frequencies[key] < 12000,
                    "existing keys must be sampled uniformly by position: " + key + " -> " + frequencies[key]);
        }
        BenchmarkResult sparse = Benchmark.measure(
                new PiecewiseLinearParams(IndexType._pla_convex, me.index.config.parameters.enums.MaxErr._0),
                new int[]{0, 100}, 42, false, () -> 0L);
        assertTrue(sparse.summary().built(), sparse.summary().note());
        assertEquals(0, sparse.summary().maxErr(), "the error bound applies to stored keys, not gaps");
    }

    @ParameterizedTest
    @CsvSource({"_gauss_int32,false", "_gauss_int32,true", "_books_200M_uint32,false", "_books_200M_uint32,true"})
    void childMeasurementsMatchInProcessWithOptionalPredictions(String keyset, boolean plot) throws Exception {
        writeUint32(tempDir.resolve(Keyset._books_200M_uint32.fileName()),
                0, 1, 2147483647L, 2147483648L, 4294967294L, 4294967295L);
        Properties props = base();
        props.setProperty("data.keyset", keyset);
        props.setProperty("index.types", "_binary;_pla_convex;_net_softsign");
        props.setProperty("index.err", "_4");
        Config config = Config.parse(props);
        DataParams data = config.dataParams().getFirst();
        int[] keys = Context.load(data, tempDir).intKeys();
        long checksum = expectedChecksum(keys, data.seed());
        for (int i = 0; i < config.indexParams().size(); i++) {
            IndexParams spec = config.indexParams().get(i);
            BenchmarkArguments args =
                    new BenchmarkArguments(data, spec, tempDir, tempDir.resolve("result-" + i + ".bin"), plot);
            assertEquals(0, runJava(Benchmark.class.getName(), args.arguments().toArray(String[]::new)));
            BenchmarkResult actual;
            try (InputStream input = Files.newInputStream(args.resultFile())) {
                actual = BenchmarkResult.read(input, keys.length);
            }
            BenchmarkResult expected = Benchmark.measure(spec, keys, data.seed(), plot, () -> 0L);
            BenchmarkSummary result = actual.summary();
            assertTrue(result.built(), result.note());
            assertEquals(checksum, result.checksum());
            assertEquals(expected.summary().id(), result.id());
            assertEquals(expected.summary().maxErr(), result.maxErr());
            assertEquals(expected.summary().bytes(), result.bytes());
            assertTrue(result.buildMs() >= 0 && result.minIterationNs() >= 0
                    && result.meanIterationNs() >= result.minIterationNs()
                    && result.maxIterationNs() >= result.meanIterationNs());
            assertArrayEquals(expected.predictions(), actual.predictions());
            assertEquals(plot && result.shouldPlot(), actual.predictions() != null);
            assertFalse(Files.exists(args.resultFile().resolveSibling(args.resultFile().getFileName() + ".part")));
        }
        assertFalse(Files.exists(tempDir.resolve("config.properties")));
    }

    @Test
    void benchmarkReportsBatchTimingsAndThroughputFromMeanTime() {
        long[] ticks = new long[2 + 2 * Benchmark.MEASURED_ROUNDS];
        ticks[1] = 2_000_000;
        for (int i = 0; i < Benchmark.MEASURED_ROUNDS; i++) {
            ticks[2 + 2 * i] = 3_000_000L + 20_000_000L * i;
            ticks[3 + 2 * i] = ticks[2 + 2 * i] + 1_000_000L * (i + 1);
        }
        int[] next = {0};
        BenchmarkSummary summary = Benchmark.measure(new SearchParams(IndexType._binary),
                new int[]{1, 3, 5}, 42, false, () -> ticks[next[0]++]).summary();
        assertTrue(summary.built(), summary.note());
        assertEquals(ticks.length, next[0]);
        assertEquals(2.0, summary.buildMs());
        assertEquals(1_000_000.0, summary.minIterationNs());
        assertEquals(10_000_000.0, summary.maxIterationNs());
        assertEquals(5_500_000.0, summary.meanIterationNs());
        assertArrayEquals(new String[]{"binary", "2.0", "0", "0.0", "1.000", "10.000", "5.500", "18181818"},
                StringReport.row(summary).strip().split("\\s+"));
        for (double mean : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            BenchmarkSummary invalid = new BenchmarkSummary("test", 0, 0, 0, 0, 0, mean, 0, null);
            assertTrue(StringReport.row(invalid).strip().endsWith("-"));
        }
        assertEquals("test failed", StringReport.row(BenchmarkSummary.skipped("test", "failed"))
                .strip().replaceAll("\\s+", " "));
    }

    @ParameterizedTest
    @CsvSource({"42,false,false", "42,true,false", "42;43,false,false", "42;43,true,false", "42,true,true"})
    void benchmarkPipelineWritesTableChecksumAndOptionalPdf(String seeds, boolean plot, boolean exactOnly)
            throws Exception {
        long[] raw = exactOnly ? new long[]{0, 1, 2, 3, 4, 5}
                : new long[]{0, 1, 2147483647L, 2147483648L, 4294967294L, 4294967295L};
        writeUint32(tempDir.resolve(Keyset._books_200M_uint32.fileName()), raw);
        Properties props = base();
        props.setProperty("data.keyset", "_books_200M_uint32");
        props.setProperty("data.seed", seeds);
        props.setProperty("index.types", exactOnly ? "_binary;_pla_convex" : "_binary;_pla_convex;_net_softsign");
        props.setProperty("index.err", exactOnly ? "_0" : "_4");
        props.setProperty("out.path", tempDir.resolve("out").toString());
        try (OutputStream out = Files.newOutputStream(tempDir.resolve("config.properties"))) {
            props.store(out, "full pipeline test");
        }
        String[] args = !plot ? new String[]{tempDir.toString()}
                : seeds.contains(";") ? new String[]{tempDir.toString(), "--plot"}
                : new String[]{"--plot", tempDir.toString()};
        assertEquals(0, runJava("me.index.Main", args));
        String log = Files.readString(tempDir.resolve("process.log"));
        assertTrue(log.contains("throughput/s"), log);
        for (String column : List.of("min iter ms", "max iter ms", "mean iter ms"))
            assertTrue(log.contains(column), log);
        assertTrue(log.contains("100000 per batch, 100% existing keys"), log);
        assertTrue(log.contains("a fresh pregenerated batch for every warmup and measured round"), log);
        assertFalse(log.contains("failed"), log);
        assertFalse(log.contains("not implemented"), log);
        for (String name : exactOnly ? List.of("binary", "pla_convex")
                : List.of("binary", "pla_convex", "net_softsign")) assertTrue(log.contains(name), log);
        boolean hasPdf = plot && !exactOnly;
        long expected = 0;
        int[] keys = Arrays.stream(raw).mapToInt(value -> (int) (value + Integer.MIN_VALUE)).toArray();
        for (String seed : seeds.split(";")) {
            expected += expectedChecksum(keys, Long.parseLong(seed));
            String suffix = seeds.contains(";") ? "_seed" + seed : "";
            if (hasPdf) assertPdfOffsets(tempDir.resolve("out/books_200M_uint32_1e4" + suffix + ".pdf"));
        }
        expected *= exactOnly ? 2L : 3L;
        assertTrue(log.contains("checksum: " + expected), log);
        assertEquals(hasPdf, Files.exists(tempDir.resolve("out")));
        assertEquals(hasPdf, log.contains("plot written to"));
    }

    @Test
    void mainCleansWorkspaceOnSuccessAndFailure() throws Exception {
        Path data = tempDir.resolve(Keyset._books_200M_uint32.fileName());
        writeUint32(data, 1, 2, 3);
        Properties props = base();
        props.setProperty("data.keyset", "_books_200M_uint32");
        props.setProperty("index.types", "_binary");
        try (OutputStream output = Files.newOutputStream(tempDir.resolve("config.properties"))) {
            props.store(output, null);
        }
        assertEquals(0, runJava(Main.class.getName(), tempDir.toString()));
        String report = Files.readString(tempDir.resolve("process.log"));
        assertTrue(report.contains("binary") && report.contains("checksum:"), report);
        assertFalse(report.contains("failed") || report.contains("cannot delete"), report);
        try (var entries = Files.list(tempDir)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("hw-template-")));
        }
        Files.delete(data);
        assertEquals(1, runJava(Main.class.getName(), tempDir.toString()));
        String error = Files.readString(tempDir.resolve("process.log"));
        assertTrue(error.contains("error:") && error.contains(data.getFileName().toString()), error);
        assertFalse(error.contains("cannot delete"), error);
        try (var entries = Files.list(tempDir)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("hw-template-")));
        }
    }

    @Test
    void cliRejectsBadArgumentsAndRunnerForwardsBothStreamsAndStopsOnTimeout() throws Exception {
        assertEquals("1 2 3", StringReport.preview(new int[]{1, 2, 3}));
        assertEquals("0 1 2 ... 4 5 6", StringReport.preview(new int[]{0, 1, 2, 3, 4, 5, 6}));
        assertEquals(2, runJava("me.index.Main", "--unknown"));
        String diagnostic = Files.readString(tempDir.resolve("process.log"));
        assertTrue(diagnostic.contains("unknown option --unknown") && diagnostic.contains("usage: Main"), diagnostic);
        assertFalse(diagnostic.contains("Exception"), diagnostic);
        assertEquals(2, runJava("me.index.Main", "a", "b"));
        assertEquals(2, runJava("me.index.Main", "--plot", "--plot"));
        assertEquals(2, runJava("me.index.Main", "--plot=false"));
        assertEquals(1, runJava("me.index.Main"));
        assertEquals(2, runJava(Benchmark.class.getName()));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String testClasses =
                Path.of(AllTests.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> command = List.of(java, "-cp", testClasses, ProcessFixture.class.getName(), "flood");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream(), stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout); PrintStream err = new PrintStream(stderr);
             JvmRunner runner = new JvmRunner()) {
            assertEquals(new JvmRunner.Exit(7, false), runner.run(command, Duration.ofSeconds(10), out, err));
            assertEquals(2 * 1024 * 1024, stdout.size());
            assertEquals(2 * 1024 * 1024, stderr.size());
            assertTrue(stdout.toString(StandardCharsets.US_ASCII).chars().allMatch(c -> c == 'a'));
            assertTrue(stderr.toString(StandardCharsets.US_ASCII).chars().allMatch(c -> c == 'b'));
            out.print("tail");
            err.print("tail");
            assertEquals(2 * 1024 * 1024 + 4, stdout.size());
            assertEquals(2 * 1024 * 1024 + 4, stderr.size());
            command = List.of(java, "-cp", testClasses, ProcessFixture.class.getName(), "sleep");
            assertTrue(runner.run(command, Duration.ofMillis(500), out, err).timedOut());
        }
    }

    @Test
    @SuppressWarnings("try")
    void jvmRunnerHandlesInterruptionCloseAndBrokenOutput() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(AllTests.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> command = List.of(java, "-cp", classes, ProcessFixture.class.getName(), "sleep");
        for (boolean interrupt : new boolean[]{false, true}) {
            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<JvmRunner.Exit> exit = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            try (JvmRunner runner = new JvmRunner();
                 PrintStream out = new PrintStream(new OutputStream() {
                     @Override
                     public void write(int value) {
                         ready.countDown();
                     }
                 });
                 PrintStream err = new PrintStream(OutputStream.nullOutputStream())) {
                Thread worker = Thread.ofPlatform().start(() -> {
                    try {
                        exit.set(runner.run(command, Duration.ofSeconds(30), out, err));
                    } catch (Throwable e) {
                        failure.set(e);
                        interrupted.set(Thread.currentThread().isInterrupted());
                    }
                });
                try {
                    assertTrue(ready.await(10, TimeUnit.SECONDS), "child did not start");
                    assertThrows(IllegalStateException.class,
                            () -> runner.run(command, Duration.ofSeconds(1), out, err));
                    if (interrupt) worker.interrupt();
                    else runner.close();
                    worker.join(10_000);
                    assertFalse(worker.isAlive(), "runner did not stop");
                    if (interrupt) {
                        assertInstanceOf(IOException.class, failure.get());
                        assertTrue(interrupted.get(), "interrupt status must be preserved");
                    } else {
                        assertNull(failure.get());
                        assertNotEquals(0, exit.get().code());
                        assertFalse(exit.get().timedOut());
                        assertThrows(IllegalStateException.class,
                                () -> runner.run(command, Duration.ofSeconds(1), out, err));
                    }
                } finally {
                    runner.close();
                    worker.interrupt();
                    worker.join(10_000);
                }
            }
        }
        try (JvmRunner runner = new JvmRunner();
             PrintStream broken = new PrintStream(new OutputStream() {
                 @Override
                 public void write(int value) throws IOException {
                     throw new IOException("output unavailable");
                 }
             });
             PrintStream err = new PrintStream(OutputStream.nullOutputStream())) {
            assertThrows(IllegalArgumentException.class, () -> runner.run(command, Duration.ZERO, broken, err));
            assertThrows(IllegalArgumentException.class,
                    () -> runner.run(command, Duration.ofMillis(-1), broken, err));
            IOException failure = assertThrows(IOException.class, () -> runner.run(
                    List.of(java, "-cp", classes, ProcessFixture.class.getName(), "flood"),
                    Duration.ofSeconds(10), broken, err));
            assertTrue(failure.getMessage().contains("cannot forward JVM output"));
        }
    }

    @Test
    void forkReportsChildFailureAndTimeoutWithoutAConfigFile() throws Exception {
        DataParams data = new DataParams(Keyset._books_200M_uint32, DataSize._1e4, 42);
        BenchmarkArguments job = new BenchmarkArguments(data, new SearchParams(IndexType._binary), tempDir,
                tempDir.resolve("result"), false);
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(OutputStream.nullOutputStream());
             PrintStream err = new PrintStream(errors); JvmRunner runner = new JvmRunner()) {
            assertThrows(IOException.class, () -> Context.load(data, tempDir));
            Files.writeString(job.resultFile(), "stale or partial result");
            BenchmarkSummary failed =
                    Benchmark.fork(job, runner, java, classpath, Duration.ofSeconds(30), out, err).summary();
            assertFalse(Files.exists(job.resultFile()));
            assertFalse(failed.built());
            assertEquals("binary", failed.id());
            assertEquals("failed: JVM exited with code 1", failed.note());
            assertEquals(0, failed.checksum());
            assertTrue(errors.toString(StandardCharsets.UTF_8).contains(data.keyset().fileName()));

            writeUint32(tempDir.resolve(data.keyset().fileName()), 1, 2, 3);
            BenchmarkSummary timedOut =
                    Benchmark.fork(job, runner, java, classpath, Duration.ofMillis(1), out, err).summary();
            assertFalse(Files.exists(job.resultFile()));
            assertFalse(timedOut.built());
            assertEquals("failed: timed out after 1 ms", timedOut.note());
            assertEquals(0, timedOut.checksum());
            BenchmarkResult success = Benchmark.fork(job, runner, java, classpath, Duration.ofSeconds(30), out, err);
            assertTrue(success.summary().built(), success.summary().note());
            assertNull(success.predictions());
            assertFalse(Files.exists(job.resultFile()));
        }
        assertFalse(Files.exists(tempDir.resolve("config.properties")));
    }

    @Test
    void benchmarkRejectsIndexesThatViolateTheirContracts() throws Exception {
        Path source = tempDir.resolve("BinarySearchIndex.java");
        Files.writeString(source, """
                package me.index.task.isearch;
                import me.index.task.Index;
                import me.index.libs.math.Sorted;
                public final class BinarySearchIndex implements Index {
                    private final String failure = System.getProperty("index.failure");
                    private int[] keys;
                    public void build(int[] keys) {
                        if (failure.equals("unimplemented")) throw new UnsupportedOperationException();
                        if (failure.equals("build")) throw new IllegalStateException("build rejected");
                        this.keys = keys;
                    }
                    private int rank(int key) { return Sorted.lowerBound(keys, 0, keys.length, key); }
                    private boolean missing(int key, int rank) {
                        return rank == keys.length || keys[rank] != key;
                    }
                    public int lower_bound(int key) {
                        int r = rank(key);
                        if (failure.equals("lookup") || failure.equals("missingLookup") && missing(key, r)) return -1;
                        return r;
                    }
                    public int predict(int key) {
                        int r = rank(key);
                        if (failure.equals("range") || failure.equals("missingRange") && missing(key, r))
                            return keys.length;
                        int p = Math.min(r, keys.length - 1);
                        if (failure.equals("error") && !missing(key, r)
                                || failure.equals("missingPrediction") && missing(key, r))
                            return (p + 1) % keys.length;
                        return p;
                    }
                    public String id() { return "invalid"; }
                    public int maxError() {
                        return failure.equals("negativeError") ? -1 : failure.equals("positiveError") ? 1 : 0;
                    }
                    public long sizeInBytes() {
                        return failure.equals("negativeSize") ? -1 : failure.equals("positiveSize") ? 1 : 0;
                    }
                }
                """);
        String classes = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
                "-cp", classes, "-d", tempDir.toString(), source.toString()), diagnostics.toString());
        writeUint32(tempDir.resolve(Keyset._books_200M_uint32.fileName()), 100, 200, 300);
        BenchmarkArguments job = new BenchmarkArguments(
                new DataParams(Keyset._books_200M_uint32, DataSize._1e4, 42),
                new SearchParams(IndexType._binary), tempDir, tempDir.resolve("result.bin"), false);
        String[][] cases = {
                {"unimplemented", "not implemented"}, {"build", "build rejected"},
                {"negativeError", "negative index metadata"}, {"negativeSize", "negative index metadata"},
                {"positiveError", "must report maxError=0"}, {"positiveSize", "must report maxError=0"},
                {"lookup", "lower_bound("}, {"range", "outside [0,"}, {"error", "off by"},
                {"missingLookup", "lower_bound("}, {"missingRange", "outside [0,"},
                {"missingPrediction", "for a search index"}
        };
        try (JvmRunner runner = new JvmRunner(); PrintStream output = new PrintStream(diagnostics)) {
            for (String[] scenario : cases) {
                List<String> command = new ArrayList<>(List.of(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx256m",
                        "-Dindex.failure=" + scenario[0], "-cp", tempDir + java.io.File.pathSeparator + classes,
                        Benchmark.class.getName()));
                command.addAll(job.arguments());
                assertEquals(new JvmRunner.Exit(0, false),
                        runner.run(command, Duration.ofSeconds(10), output, output), diagnostics.toString());
                try (InputStream input = Files.newInputStream(job.resultFile())) {
                    BenchmarkResult result = BenchmarkResult.read(input, 0);
                    assertFalse(result.summary().built(), scenario[0]);
                    assertTrue(result.summary().note().contains(scenario[1]), result.summary().note());
                    assertNull(result.predictions());
                    assertEquals(0, result.summary().checksum());
                }
            }
        }
    }

    @Test
    void forkRejectsMissingOrCorruptResultsAfterSuccessfulExit() throws Exception {
        Path source = tempDir.resolve("Benchmark.java");
        Files.writeString(source, """
                package me.index;
                public class Benchmark {
                    public static void main(String[] args) throws Exception {
                        var options = java.util.List.of(args);
                        if (options.contains("_binary")) return;
                        var file = java.nio.file.Path.of(options.get(options.indexOf("--result") + 1));
                        java.nio.file.Files.write(file, new byte[]{1, 2, 3});
                    }
                }
                """);
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
                "-d", tempDir.toString(), source.toString()), diagnostics.toString(StandardCharsets.UTF_8));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        DataParams data = new DataParams(Keyset._gauss_int32, DataSize._1e4, 42);
        try (PrintStream output = new PrintStream(diagnostics); JvmRunner runner = new JvmRunner()) {
            for (IndexType type : List.of(IndexType._binary, IndexType._exponential)) {
                BenchmarkArguments args = new BenchmarkArguments(data, new SearchParams(type), tempDir,
                        tempDir.resolve("result.bin"), false);
                BenchmarkResult measured = Benchmark.fork(args, runner, java, tempDir.toString(),
                        Duration.ofSeconds(30), output, output);
                assertFalse(measured.summary().built());
                assertTrue(measured.summary().note().startsWith("failed: cannot read result:"),
                        measured.summary().note());
                assertNull(measured.predictions());
                assertFalse(Files.exists(args.resultFile()));
            }
        }
    }

    @Test
    void streamCodecsLeaveCallerOwnedStreamsOpen() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public void close() {
                fail("codec closed caller-owned output");
            }
        };
        BenchmarkSummary result = new BenchmarkSummary("test", 1, 0, 0, 2, 4, 3, 4, null);
        new BenchmarkResult(result, null).write(output);
        InputStream input = uncloseableInput(output.toByteArray());
        assertEquals(result, BenchmarkResult.read(input, 0).summary());
        output.reset();
        new BenchmarkResult(new BenchmarkSummary("approximate", 1, 1, 16, 2, 4, 3, 4, null), new int[]{0, 1}).write(
                output);
        assertArrayEquals(new int[]{0, 1},
                BenchmarkResult.read(uncloseableInput(output.toByteArray()), 2).predictions());
        byte[] sosd = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(2).putInt(0).putInt(-1).array();
        assertArrayEquals(new long[]{Integer.MIN_VALUE, Integer.MAX_VALUE},
                SosdReader.read(uncloseableInput(sosd), 2, false));
        output.reset();
        new Plot(300, 200).write(output);
        assertTrue(output.toString(StandardCharsets.US_ASCII).startsWith("%PDF-1.4"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 99_999, 100_000, 100_001, 1_000_000})
    void pdfReportsSampleTheSameKeysAndPreserveOriginalRanks(int size) {
        long[] keys = new long[size];
        long[] predictions = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = Integer.MIN_VALUE + 2L * i;
            predictions[i] = 3L * i + 7;
        }
        Plot plot = PdfReport.plot(keys);
        PdfReport.addPrediction(plot, keys, predictions, "prediction", 0);
        List<?> layers = (List<?>) field(plot, "layers");
        assertEquals(2, layers.size());
        double[] x = (double[]) field(layers.getFirst(), "x");
        double[] ranks = (double[]) field(layers.getFirst(), "y");
        double[] predicted = (double[]) field(layers.getLast(), "y");
        assertEquals(Math.min(size, 100_000), x.length);
        assertEquals(x.length, ranks.length);
        assertEquals(x.length, predicted.length);
        assertArrayEquals(x, (double[]) field(layers.getLast(), "x"));
        if (size > 0) {
            assertEquals(0.0, ranks[0]);
            assertEquals(size - 1.0, ranks[ranks.length - 1]);
        }
        for (int i = 0; i < ranks.length; i++) {
            assertEquals(Math.rint(ranks[i]), ranks[i]);
            assertEquals(Integer.MIN_VALUE + 2.0 * ranks[i], x[i]);
            assertEquals(3.0 * ranks[i] + 7, predicted[i]);
            if (size <= 100_000) assertEquals((double) i, ranks[i]);
            if (i > 0) {
                double spacing = (size - 1.0) / (ranks.length - 1);
                double gap = ranks[i] - ranks[i - 1];
                assertTrue(gap >= 1 && gap >= Math.floor(spacing) && gap <= Math.ceil(spacing));
            }
        }
        if (size == 100_001) {
            assertThrows(IllegalArgumentException.class,
                    () -> PdfReport.addPrediction(plot, keys, new long[size + 1], "mismatch", 0));
        }
    }

    @Test
    void plotsRejectChangesAfterWritingButCanBeWrittenAgain() throws IOException {
        Plot plot = new Plot(300, 200).line(new long[]{0, 1}, new long[]{1, 2}, "line");
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        plot.write(first);
        List<Consumer<Plot>> changes = List.of(
                p -> p.title("changed"), p -> p.xlabel("x"), p -> p.ylabel("y"),
                p -> p.grid(false), p -> p.legend(false), p -> p.xTicks(2), p -> p.yTicks(2),
                p -> p.xlim(0, 2), p -> p.ylim(0, 2), p -> p.squareMarkers(true),
                p -> p.line(new double[]{0}, new double[]{0}, Plot.RED, "line"),
                p -> p.scatter(new double[]{0}, new double[]{0}, Plot.RED, "point"),
                p -> p.line(new long[]{0}, new long[]{0}, "auto"),
                p -> p.scatter(new long[]{0}, new long[]{0}, "auto")
        );
        for (Consumer<Plot> change : changes) {
            assertThrows(IllegalStateException.class, () -> change.accept(plot));
        }
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        plot.write(second);
        assertArrayEquals(first.toByteArray(), second.toByteArray());
    }

    @ParameterizedTest
    @EnumSource(Models.class)
    void networksReuseTrainingStorageAndDoNotAllocateInSteadyState(Models type) {
        ShuffleProbe random = new ShuffleProbe();
        Model probe = type.create(new int[]{1, 1}, 0.01, 2, new SquaredLoss(), random);
        probe.train(new double[8], new double[8], 1);
        Object order = field(probe, "order");
        random.bounds.clear();
        probe.train(new double[3], new double[3], 1);
        assertEquals(List.of(3, 2), random.bounds, "shuffle only the active part of the buffer");
        assertSame(order, field(probe, "order"));
        probe.train(new double[8], new double[8], 1);
        assertSame(order, field(probe, "order"));
        probe.train(new double[9], new double[9], 1);
        assertNotSame(order, field(probe, "order"));

        var management = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(management instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) management;
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        Model net = type.create(new int[]{1, 4, 4, 1}, 0.01, 3, new SquaredLoss(), new Random(42));
        double[] xs = {-1, -0.5, 0, 0.5, 1}, ys = {0, 0.25, 0.5, 0.75, 1};
        double[] originalXs = xs.clone(), originalYs = ys.clone();
        for (int i = 0; i < 10_000; i++) allocationSink = net.predict(xs[i % xs.length]);
        for (int i = 0; i < 1000; i++) allocationSink = net.train(xs, ys, 1);
        long thread = Thread.currentThread().threadId();
        long predictionBytes = Long.MAX_VALUE, trainingBytes = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 10_000; i++) allocationSink = net.predict(xs[i % xs.length]);
            predictionBytes = Math.min(predictionBytes, bean.getThreadAllocatedBytes(thread) - before);
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100; i++) allocationSink = net.train(xs, ys, 2);
            trainingBytes = Math.min(trainingBytes, bean.getThreadAllocatedBytes(thread) - before);
        }
        assertEquals(0, predictionBytes, "predict allocates after warmup");
        assertEquals(0, trainingBytes, "train allocates despite sufficient buffer capacity");
        assertArrayEquals(originalXs, xs);
        assertArrayEquals(originalYs, ys);
    }

    @Test
    void pdfEncodingOffsetsAndPlotVariantsAreValid() throws IOException {
        assertEquals("\\(x\\)\\\\\\200?", Page.escape("(x)\\€Ж"));
        assertEquals("1.25", Page.fmt(1.25));
        assertEquals("2", Page.fmt(2));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Page.fmt(invalid));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CntOutputStream out = new CntOutputStream(bytes)) {
            out.write('A');
            out.write("BC");
            out.write(new byte[]{68, 69});
            out.write(new byte[]{0, 70, 71, 0}, 1, 2);
            assertEquals(7, out.count());
        }
        assertEquals("ABCDEFG", bytes.toString(StandardCharsets.ISO_8859_1));
        Document doc = new Document();
        Path pdf = tempDir.resolve("document.pdf");
        assertThrows(IllegalStateException.class, () -> writePdf(doc, pdf));
        assertThrows(IllegalStateException.class, () -> doc.getRegFontName(Font.COURIER));
        Page page = doc.addA4VerticalPage();
        doc.addA4HorizontalPage();
        page.save().strokeColor(1, 0, 0).fillColor(0, 0, 1).lineWidth(2).dash(1, 2)
                .moveTo(0, 0).lineTo(1, 2).stroke().solidLine().square(5, 5, 1).fillAndStroke()
                .circle(10, 10, 2).fill().restore().text("(hello)\\", 20, 20, Font.COURIER, 12);
        writePdf(doc, pdf);
        assertPdfOffsets(pdf);
        List<Plot> plots = List.of(new Plot(300, 200),
                new Plot(300, 200)
                        .line(new long[]{0, 1}, new long[]{1, 2}, Plot.RED, "width", 2)
                        .line(new long[]{0, 1}, new long[]{2, 3}, Plot.GREEN, "color")
                        .line(new long[]{0, 1}, new long[]{3, 4}, "auto")
                        .scatter(new long[]{0, 1}, new long[]{4, 5}, Plot.BLUE, "points"),
                new Plot(300, 200).scatter(new long[]{1}, new long[]{2}, "single"),
                new Plot(300, 200).title("Test").xlabel("x").ylabel("y")
                        .scatter(new long[]{-1, 0, 1}, new long[]{0, 1, 2}, Plot.BLUE, "points", 1)
                        .line(new double[]{0, 1, Double.NaN, 2}, new double[]{0, 1, 2, 3}, "gaps"),
                new Plot(300, 200).grid(false).legend(false).squareMarkers(true).xTicks(1).yTicks(1)
                        .xlim(-1, 1).ylim(0, 0).scatter(new double[]{0, Double.NaN}, new double[]{0, 1}, ""));
        for (Plot plot : plots) {
            writePdf(plot, pdf);
            assertPdfOffsets(pdf);
            byte[] first = Files.readAllBytes(pdf);
            writePdf(plot, pdf);
            assertArrayEquals(first, Files.readAllBytes(pdf), "save must not render layers twice");
        }
        assertThrows(IllegalArgumentException.class, () -> new Plot(300, 200).xTicks(0));
        assertThrows(IllegalArgumentException.class, () -> new Plot(300, 200).yTicks(-1));
        assertThrows(IllegalStateException.class, () -> writePdf(new Plot(300, 200).xlim(2, 1), pdf));
        assertThrows(IllegalStateException.class,
                () -> writePdf(new Plot(300, 200).ylim(0, Double.POSITIVE_INFINITY), pdf));
    }

    // Independent reference calculations and small fixtures used by the tests above.

    private static Loss[] losses() {
        return new Loss[]{new SquaredLoss(), new AbsoluteLoss(), new HuberLoss(0.2)};
    }

    private static long expectedChecksum(int[] keys, long seed) {
        long checksum = 0;
        for (BenchmarkQueries batch : BenchmarkQueries.createBatches(keys, Benchmark.QUERIES,
                Benchmark.WARMUP_ROUNDS + Benchmark.MEASURED_ROUNDS, seed)) {
            for (int key : batch.keys()) checksum += lowerBound(keys, key);
        }
        return checksum;
    }

    private static long[] readKeys(Path file, int limit, boolean wide) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return SosdReader.read(input, limit, wide);
        }
    }

    private static void writePdf(Document doc, Path file) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            doc.write(output);
        }
    }

    private static void writePdf(Plot plot, Path file) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            plot.write(output);
        }
    }

    private static InputStream uncloseableInput(byte[] bytes) {
        return new ByteArrayInputStream(bytes) {
            @Override
            public void close() {
                fail("codec closed caller-owned input");
            }
        };
    }

    public static final class ProcessFixture {
        public static void main(String[] args) throws InterruptedException {
            if (args[0].equals("sleep")) {
                System.out.println("ready");
                System.out.flush();
                Thread.sleep(30_000);
                return;
            }
            byte[] block = new byte[8192];
            Arrays.fill(block, (byte) 'a');
            for (int i = 0; i < 256; i++) System.out.write(block, 0, block.length);
            System.out.flush();
            Arrays.fill(block, (byte) 'b');
            for (int i = 0; i < 256; i++) System.err.write(block, 0, block.length);
            System.err.flush();
            System.exit(7);
        }
    }

    private static void assertInt128(BigInteger expected, Int128 actual) {
        BigInteger wrapped = expected.mod(MOD);
        if (wrapped.testBit(127)) wrapped = wrapped.subtract(MOD);
        assertEquals(wrapped, integer(actual), "signed arithmetic modulo 2^128");
    }

    private static void writeUint32(Path file, long... values) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 4 * values.length).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putLong(values.length);
        for (long value : values) bytes.putInt((int) value);
        Files.write(file, bytes.array());
    }

    private int runJava(String mainClass, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx256m",
                "-Djava.io.tmpdir=" + tempDir, "-cp",
                Path.of(Context.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                mainClass));
        command.addAll(List.of(args));
        Path log = tempDir.resolve("process.log");
        Process process = new ProcessBuilder(command).directory(tempDir.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "process timed out: " + mainClass);
            int code = process.exitValue();
            return code;
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor();
            }
        }
    }

    private static void assertPdfOffsets(Path file) throws IOException {
        // ISO-8859-1 preserves byte offsets, unlike UTF-8 decoding.
        String pdf = Files.readString(file, StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4\n"));
        assertTrue(pdf.endsWith("%%EOF"));
        int start = pdf.lastIndexOf("startxref\n") + "startxref\n".length();
        int offset = Integer.parseInt(pdf.substring(start, pdf.indexOf('\n', start)));
        String[] xref = pdf.substring(offset).split("\n");
        assertEquals("xref", xref[0]);
        int count = Integer.parseInt(xref[1].split(" ")[1]);
        for (int i = 1; i < count; i++) {
            int objectOffset = Integer.parseInt(xref[i + 2].substring(0, 10));
            assertTrue(pdf.startsWith(i + " 0 obj\n", objectOffset), "PDF object " + i);
        }
    }

    private static List<int[]> datasets() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
        out.add(new int[]{0, 49});
        out.add(new int[]{-1, 0, 1});
        out.add(new int[]{0, 10, 30, 31, 32});
        out.add(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE});
        for (int offset : new int[]{Integer.MIN_VALUE, -1000, 0, Integer.MAX_VALUE - 3000}) {
            int[] keys = new int[200];
            for (int i = 0; i < keys.length; i++) keys[i] = offset + i * 13;
            out.add(keys);
        }
        Random rnd = new Random(0x51EA2C);
        for (int t = 0; t < 240; t++) {
            int n = 2 + rnd.nextInt(80);
            int[] keys;
            if (t % 3 == 0) {
                keys = rnd.ints(n, -200, 201).distinct().sorted().toArray();
            } else if (t % 3 == 1) {
                keys = rnd.ints(n).distinct().sorted().toArray();
            } else {
                keys = new int[n];
                keys[0] = -1_000_000;
                for (int i = 1; i < n; i++) keys[i] = keys[i - 1] + (i % 9 == 0 ? 100_000 : 1);
            }
            if (keys.length >= 2) out.add(keys);
        }
        return out;
    }

    private static void assertIndex(Index index, int[] keys, boolean exact) {
        String context = index.id() + " keys=" + Arrays.toString(keys);
        int worst = 0;
        for (int i = 0; i < keys.length; i++) {
            int prediction = index.predict(keys[i]);
            assertTrue(prediction >= 0 && prediction < keys.length, context + " prediction range");
            worst = Math.max(worst, Math.abs(prediction - i));
            query(index, keys, keys[i], exact, context);
            if (keys[i] != Integer.MIN_VALUE) query(index, keys, keys[i] - 1, exact, context);
            if (keys[i] != Integer.MAX_VALUE) query(index, keys, keys[i] + 1, exact, context);
            if (i != 0) query(index, keys, (int) (((long) keys[i - 1] + keys[i]) / 2), exact, context);
        }
        assertTrue(worst <= index.maxError(), context + " measured error exceeds maxError");
        assertTrue(index.sizeInBytes() >= 0, context + " negative size");
        query(index, keys, Integer.MIN_VALUE, exact, context);
        query(index, keys, Integer.MAX_VALUE, exact, context);
        Random rnd = new Random(572);
        for (int i = 0; i < 50; i++) query(index, keys, rnd.nextInt(), exact, context);
    }

    private static void query(Index index, int[] keys, int key, boolean exact, String context) {
        int expected = lowerBound(keys, key);
        assertEquals(expected, index.lower_bound(key), context + " lower_bound(" + key + ")");
        int p = index.predict(key);
        assertTrue(p >= 0 && p < keys.length, context + " predict(" + key + ") range");
        if (exact) assertEquals(Math.min(expected, keys.length - 1), p, context + " exact prediction");
    }

    private record Ratio(BigInteger n, BigInteger d) implements Comparable<Ratio> {
        Ratio(long n, long d) {
            this(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        Ratio(Frac128 f) {
            this(integer(f.num()), integer(f.den()));
        }

        @Override
        public int compareTo(Ratio other) {
            return n.multiply(other.d).compareTo(other.n.multiply(d));
        }

        Ratio at(int key, Ratio intercept) {
            return new Ratio(n.multiply(BigInteger.valueOf(key)).multiply(intercept.d)
                    .add(intercept.n.multiply(d)), d.multiply(intercept.d));
        }
    }

    private static BigInteger integer(Int128 value) {
        // Independent of Maths.toByteArray and all production arithmetic.
        BigInteger low = BigInteger.valueOf(value.lo() & Long.MAX_VALUE);
        if (value.lo() < 0) low = low.setBit(63);
        return BigInteger.valueOf(value.hi()).shiftLeft(64).add(low);
    }

    // Eliminating the intercept gives a slope interval for each pair of points.
    // This quadratic oracle does not reuse the production convex-hull algorithm.
    private static final class Feasibility {
        private Ratio lower;
        private Ratio upper;

        boolean add(int[] keys, int start, int end, int eps) {
            for (int i = start; i < end; i++) {
                long dx = (long) keys[end] - keys[i];
                Ratio lo = new Ratio(end - i - 2L * eps, dx);
                Ratio hi = new Ratio(end - i + 2L * eps, dx);
                if (lower == null || lo.compareTo(lower) > 0) lower = lo;
                if (upper == null || hi.compareTo(upper) < 0) upper = hi;
            }
            return lower == null || lower.compareTo(upper) <= 0;
        }
    }

    private static int checkCht(int[] keys, int eps) {
        Window window = CHT.INSTANCE;
        int count = 0;
        for (int start = 0; start < keys.length; ) {
            assertEquals(0, window.init_skip(keys[start], eps), "CHT skip");
            Feasibility oracle = new Feasibility();
            int end = start;
            double[] accepted = window.get_result();
            while (end < keys.length) {
                boolean expected = oracle.add(keys, start, end, eps);
                boolean actual = window.can_expand(keys[end], end - start, eps);
                assertEquals(expected, actual, "CHT feasibility eps=" + eps + " start=" + start
                        + " end=" + end + " keys=" + Arrays.toString(keys));
                if (!actual) {
                    assertArrayEquals(accepted, window.get_result(), "rejection changed accepted line");
                    break;
                }
                accepted = window.get_result();
                end++;
            }
            Ratio slope = new Ratio((Frac128) field(window, "cf0"));
            Ratio intercept = new Ratio((Frac128) field(window, "cf1"));
            for (int i = start; i < end; i++) {
                Ratio prediction = slope.at(keys[i], intercept);
                assertTrue(prediction.compareTo(new Ratio(i - start - eps, 1)) >= 0
                                && prediction.compareTo(new Ratio(i - start + eps, 1)) <= 0,
                        "CHT exact certificate violates eps=" + eps + " at " + i);
            }
            checkRounded(keys, start, end, accepted, eps);
            assertTrue(end > start, "CHT must advance");
            start = end;
            count++;
        }
        return count;
    }

    private static void checkRounded(int[] keys, int start, int end, double[] line, int eps) {
        assertTrue(Double.isFinite(line[0]) && Double.isFinite(line[1]), "finite coefficients");
        for (int i = start; i < end; i++) {
            double predicted = line[0] * keys[i] + line[1];
            // For these int-key fixtures the floating arithmetic error is much
            // smaller than one rank. Do not demand error <= eps after truncation.
            assertTrue(Math.abs(predicted - (i - start)) <= eps + 1e-5,
                    "real-valued line error: " + predicted + " rank=" + (i - start) + " eps=" + eps);
            assertTrue(Math.abs((long) Maths.predict(line[0], line[1], keys[i]) - (i - start)) <= eps + 1L,
                    "integer error exceeds eps + one rounding position");
        }
    }

    private static int greedyEnd(int[] keys, int start, int eps, boolean spline) {
        if (start + 2 >= keys.length) return keys.length;
        Ratio fixed = new Ratio(1, (long) keys[start + 1] - keys[start]);
        Ratio lower = new Ratio(1L - eps, (long) keys[start + 1] - keys[start]);
        Ratio upper = new Ratio(1L + eps, (long) keys[start + 1] - keys[start]);
        for (int end = start + 2; end < keys.length; end++) {
            long dx = (long) keys[end] - keys[start];
            long pos = end - start;
            Ratio point = new Ratio(pos, dx);
            Ratio lo = new Ratio(pos - eps, dx);
            Ratio hi = new Ratio(pos + eps, dx);
            if (spline) {
                // Independent slope-interval oracle, strict endpoint inclusion.
                if (point.compareTo(lower) <= 0 || point.compareTo(upper) >= 0) return end;
                if (lo.compareTo(lower) > 0) lower = lo;
                if (hi.compareTo(upper) < 0) upper = hi;
            } else if (fixed.compareTo(lo) < 0 || fixed.compareTo(hi) > 0) {
                return end;
            }
        }
        return keys.length;
    }

    private static void checkGreedy(int[] keys, int eps, Windows variant) {
        Window window = variant.window();
        for (int start = 0; start < keys.length; ) {
            int expected = greedyEnd(keys, start, eps, variant == Windows.SPLINE);
            int end = start + window.init_skip(keys[start], eps);
            while (end < keys.length && window.can_expand(keys[end], end - start, eps)) end++;
            long firstGap = start + 1 < keys.length ? (long) keys[start + 1] - keys[start] : 1;
            boolean binaryExactSlope = (firstGap & (firstGap - 1)) == 0;
            if (variant == Windows.SPLINE || binaryExactSlope) {
                assertEquals(expected, end, variant + " corridor start=" + start + " eps=" + eps
                        + " keys=" + Arrays.toString(keys));
            }
            // GreedyWindow uses floating arithmetic at its boundary. Exact
            // partition equality is asserted only for binary-exact first gaps.
            checkRounded(keys, start, end, window.get_result(), eps);
            start = end;
        }
    }

    private static int optimalCount(int[] keys, int eps) {
        int[] dp = new int[keys.length + 1];
        Arrays.fill(dp, keys.length + 1);
        dp[0] = 0;
        for (int start = 0; start < keys.length; start++) {
            Feasibility oracle = new Feasibility();
            for (int end = start; end < keys.length && oracle.add(keys, start, end, eps); end++) {
                dp[end + 1] = Math.min(dp[end + 1], dp[start] + 1);
            }
        }
        return dp[keys.length];
    }

    private static List<Parameter> parameters(Model net) {
        List<Parameter> out = new ArrayList<>();
        for (double[][] layer : (double[][][]) field(net, "weights")) {
            for (double[] row : layer) for (int i = 0; i < row.length; i++) out.add(new Parameter(row, i));
        }
        for (double[] row : (double[][]) field(net, "biases")) {
            for (int i = 0; i < row.length; i++) out.add(new Parameter(row, i));
        }
        return out;
    }

    private static double objective(Model net, Loss loss, double[] xs, double[] ys) {
        double sum = 0;
        for (int i = 0; i < xs.length; i++) sum += loss.value(net.predict(xs[i]), ys[i]);
        return sum / xs.length;
    }

    private static double mse(Model net, double[] xs, double[] ys) {
        double sum = 0;
        for (int i = 0; i < xs.length; i++) sum += Math.pow(net.predict(xs[i]) - ys[i], 2);
        return sum / xs.length;
    }

    private static double activation(Models type, double x) {
        return switch (type) {
            case SIGMOID -> 1 / (1 + Math.exp(-x));
            case RELU -> Math.max(0, x);
            case LEAKY_RELU -> x > 0 ? x : 0.01 * x;
            case SOFTSIGN -> x / (1 + Math.abs(x));
        };
    }

    private static void gradients(Models type, Loss loss) {
        double rate = 0.025;
        Model net = type.create(new int[]{1, 3, 2, 1}, rate, 8, loss, new Random(45));
        List<Parameter> ps = parameters(net);
        // Deliberately nonzero biases and positive/negative hidden activations,
        // away from ReLU and absolute-loss nondifferentiable points.
        for (int i = 0; i < ps.size(); i++) ps.get(i).set(i % 2 == 0 ? 0.4 : -0.3);
        double[] xs = type == Models.SIGMOID ? new double[]{0.7} : new double[]{-0.7, 0.3, 0.8};
        double[] ys = type == Models.SIGMOID ? new double[]{2} : new double[]{2, 3, -2};
        double[] expected = new double[ps.size()];
        for (int i = 0; i < ps.size(); i++) {
            Parameter p = ps.get(i);
            double value = p.get(), h = 1e-6;
            p.set(value + h);
            double plus = objective(net, loss, xs, ys);
            p.set(value - h);
            double minus = objective(net, loss, xs, ys);
            p.set(value);
            expected[i] = value - rate * (plus - minus) / (2 * h);
        }
        double reported = net.train(xs, ys, 1);
        for (int i = 0; i < ps.size(); i++) {
            assertEquals(expected[i], ps.get(i).get(), 2e-8,
                    type + "/" + loss.id() + " finite-difference parameter " + i);
        }
        assertEquals(mse(net, xs, ys), reported, 1e-14, "train returns MSE, not training loss");
    }

    private static void batches(Models type, Loss loss, int batch) {
        Model net = type.create(new int[]{1, 1}, 0.05, batch, loss, new OrderedRandom());
        double[] xs = {-1, -0.5, 0, 0.5, 1};
        double[] ys = {-0.2, 0.1, 0.4, 0.7, 1.0};
        double w = 0, b = 0;
        int actualBatch = type == Models.SIGMOID ? 1 : batch;
        for (int epoch = 0; epoch < 3; epoch++) {
            for (int start = 0; start < xs.length; start += actualBatch) {
                int end = Math.min(xs.length, start + actualBatch);
                double dw = 0, db = 0;
                for (int i = start; i < end; i++) {
                    double g = loss.gradient(w * xs[i] + b, ys[i]);
                    dw += g * xs[i];
                    db += g;
                }
                w -= 0.05 * dw / (end - start);
                b -= 0.05 * db / (end - start);
            }
            // Three separate calls also catch stale accumulated gradients.
            double reported = net.train(xs, ys, 1);
            assertEquals(mse(net, xs, ys), reported, 1e-14, "final MSE");
            for (double x : new double[]{-2, 0, 0.25, 1, 3}) {
                assertEquals(w * x + b, net.predict(x), 1e-13, type + " batch=" + batch);
            }
        }
    }

    private record Parameter(double[] row, int index) {
        double get() {
            return row[index];
        }

        void set(double value) {
            row[index] = value;
        }
    }

    private static final class OrderedRandom extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }

        @Override
        public double nextGaussian() {
            return 0;
        }
    }

    private static final class ShuffleProbe extends Random {
        private static final long serialVersionUID = 1L;
        final ArrayList<Integer> bounds = new ArrayList<>();

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return 0;
        }

        @Override
        public double nextGaussian() {
            return 0;
        }
    }

    private static final class ProbeModel implements Model {
        double output;
        double trainingResult;
        double[] seenX, seenY;

        public double predict(double x) {
            return output;
        }

        public double train(double[] xs, double[] ys, int epochs) {
            seenX = xs.clone();
            seenY = ys.clone();
            return trainingResult;
        }

        public String id() {
            return "probe";
        }

        public long sizeInBytes() {
            return 8;
        }
    }

    private static Properties base() {
        Properties props = new Properties();
        props.setProperty("data.keyset", "_gauss_int32");
        props.setProperty("data.size", "_1e4");
        props.setProperty("index.types", "_binary;_exponential;_interpolation;_pla_greedy;_pla_spline;_pla_convex;"
                + "_net_sigmoid;_net_relu;_net_leaky_relu;_net_softsign");
        props.setProperty("index.err", "_0;_1;_2;_4;_8;_16;_32");
        props.setProperty("net.layers", "2");
        props.setProperty("net.train.epochs", "1");
        return props;
    }

    private static Object field(Object object, String name) {
        for (Class<?> cls = object.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                // Continue through the model's shared superclass.
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("field not found: " + name);
    }

    private static int lowerBound(int[] keys, int key) {
        int result = Arrays.binarySearch(keys, key);
        return result >= 0 ? result : -result - 1;
    }
}
