package me.index.config.parameters.enums;

import me.index.task.inet.loss.Loss;
import me.index.task.inet.loss.AbsoluteLoss;
import me.index.task.inet.loss.HuberLoss;
import me.index.task.inet.loss.SquaredLoss;

public enum LossFunction {
    _squared {
        @Override
        public Loss create(double parameter) {
            return new SquaredLoss();
        }

        @Override
        public boolean takesParameter() {
            return false;
        }

        @Override
        public double defaultParameter() {
            return 1.0;
        }

        @Override
        public double defaultLearningRate() {
            return 0.05;
        }
    },
    _absolute {
        @Override
        public Loss create(double parameter) {
            return new AbsoluteLoss();
        }

        @Override
        public boolean takesParameter() {
            return false;
        }

        @Override
        public double defaultParameter() {
            return 1.0;
        }

        @Override
        public double defaultLearningRate() {
            return 0.005;
        }
    },
    _huber {
        @Override
        public Loss create(double parameter) {
            return new HuberLoss(parameter);
        }

        @Override
        public boolean takesParameter() {
            return true;
        }

        @Override
        public double defaultParameter() {
            return 0.02;
        }

        @Override
        public double defaultLearningRate() {
            return 0.5;
        }
    };

    public abstract Loss create(double parameter);

    public abstract boolean takesParameter();

    public abstract double defaultParameter();

    public abstract double defaultLearningRate();

    public record Choice(LossFunction loss, double parameter) {
        public Loss newLoss() {
            return loss.create(parameter);
        }

        public double learningRate() {
            return loss.defaultLearningRate();
        }

        public String label() {
            String name = loss.name().substring(1);
            return loss.takesParameter() ? name + "(" + parameter + ")" : name;
        }
    }
}
