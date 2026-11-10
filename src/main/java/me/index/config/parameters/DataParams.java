package me.index.config.parameters;

import me.index.config.parameters.enums.DataSize;
import me.index.config.parameters.enums.Keyset;

public record DataParams(Keyset keyset, DataSize size, long seed) {
    public DataParams {
        if (!keyset.isLong && !keyset.isSOSD() && size == DataSize._max) {
            throw new IllegalArgumentException("size " + size
                    + " is not available for 32-bit synthetic keyset " + keyset);
        }
    }
}
