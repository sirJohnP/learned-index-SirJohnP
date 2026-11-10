package me.index.config.parameters;

import me.index.config.parameters.enums.IndexType;
import me.index.task.Index;

public record SearchParams(IndexType type) implements IndexParams {
    @Override
    public String suffix() {
        return "";
    }

    @Override
    public String label() {
        return type.label();
    }

    @Override
    public Index createIndex() {
        return type.search().create();
    }
}
