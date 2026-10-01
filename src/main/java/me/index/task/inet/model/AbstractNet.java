package me.index.task.inet.model;

import java.util.Arrays;
import java.util.Random;

import me.index.libs.math.Maths;
import me.index.task.inet.Init;
import me.index.task.inet.loss.Loss;

public abstract class AbstractNet implements Model {
    protected final double learningRate;
    protected final Loss loss;
    protected final Random rnd;
    protected final int layers;
    protected final double[][][] weights;
    protected final double[][] biases;

    protected final double[][] a;
    protected final double[][] delta;

    public AbstractNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
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

        this.a = new double[layers+1][];
        for (int l = 0; l < layerSizes.length; l++) {
            this.a[l] = new double[layerSizes[l]];
        }
        
        this.delta = new double[layers][];
        for (int l = 0; l < layers; l++) {
            this.delta[l] = new double[layerSizes[l + 1]];
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
        forward(x);
        backward(y);
        update(a, delta);
    }
    
    private double[][] forward(double x) {
        a[0][0] = x;
        for (int l = 0; l < layers; l++) {
            for (int i = 0; i < a[l+1].length; i++) {
                double acc = 0;
                for (int j = 0; j < a[l].length; j++) {
                    acc += weights[l][i][j] * a[l][j]; 
                }
                acc += biases[l][i];
                a[l+1][i] = l == layers - 1 ? acc : activFunction(acc);
            }
        }
        return a;
    }

    private double[][] backward(double y) {
        delta[layers - 1][0] = loss.gradient(a[layers][0], y);

        for (int l = layers - 2; l >= 0; l--) {
            for (int j = 0; j < delta[l].length; j++) {
                delta[l][j] = 0;
            }

            for (int i = 0; i < weights[l + 1].length; i++) {
                double[] row = weights[l+1][i];
                double scale = delta[l+1][i];
                for (int j = 0; j < row.length; j++) {
                    delta[l][j] += row[j] * scale;
                }
            }

            for (int j = 0; j < delta[l].length; j++) {
                delta[l][j] = delta[l][j] * derivative(a[l+1][j]);
            }

        }
        
        return delta;
    }
    
    protected abstract double activFunction(double z);
    protected abstract double derivative(double z);

    private void update(double[][] a, double[][] delta) {
        for (int l = 0; l < layers; l++) {
            Maths.subtractScaled(weights[l], gradient(delta[l], a[l]), learningRate);
            Maths.subtractScaled(biases[l], delta[l], learningRate);
        }
    }

    private static double[][] gradient(double[] delta, double[] input) {
        return Maths.outer(delta, input);
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
    public long sizeInBytes() {
        long doubles = 0;
        for (int l = 0; l < layers; l++) {
            doubles += (long) weights[l].length * weights[l][0].length + biases[l].length;
        }
        return doubles * Double.BYTES;
    }

    // protected abstract double[][] forward(double x);
    // protected abstract double[][] backward(double[][] a, double y);
}