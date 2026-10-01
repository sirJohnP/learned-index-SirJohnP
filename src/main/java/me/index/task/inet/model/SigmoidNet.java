package me.index.task.inet.model;

import me.index.task.inet.loss.Loss;

import java.util.Random;

public final class SigmoidNet extends AbstractNet {
    public SigmoidNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
        super(layerSizes, learningRate, batchSize, loss, rnd);
    }

    @Override 
    protected double activFunction(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    @Override
    protected double derivative(double sigmoid) {
        return sigmoid * (1.0 - sigmoid);
    }

    @Override
    public String id() {
        return "sigmoid/" + loss.id();
    }
}
