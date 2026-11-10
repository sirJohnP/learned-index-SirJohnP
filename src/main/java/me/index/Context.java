package me.index;

import me.index.config.parameters.DataParams;
import me.index.libs.io.SosdReader;
import me.index.libs.math.KeyGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public record Context(DataParams dataset, long[] keys) {
    public Context(DataParams dataset, long[] keys) {
        this.dataset = dataset;
        this.keys = keys;

        if (keys.length < 2) {
            throw new IllegalStateException("keyset " + dataset.keyset() + " with " + dataset.size()
                    + " yielded " + keys.length + " distinct key(s); at least 2 are required");
        }
        for (int i = 1; i < keys.length; i++) {
            if (keys[i - 1] >= keys[i]) {
                throw new IllegalStateException(
                        "keyset " + dataset.keyset() + " is not strictly increasing: keys[" + (i - 1)
                                + "] = " + keys[i - 1] + " >= keys[" + i + "] = " + keys[i]
                                + "; the pipeline treats the array index as the key's rank, so the input must be sorted"
                                + " and free of duplicates");
            }
        }
    }

    public static Context load(DataParams dataset, Path dataDir) throws IOException {
        long[] keys = switch (dataset.keyset().source) {
            case GAUSSIAN -> KeyGenerator.gaussian(
                    dataset.size().size, dataset.keyset().isLong, new Random(dataset.seed())
            );
            case LOGNORMAL -> KeyGenerator.lognormal(
                    dataset.size().size, dataset.keyset().isLong, new Random(dataset.seed())
            );
            case SOSD -> {
                try (InputStream input = Files.newInputStream(dataDir.resolve(dataset.keyset().fileName()))) {
                    yield SosdReader.read(input, dataset.size().size, dataset.keyset().isLong);
                }
            }
        };
        return new Context(dataset, keys);
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
