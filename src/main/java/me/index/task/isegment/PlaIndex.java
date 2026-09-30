package me.index.task.isegment;

import me.index.libs.math.Maths;
import me.index.libs.math.Sorted;
import me.index.task.Index;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaIndex implements Index {
    private record Segment(int start, double slope, double intercept) {
        int predict(int key) {
            return start + Maths.predict(slope, intercept, key);
        }
    }

    private final Windows variant;
    private final int maxErr;

    private int[] keys;
    private int[] maxKeys;
    private Segment[] segments;
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
        List<Segment> segments = new ArrayList<>();
        List<Integer> maxKeys = new ArrayList<>();
        Window.split(variant.window(), keys, maxErr, (start, end, slope, intercept) -> {
            segments.add(new Segment(start, slope, intercept));
            maxKeys.add(keys[end - 1]);
        });
        this.keys = keys;
        this.segments = segments.toArray(new Segment[0]);
        this.maxKeys = maxKeys.stream().mapToInt(Integer::intValue).toArray();
        this.error = measureError();
    }

    private int measureError() {
        int worst = 0;
        for (int i = 0; i < keys.length; i++) {
            worst = Math.max(worst, Math.abs(predict(keys[i]) - i));
        }
        return worst;
    }

    @Override
    public int predict(int key) {
        int segment = Sorted.lowerBound(maxKeys, 0, maxKeys.length, key);
        if (segment == segments.length) {
            return keys.length - 1;
        }
        return Math.min(segments[segment].predict(key), keys.length - 1);
    }

    @Override
    public int lower_bound(int key) {
        return Sorted.lowerBoundNear(keys, key, predict(key), error);
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
        return (long) segments.length * (Integer.BYTES + Integer.BYTES + Double.BYTES + Double.BYTES);
    }
}
