package me.index.config;

import java.util.List;

public record Run(Dataset dataset, List<IndexSpec> indexes, String label) {
    public Run {
        indexes = List.copyOf(indexes);
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("property '" + Keys.INDEX_TYPES + "' produced no indexes");
        }
    }
}
