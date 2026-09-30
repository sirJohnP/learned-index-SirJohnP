package me.index.config;

import me.index.config.parameters.enums.DataSize;
import me.index.config.parameters.enums.Keyset;

public record Dataset(Keyset keyset, DataSize size, long seed) {
    public Dataset {
        if (!keyset.isLong && !keyset.isSOSD() && size == DataSize._max) {
            throw new IllegalArgumentException("incorrect properties: " + Keys.DATA_SIZE + "=" + DataSize._max
                    + " is not available for 32-bit synthetic keysets (" + Keys.DATA_KEYSET + "=" + keyset + ")");
        }
    }
}
