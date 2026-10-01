package me.index.task.inet.model;

import me.index.task.inet.loss.Loss;

import java.util.Random;

public final class SoftsignNet extends AbstractNet {
    public SoftsignNet(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd) {
        super(layerSizes, learningRate, batchSize, loss, rnd);
    }

    @Override 
    protected double activFunction(double z) {
        return z / (1 + Math.abs(z));
    }

    @Override
    protected double derivative(double activ) {
        double z = 1 - Math.abs(activ);
        return z*z;
    }

    @Override
    public String id() {
        return "softsign/" + loss.id();
    }
}
