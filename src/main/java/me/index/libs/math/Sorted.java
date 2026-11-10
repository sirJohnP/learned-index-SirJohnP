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
        return lowerBoundNear(keys, 0, keys.length, key, predicted, maxErr);
    }

    public static int lowerBoundNear(int[] keys, int from, int to, int key, int predicted, int maxErr) {
        if (from == to) return from;
        int pivot = Math.clamp(predicted, from, to - 1);
        int lo = Math.clamp((long) pivot - maxErr, from, to);
        int hi = Math.clamp((long) pivot + maxErr + 1, lo, to);
        int found;
        if (maxErr <= 32) {
            int value = keys[pivot];
            if (value == key) return pivot;
            found = pivot;
            if (value < key) {
                found++;
                while (found < hi && keys[found] < key) found++;
            } else {
                while (found > lo && keys[found - 1] >= key) found--;
            }
        } else {
            found = lowerBound(keys, lo, hi, key);
        }
        if (found == lo && lo > from && keys[lo - 1] >= key) {
            return lowerBound(keys, from, lo - 1, key);
        }
        if (found == hi && hi < to && keys[hi] < key) {
            return lowerBound(keys, hi + 1, to, key);
        }
        return found;
    }
}
