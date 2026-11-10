package me.index.config.parameters;

import me.index.config.parameters.enums.IndexType;
import me.index.task.Index;

public sealed interface IndexParams
        permits SearchParams, PiecewiseLinearParams, NeuralNetworkParams {
    IndexType type();

    String suffix();

    String label();

    Index createIndex();
}
