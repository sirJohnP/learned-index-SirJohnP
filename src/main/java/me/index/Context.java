package me.index;

import me.index.config.Dataset;
import me.index.config.parameters.enums.Keyset;
import me.index.libs.io.ReadUtils;
import me.index.libs.math.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

public final class Context {
    public final Dataset dataset;
    public final long[] keys;

    public Context(Dataset dataset, Path dataDir) throws IOException {
        this.dataset = dataset;
        Random rnd = new Random(dataset.seed());
        Keyset keyset = dataset.keyset();
        int size = dataset.size().size;

        this.keys = switch (keyset.source) {
            case SOSD -> ReadUtils.read(dataDir.resolve(keyset.fileName()), size,
                    keyset.isLong, keyset.needShift, keyset.needPlusOne);
            case GAUSSIAN -> Utils.genKeysGaussian(size, keyset.isLong, rnd);
            case LOGNORMAL -> Utils.genKeysLognormal(size, keyset.isLong, rnd);
        };

        if (keys.length < 2) {
            throw new IllegalStateException("keyset " + keyset + " with " + dataset.size()
                    + " yielded " + keys.length + " distinct key(s); at least 2 are required");
        }
        for (int i = 1; i < keys.length; i++) {
            if (keys[i - 1] >= keys[i]) {
                throw new IllegalStateException("keyset " + keyset + " is not strictly increasing: keys[" + (i - 1)
                        + "] = " + keys[i - 1] + " >= keys[" + i + "] = " + keys[i]
                        + "; the pipeline treats the array index as the key's rank, so the input must be sorted"
                        + " and free of duplicates");
            }
        }
    }

    public int[] intKeys() {
        int[] out = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            long key = keys[i];
            if (key < Integer.MIN_VALUE || key > Integer.MAX_VALUE) {
                throw new IllegalStateException("keyset " + dataset.keyset() + " does not fit into int: keys["
                        + i + "] = " + key + "; pick a 32-bit keyset for the benchmark");
            }
            out[i] = (int) key;
        }
        return out;
    }
}
