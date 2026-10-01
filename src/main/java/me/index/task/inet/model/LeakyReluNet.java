package me.index.task.inet.model;

import me.index.task.inet.loss.Loss;

import java.util.Random;

public final class LeakyReluNet extends AbstractNet {
    public LeakyReluNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
        super(layerSizes, learningRate, batchSize, loss, rnd);
    }

    @Override 
    protected double activFunction(double z) {
        return z > 0 ? z : 0.01 * z;
    }

    @Override 
    protected double derivative(double z) {
        return z > 0 ? 1.0 : 0.01;
    }

    @Override
    public String id() {
        return "leaky_relu/" + loss.id();
    }
}
