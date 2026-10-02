package me.index.task.isearch;

import me.index.libs.math.Sorted;
import me.index.task.Index;

public final class ExponentialSearchIndex implements Index {
    private int[] keys;

    @Override
    public void build(int[] keys) {
        Sorted.checkKeys(keys);
        this.keys = keys;
    }

    @Override
    public int predict(int key) {
        int hi = findSegment(key);
        return Sorted.lowerBoundNear(keys, key, hi - 1, hi/2);
    }

    private int findSegment(int key) {
        int lo = 1;
        while (lo < keys.length) {
            if (keys[lo - 1] >= key) {
                return lo;
            }
            lo <<= 2;
        }
        return keys.length;
    }

    @Override
    public int lower_bound(int key) {
        return Sorted.lowerBoundNear(keys, key, predict(key), maxError());
    }

    @Override
    public String id() {
        return "exponential";
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
