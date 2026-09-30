package me.index.libs.math;

public final class Sorted {
    private Sorted() {
    }

    public static void checkKeys(int[] keys) {
        if (keys.length < 2) {
            throw new IllegalArgumentException("an index needs at least two keys, got " + keys.length);
        }
        for (int i = 1; i < keys.length; i++) {
            if (keys[i - 1] >= keys[i]) {
                throw new IllegalArgumentException("keys must be sorted and distinct, but keys[" + (i - 1)
                        + "] = " + keys[i - 1] + " >= keys[" + i + "] = " + keys[i]);
            }
        }
    }

    public static int lowerBound(int[] keys, int from, int to, int key) {
        int lo = from;
        int hi = to;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid] < key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public static int lowerBoundNear(int[] keys, int key, int predicted, int maxErr) {
        int from = Math.clamp((long) predicted - maxErr, 0, keys.length);
        int to = Math.clamp((long) predicted + maxErr + 1, from, keys.length);
        int found = lowerBound(keys, from, to, key);
        if (found == from && from > 0 && keys[from - 1] >= key) {
            return lowerBound(keys, 0, from - 1, key);
        }
        if (found == to && to < keys.length && keys[to] < key) {
            return lowerBound(keys, to + 1, keys.length, key);
        }
        return found;
    }
}
