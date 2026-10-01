package me.index.task.inet.model;

import me.index.task.inet.loss.Loss;

import java.util.Random;

public final class ReluNet extends AbstractNet {
    public ReluNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
        super(layerSizes, learningRate, batchSize, loss, rnd);
    }

    @Override 
    protected double activFunction(double z) {
        return Math.max(0.0, z);
    }

    @Override
    protected double derivative(double z) {
        return z > 0 ? 1.0 : 0.0;
    }

    @Override
    public String id() {
        return "relu/" + loss.id();
    }
}
