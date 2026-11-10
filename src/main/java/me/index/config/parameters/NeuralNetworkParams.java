package me.index.config.parameters;

import java.util.List;
import java.util.Random;

import me.index.config.parameters.enums.IndexType;
import me.index.config.parameters.enums.LossFunction;
import me.index.task.Index;
import me.index.task.inet.NetIndex;

public record NeuralNetworkParams(
        IndexType type,
        LossFunction.Choice loss,
        double rate,
        List<Integer> layers,
        int epochs,
        int batchSize,
        long seed,
        String suffix
) implements IndexParams {
    public NeuralNetworkParams {
        layers = List.copyOf(layers);
    }

    @Override
    public String label() {
        return type.label() + "/" + loss.label() + suffix;
    }

    @Override
    public Index createIndex() {
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
