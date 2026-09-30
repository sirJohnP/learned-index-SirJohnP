package me.index.config;

import me.index.config.parameters.enums.IndexType;
import me.index.config.parameters.enums.LossFunction;
import me.index.config.parameters.enums.MaxErr;
import me.index.task.Index;
import me.index.task.inet.NetIndex;
import me.index.task.isegment.PlaIndex;

import java.util.List;
import java.util.Random;

public sealed interface IndexSpec {
    IndexType type();

    String suffix();

    String label();

    Index create();

    record Search(IndexType type) implements IndexSpec {
        @Override
        public String suffix() {
            return "";
        }

        @Override
        public String label() {
            return type.label();
        }

        @Override
        public Index create() {
            return type.search().create();
        }
    }

    record Pla(IndexType type, MaxErr err) implements IndexSpec {
        @Override
        public String suffix() {
            return "";
        }

        @Override
        public String label() {
            return type.label() + "(err=" + err.value + ")";
        }

        @Override
        public Index create() {
            return new PlaIndex(type.window(), err.value);
        }
    }

    record Net(IndexType type, LossFunction.Choice loss, double rate, List<Integer> layers,
               int epochs, int batchSize, long seed, String suffix) implements IndexSpec {
        public Net {
            layers = List.copyOf(layers);
        }

        @Override
        public String label() {
            return type.label() + "/" + loss.label() + suffix;
        }

        @Override
        public Index create() {
            return new NetIndex(
                    type.model().create(layerSizes(), rate, batchSize, loss.newLoss(), new Random(seed)),
                    epochs);
        }

        private int[] layerSizes() {
            int[] sizes = new int[layers.size() + 2];
            sizes[0] = 1;
            for (int i = 0; i < layers.size(); i++) {
                sizes[i + 1] = layers.get(i);
            }
            sizes[sizes.length - 1] = 1;
            return sizes;
        }
    }
}
