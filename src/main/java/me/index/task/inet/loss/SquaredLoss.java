package me.index.task.inet.loss;

public final class SquaredLoss implements Loss {
    @Override
    public double value(double predicted, double target) {
        double error = predicted - target;
        return 0.5 * error * error;
    }

    @Override
    public double gradient(double predicted, double target) {
        return predicted - target;
    }

    @Override
    public String id() {
        return "squared";
    }
}
