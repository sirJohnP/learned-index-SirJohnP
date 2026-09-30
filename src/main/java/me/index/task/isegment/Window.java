package me.index.task.isegment;

import me.index.libs.math.Maths;

public interface Window {
    int init_skip(long key, long err);

    boolean can_expand(long key, long pos, int err);

    double[] get_result();

    @FunctionalInterface
    interface SegmentConsumer {
        void accept(int start, int end, double slope, double intercept);
    }

    static void split(Window window, int[] keys, int maxErr, SegmentConsumer segments) {
        int start = 0;
        while (start < keys.length) {
            int length = window.init_skip(keys[start], maxErr);
            while (start + length < keys.length && window.can_expand(keys[start + length], length, maxErr)) {
                length++;
            }
            if (length <= 0) {
                throw new IllegalStateException("init_skip returned " + length + " and can_expand rejected the"
                        + " segment's own first key, so the segment is empty and the split cannot advance");
            }

            double[] line = window.get_result();
            double slope = line[0];
            double intercept = line[1];
            int error = 0;
            for (int i = start; i < start + length; i++) {
                error = Math.max(error, Math.abs((i - start) - Maths.predict(slope, intercept, keys[i])));
            }
            if (error > maxErr) {
                System.err.println("[WARNING] large error due to the limited precision of doubles, found: "
                        + error + ", expected: " + maxErr);
            }

            segments.accept(start, start + length, slope, intercept);
            start += length;
        }
    }
}
