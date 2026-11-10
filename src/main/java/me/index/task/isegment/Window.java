package me.index.task.isegment;

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
            if (line == null || line.length != 2) {
                throw new IllegalStateException("get_result must return {slope, intercept}");
            }
            double slope = line[0];
            double intercept = line[1];
            if (!Double.isFinite(slope) || !Double.isFinite(intercept)) {
                throw new IllegalStateException("non-finite segment coefficients at start " + start
                        + ": slope=" + slope + ", intercept=" + intercept);
            }
            segments.accept(start, start + length, slope, intercept);
            start += length;
        }
    }
}
