package me.index.task.inet.model;

import me.index.libs.math.Maths;
import me.index.task.inet.Init;
import me.index.task.inet.loss.Loss;

import java.util.Arrays;
import java.util.Random;

abstract class MiniBatchNet implements Model {
    private final double learningRate;
    private final int batchSize;
    private final Loss loss;
    private final Random rnd;
    private final int layers;

    private final double[][][] weights;
    private final double[][] biases;

    private final double[][] a;

    private final double[][] delta;
    private final double[][][] gradWeights;
    private final double[][] gradBiases;
    private int[] order = new int[0];

    protected MiniBatchNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd, Init init) {
        if (layerSizes == null || layerSizes.length < 2
                || layerSizes[0] != 1 || layerSizes[layerSizes.length - 1] != 1) {
            throw new IllegalArgumentException("input and output layers must have size 1, got "
                    + Arrays.toString(layerSizes));
        }
        for (int size : layerSizes) {
            if (size <= 0) {
                throw new IllegalArgumentException("layer sizes must be positive, got "
                        + Arrays.toString(layerSizes));
            }
        }
        if (!(learningRate > 0) || !Double.isFinite(learningRate)) {
            throw new IllegalArgumentException("learningRate must be finite and positive, got " + learningRate);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, got " + batchSize);
        }
        if (loss == null || rnd == null) {
            throw new IllegalArgumentException("loss and rnd must not be null");
        }
        this.learningRate = learningRate;
        this.batchSize = batchSize;
        this.loss = loss;
        this.rnd = rnd;
        this.layers = layerSizes.length - 1;

        this.weights = new double[layers][][];
        this.biases = new double[layers][];
        this.a = new double[layers + 1][];
        this.delta = new double[layers][];
        this.gradWeights = new double[layers][][];
        this.gradBiases = new double[layers][];

        a[0] = new double[layerSizes[0]];
        for (int l = 0; l < layers; l++) {
            int in = layerSizes[l];
            int out = layerSizes[l + 1];
            weights[l] = Maths.gaussian(out, in, init.scale(in, out), rnd);
            biases[l] = new double[out];
            a[l + 1] = new double[out];
            delta[l] = new double[out];
            gradWeights[l] = new double[out][in];
            gradBiases[l] = new double[out];
        }
    }

    protected abstract double activation(double z);

    protected abstract double derivativeFromActivation(double activation);

    protected abstract String activationName();

    @Override
    public double predict(double x) {
        forward(x);
        return a[layers][0];
    }

    @Override
    public double train(double[] xs, double[] ys, int epochs) {
        if (xs == null || ys == null) {
            throw new IllegalArgumentException("xs and ys must not be null");
        }
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys differ in length: " + xs.length + " vs " + ys.length);
        }
        if (epochs < 0) {
            throw new IllegalArgumentException("epochs must not be negative, got " + epochs);
        }
        if (xs.length == 0) {
            return Double.NaN;
        }
        if (epochs == 0) {
            return meanSquaredError(xs, ys);
        }
        int n = xs.length;
        if (order.length < n) order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        for (int epoch = 0; epoch < epochs; epoch++) {
            shuffle(n);
            for (int start = 0; start < n; ) {
                int end = start + Math.min(batchSize, n - start);
                for (int k = start; k < end; k++) {
                    int i = order[k];
                    forward(xs[i]);
                    backward(ys[i]);
                    accumulate();
                }
                update(end - start);
                start = end;
            }
        }
        return meanSquaredError(xs, ys);
    }

    private void forward(double x) {
        a[0][0] = x;
        for (int l = 0; l < layers; l++) {
            double[][] w = weights[l];
            double[] b = biases[l];
            double[] in = a[l];
            double[] out = a[l + 1];
            boolean linear = l == layers - 1;
            for (int i = 0; i < out.length; i++) {
                double[] row = w[i];
                double sum = b[i];
                for (int j = 0; j < in.length; j++) {
                    sum += row[j] * in[j];
                }
                out[i] = linear ? sum : activation(sum);
            }
        }
    }

    private void backward(double y) {
        delta[layers - 1][0] = loss.gradient(a[layers][0], y);
        for (int l = layers - 2; l >= 0; l--) {
            double[][] next = weights[l + 1];
            double[] nextDelta = delta[l + 1];
            double[] cur = delta[l];
            double[] activated = a[l + 1];
            for (int i = 0; i < cur.length; i++) {
                double sum = 0.0;
                for (int k = 0; k < nextDelta.length; k++) {
                    sum += next[k][i] * nextDelta[k];
                }
                cur[i] = sum * derivativeFromActivation(activated[i]);
            }
        }
    }

    private void accumulate() {
        for (int l = 0; l < layers; l++) {
            double[] d = delta[l];
            double[] in = a[l];
            double[][] gw = gradWeights[l];
            double[] gb = gradBiases[l];
            for (int i = 0; i < d.length; i++) {
                gb[i] += d[i];
                double[] row = gw[i];
                for (int j = 0; j < in.length; j++) {
                    row[j] += d[i] * in[j];
                }
            }
        }
    }

    private void update(int count) {
        double step = learningRate / count;
        for (int l = 0; l < layers; l++) {
            double[][] w = weights[l];
            double[] b = biases[l];
            double[][] gw = gradWeights[l];
            double[] gb = gradBiases[l];
            for (int i = 0; i < b.length; i++) {
                b[i] -= step * gb[i];
                gb[i] = 0.0;
                double[] row = w[i];
                double[] gRow = gw[i];
                for (int j = 0; j < row.length; j++) {
                    row[j] -= step * gRow[j];
                    gRow[j] = 0.0;
                }
            }
        }
    }

    private void shuffle(int size) {
        for (int i = size - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int swapped = order[i];
            order[i] = order[j];
            order[j] = swapped;
        }
    }

    private double meanSquaredError(double[] xs, double[] ys) {
        double sum = 0.0;
        for (int i = 0; i < xs.length; i++) {
            double error = predict(xs[i]) - ys[i];
            sum += error * error;
        }
        return sum / xs.length;
    }

    @Override
    public String id() {
        return activationName() + "/" + loss.id();
    }

    @Override
    public long sizeInBytes() {
        long doubles = a[0].length;
        for (int l = 0; l < layers; l++) {
            doubles += (long) weights[l].length * weights[l][0].length + biases[l].length;
            doubles += a[l + 1].length;
        }
        return doubles * Double.BYTES;
    }
}
