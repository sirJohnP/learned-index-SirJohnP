package me.index.task.isegment;

public enum GreedyWindow implements Window {
    INSTANCE;

    private long firstKey;
    private double slope;
    private boolean hasSlope;

    @Override
    public int init_skip(long key, long err) {
        firstKey = key;
        slope = 0.0;
        hasSlope = false;
        return 1;
    }

    @Override
    public boolean can_expand(long key, long pos, int err) {
        if (!hasSlope) {
            slope = (double) pos / (double) (key - firstKey);
            hasSlope = true;
            return true;
        }
        return Math.abs(slope * (double) (key - firstKey) - pos) <= err;
    }

    @Override
    public double[] get_result() {
        return new double[]{slope, -slope * firstKey};
    }
}
