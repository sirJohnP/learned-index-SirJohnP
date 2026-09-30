package me.index.task.inet;

import me.index.libs.math.Sorted;
import me.index.task.Index;
import me.index.task.inet.model.Model;

public final class NetIndex implements Index {
    private final Model model;
    private final int epochs;

    private int[] keys;
    private int n;
    private double base;
    private double scale;
    private int error;

    public NetIndex(Model model, int epochs) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (epochs <= 0) {
            throw new IllegalArgumentException("epochs must be positive, got " + epochs);
        }
        this.model = model;
        this.epochs = epochs;
    }

    @Override
    public void build(int[] keys) {
        Sorted.checkKeys(keys);
        this.keys = keys;
        this.n = keys.length;
        this.base = keys[0];
        this.scale = 2.0 / ((double) keys[n - 1] - keys[0]);

        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = normalize(keys[i]);
            ys[i] = (double) i / (n - 1);
        }
        double meanSquaredError = model.train(xs, ys, epochs);
        if (!Double.isFinite(meanSquaredError)) {
            throw new IllegalStateException("training diverged: the mean squared error is " + meanSquaredError);
        }
        this.error = measureError();
    }

    private int measureError() {
        int worst = 0;
        for (int i = 0; i < n; i++) {
            worst = Math.max(worst, Math.abs(predict(keys[i]) - i));
        }
        return worst;
    }

    private double normalize(int key) {
        return (key - base) * scale - 1.0;
    }

    @Override
    public int predict(int key) {
        int pos = (int) Math.round(model.predict(normalize(key)) * (n - 1));
        return Math.min(Math.max(pos, 0), n - 1);
    }

    @Override
    public int lower_bound(int key) {
        return Sorted.lowerBoundNear(keys, key, predict(key), error);
    }

    @Override
    public String id() {
        return "net_" + model.id();
    }

    @Override
    public int maxError() {
        return error;
    }

    @Override
    public long sizeInBytes() {
        return model.sizeInBytes();
    }
}
