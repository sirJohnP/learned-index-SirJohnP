package me.index.task.inet.model;

import me.index.task.inet.loss.Loss;

import java.util.Random;

public final class ReluNet implements Model {
    public ReluNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public double predict(double x) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public double train(double[] xs, double[] ys, int epochs) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public String id() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public long sizeInBytes() {
        throw new UnsupportedOperationException("TODO");
    }
}
