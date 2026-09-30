package me.index.task.inet.loss;

public interface Loss {
    double value(double predicted, double target);

    double gradient(double predicted, double target);

    String id();
}
