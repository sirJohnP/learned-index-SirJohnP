package me.index.task.inet.model;

import me.index.libs.math.Maths;
import me.index.task.inet.Init;
import me.index.task.inet.loss.Loss;

import java.util.Arrays;
import java.util.Random;

public final class SigmoidNet implements Model {
    private final double learningRate;
    private final Loss loss;
    private final Random rnd;
    private final int layers;
    private final double[][][] weights;
    private final double[][] biases;

    public SigmoidNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
        if (layerSizes.length < 2 || layerSizes[0] != 1 || layerSizes[layerSizes.length - 1] != 1) {
            throw new IllegalArgumentException("input and output layers must have size 1, got "
                    + Arrays.toString(layerSizes));
        }
        if (!(learningRate > 0) || !Double.isFinite(learningRate)) {
            throw new IllegalArgumentException("learningRate must be finite and positive, got " + learningRate);
        }
        if (loss == null || rnd == null) {
            throw new IllegalArgumentException("loss and rnd must not be null");
        }
        this.learningRate = learningRate;
        this.loss = loss;
        this.rnd = rnd;
        this.layers = layerSizes.length - 1;
        this.weights = new double[layers][][];
        this.biases = new double[layers][];
        for (int l = 0; l < layers; l++) {
            int in = layerSizes[l];
            int out = layerSizes[l + 1];
            weights[l] = Maths.gaussian(out, in, Init.XAVIER.scale(in, out), rnd);
            biases[l] = new double[out];
        }
    }

    @Override
    public double predict(double x) {
        return forward(x)[layers][0];
    }

    @Override
    public double train(double[] xs, double[] ys, int epochs) {
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys differ in length: " + xs.length + " vs " + ys.length);
        }
        if (epochs < 0) {
            throw new IllegalArgumentException("epochs must not be negative, got " + epochs);
        }
        if (xs.length == 0 || epochs == 0) {
            return Double.NaN;
        }
        int[] order = new int[xs.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        for (int epoch = 0; epoch < epochs; epoch++) {
            shuffle(order);
            for (int i : order) {
                step(xs[i], ys[i]);
            }
        }
        return meanSquaredError(xs, ys);
    }

    private void step(double x, double y) {
        double[][] a = forward(x);
        double[][] delta = backward(a, y);
        update(a, delta);
    }

    private double[][] forward(double x) {
        double[][] a = new double[layers + 1][];
        a[0] = new double[]{x};
        for (int l = 0; l < layers; l++) {
            double[] z = Maths.add(Maths.mul(weights[l], a[l]), biases[l]);
            a[l + 1] = l == layers - 1 ? z : Maths.map(z, SigmoidNet::sigmoid);
        }
        return a;
    }

    private double[][] backward(double[][] a, double y) {
        double[][] delta = new double[layers][];
        delta[layers - 1] = new double[]{loss.gradient(a[layers][0], y)};
        for (int l = layers - 2; l >= 0; l--) {
            double[] back = Maths.mulTransposed(weights[l + 1], delta[l + 1]);
            delta[l] = Maths.hadamard(back, Maths.map(a[l + 1], SigmoidNet::derivative));
        }
        return delta;
    }

    private void update(double[][] a, double[][] delta) {
        for (int l = 0; l < layers; l++) {
            Maths.subtractScaled(weights[l], gradient(delta[l], a[l]), learningRate);
            Maths.subtractScaled(biases[l], delta[l], learningRate);
        }
    }

    private static double[][] gradient(double[] delta, double[] input) {
        return Maths.outer(delta, input);
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private static double derivative(double sigmoid) {
        return sigmoid * (1.0 - sigmoid);
    }

    private void shuffle(int[] order) {
        for (int i = order.length - 1; i > 0; i--) {
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
        return "sigmoid/" + loss.id();
    }

    @Override
    public long sizeInBytes() {
        long doubles = 0;
        for (int l = 0; l < layers; l++) {
            doubles += (long) weights[l].length * weights[l][0].length + biases[l].length;
        }
        return doubles * Double.BYTES;
    }
}
