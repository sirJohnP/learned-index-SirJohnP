package me.index.task.isegment;

import me.index.libs.math.Maths;
import me.index.libs.math.Sorted;
import me.index.task.Index;

import java.util.Arrays;
import java.util.Locale;

public final class PlaIndex implements Index {
    private static final int MAX_RADIX_BITS = 14;

    private final Windows variant;
    private final int maxErr;

    private int[] keys;
    private int[] maxKeys;
    private int[] starts;
    private int[] errors;
    private double[] slopes;
    private double[] intercepts;
    private int segmentCount;
    private int[] radix;
    private int radixShift;
    private int error;

    public PlaIndex(Windows variant, int maxErr) {
        if (variant == null) {
            throw new IllegalArgumentException("variant must not be null");
        }
        if (maxErr < 0) {
            throw new IllegalArgumentException("maxErr must not be negative, got " + maxErr);
        }
        this.variant = variant;
        this.maxErr = maxErr;
    }

    @Override
    public void build(int[] keys) {
        Sorted.checkKeys(keys);
        int capacity = Math.min(16, keys.length);
        this.keys = keys;
        maxKeys = new int[capacity];
        starts = new int[capacity];
        errors = new int[capacity];
        slopes = new double[capacity];
        intercepts = new double[capacity];
        segmentCount = 0;
        error = 0;
        Window.split(variant.window(), keys, maxErr, this::addSegment);
        if (segmentCount != starts.length) resize(segmentCount);
        buildRadix();
    }

    private void addSegment(int start, int end, double slope, double intercept) {
        if (segmentCount == starts.length) {
            resize((int) Math.min(2L * starts.length, keys.length));
        }
        int segmentError = 0;
        for (int i = start; i < end; i++) {
            int predicted = predict(keys[i], start, slope, intercept, keys.length);
            segmentError = Math.max(segmentError, Math.abs(predicted - i));
        }
        maxKeys[segmentCount] = keys[end - 1];
        starts[segmentCount] = start;
        errors[segmentCount] = segmentError;
        slopes[segmentCount] = slope;
        intercepts[segmentCount] = intercept;
        segmentCount++;
        error = Math.max(error, segmentError);
    }

    private void resize(int capacity) {
        maxKeys = Arrays.copyOf(maxKeys, capacity);
        starts = Arrays.copyOf(starts, capacity);
        errors = Arrays.copyOf(errors, capacity);
        slopes = Arrays.copyOf(slopes, capacity);
        intercepts = Arrays.copyOf(intercepts, capacity);
    }

    private static int predict(int key, int start, double slope, double intercept, int size) {
        return (int) Math.min((long) start + Maths.predict(slope, intercept, key), size - 1);
    }

    @Override
    public int predict(int key) {
        int segment = findSegment(key);
        if (segment == segmentCount) {
            return keys.length - 1;
        }
        return predict(key, starts[segment], slopes[segment], intercepts[segment], keys.length);
    }

    @Override
    public int lower_bound(int key) {
        int position = findSegment(key);
        if (position == segmentCount) return keys.length;
        int start = starts[position];
        int end = position + 1 < segmentCount ? starts[position + 1] : keys.length;
        return Sorted.lowerBoundNear(keys, start, end, key,
                predict(key, start, slopes[position], intercepts[position], keys.length), errors[position]);
    }

    private void buildRadix() {
        radix = null;
        if (segmentCount <= 16) return;
        long span = (long) keys[keys.length - 1] - keys[0];
        int bits = Math.min(MAX_RADIX_BITS, Integer.SIZE - Integer.numberOfLeadingZeros(segmentCount - 1));
        radixShift = Math.max(0, Long.SIZE - Long.numberOfLeadingZeros(span) - bits);
        int buckets = (int) (span >>> radixShift) + 1;
        radix = new int[buckets + 1];
        int segment = 0;
        for (int bucket = 0; bucket <= buckets; bucket++) {
            long boundary = keys[0] + ((long) bucket << radixShift);
            while (segment < maxKeys.length && maxKeys[segment] < boundary) segment++;
            if (bucket > 0 && segment - radix[bucket - 1] > segmentCount / 2) {
                radix = null;
                return;
            }
            radix[bucket] = segment;
        }
    }

    private int findSegment(int key) {
        if (radix == null) return Sorted.lowerBound(maxKeys, 0, maxKeys.length, key);
        if (key <= keys[0]) return 0;
        if (key > keys[keys.length - 1]) return segmentCount;
        int bucket = (int) (((long) key - keys[0]) >>> radixShift);
        int from = radix[bucket];
        int to = radix[bucket + 1];
        if (to - from > 8) return Sorted.lowerBound(maxKeys, from, to, key);
        while (from < to && maxKeys[from] < key) from++;
        return from;
    }

    @Override
    public String id() {
        return "pla_" + variant.name().toLowerCase(Locale.ROOT) + "(err=" + maxErr + ")";
    }

    @Override
    public int maxError() {
        return error;
    }

    @Override
    public long sizeInBytes() {
        return (long) segmentCount * (3 * Integer.BYTES + 2 * Double.BYTES)
                + (radix == null ? 0L : (long) radix.length * Integer.BYTES);
    }
}
