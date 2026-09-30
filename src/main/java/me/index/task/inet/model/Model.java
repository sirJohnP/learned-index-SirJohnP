package me.index.task.inet.model;

public interface Model {
    double predict(double x);

    double train(double[] xs, double[] ys, int epochs);

    String id();

    long sizeInBytes();
}
