package me.index.config.parameters;

import me.index.config.parameters.enums.IndexType;
import me.index.config.parameters.enums.MaxErr;
import me.index.task.Index;
import me.index.task.isegment.PlaIndex;

public record PiecewiseLinearParams(IndexType type, MaxErr err) implements IndexParams {
    @Override
    public String suffix() {
        return "";
    }

    @Override
    public String label() {
        return type.label() + "(err=" + err.value + ")";
    }

    @Override
    public Index createIndex() {
        return new PlaIndex(type.window(), err.value);
    }
}
