package me.index.task.isearch;

import me.index.libs.math.Sorted;
import me.index.task.Index;

public final class InterpolationSearchIndex implements Index {
    private int[] keys;

    @Override
    public void build(int[] keys) {
        Sorted.checkKeys(keys);
        this.keys = keys;
    }

    @Override
    public int predict(int key) {
        if (keys[0] >= key) {
            return 0;
        }
        if (keys[keys.length - 1] < key) {
            return keys.length - 1;
        }

        int lo = 0, hi = keys.length - 1;
        while (keys[lo] < key && key < keys[hi]) {
            int m = (int)(lo + ((long)key - keys[lo]) * (hi - lo) / ((long)keys[hi] - keys[lo]));
            if (keys[m] < key) {
                lo = m + 1;
            } else if (keys[m] > key) {
                hi = m - 1;
            } else {
                return m;
            }
        }

        if (keys[lo] >= key) {
            return lo;
        } else if (keys[hi] >= key) {
            return hi;
        } else {
            return hi + 1;
        }
    }

    @Override
    public int lower_bound(int key) {
        return Sorted.lowerBoundNear(keys, key, predict(key), maxError());
    }

    @Override
    public String id() {
        return "interpolation";
    }

    @Override
    public int maxError() {
        return 0;
    }

    @Override
    public long sizeInBytes() {
        return 0;
    }
}
