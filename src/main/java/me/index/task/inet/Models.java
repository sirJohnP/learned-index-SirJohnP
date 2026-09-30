package me.index.task.inet;

import me.index.task.inet.loss.Loss;
import me.index.task.inet.model.LeakyReluNet;
import me.index.task.inet.model.Model;
import me.index.task.inet.model.ReluNet;
import me.index.task.inet.model.SigmoidNet;
import me.index.task.inet.model.SoftsignNet;

import java.util.Random;

public enum Models {
    SIGMOID {
        @Override
        public Model create(int[] sz, double lr, int batch, Loss loss, Random rnd) {
            return new SigmoidNet(sz, lr, batch, loss, rnd);
        }
    },
    RELU {
        @Override
        public Model create(int[] sz, double lr, int batch, Loss loss, Random rnd) {
            return new ReluNet(sz, lr, batch, loss, rnd);
        }
    },
    LEAKY_RELU {
        @Override
        public Model create(int[] sz, double lr, int batch, Loss loss, Random rnd) {
            return new LeakyReluNet(sz, lr, batch, loss, rnd);
        }
    },
    SOFTSIGN {
        @Override
        public Model create(int[] sz, double lr, int batch, Loss loss, Random rnd) {
            return new SoftsignNet(sz, lr, batch, loss, rnd);
        }
    };

    public abstract Model create(int[] layerSizes, double learningRate, int batchSize, Loss loss, Random rnd);
}
