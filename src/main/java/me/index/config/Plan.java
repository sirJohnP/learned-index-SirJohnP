package me.index.config;

import me.index.config.parameters.enums.DataSize;
import me.index.config.parameters.enums.IndexType;
import me.index.config.parameters.enums.Keyset;
import me.index.config.parameters.enums.LossFunction;
import me.index.config.parameters.enums.MaxErr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public record Plan(List<Run> runs, Path outDir) {
    public Plan {
        runs = List.copyOf(runs);
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("the configuration describes no benchmark run");
        }
    }

    public static Plan load(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new IOException("cannot read config file " + file.toAbsolutePath(), e);
        }
        try {
            return read(props);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid config " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public static Plan read(Properties props) {
        Keys keys = new Keys(props);

        List<Keyset> keysets = keys.requiredEnums(Keys.DATA_KEYSET, Keyset.values());
        List<DataSize> sizes = keys.requiredEnums(Keys.DATA_SIZE, DataSize.values());
        List<Long> dataSeeds = keys.longs(Keys.DATA_SEED, Keys.DEFAULT_DATA_SEED);

        List<IndexType> types = keys.requiredEnums(Keys.INDEX_TYPES, IndexType.values());
        List<MaxErr> errs = keys.enums(Keys.INDEX_ERR, MaxErr.values(), Keys.DEFAULT_ERR);

        List<List<Integer>> shapes = keys.layerShapes(Keys.NET_LAYERS, Keys.DEFAULT_LAYERS);
        List<LossFunction.Choice> losses = keys.losses(Keys.NET_LOSS, Keys.DEFAULT_LOSS);
        List<Double> rates = keys.positiveDoubles(Keys.NET_RATE);
        List<Integer> epochs = keys.positiveInts(Keys.NET_EPOCHS, Keys.DEFAULT_EPOCHS);
        List<Integer> batches = keys.positiveInts(Keys.NET_BATCH, Keys.DEFAULT_BATCH);
        List<Long> netSeeds = keys.longs(Keys.NET_SEED, Keys.DEFAULT_NET_SEED);

        Path outDir = Path.of(keys.single(Keys.OUT_PATH, Keys.DEFAULT_OUT_PATH));
        keys.rejectUnknown();

        List<IndexSpec> indexes = indexes(types, errs, losses, rates, shapes, epochs, batches, netSeeds);
        return new Plan(runs(keysets, sizes, dataSeeds, indexes), outDir);
    }

    private static List<IndexSpec> indexes(List<IndexType> types, List<MaxErr> errs,
                                           List<LossFunction.Choice> losses, List<Double> rates,
                                           List<List<Integer>> shapes, List<Integer> epochs,
                                           List<Integer> batches, List<Long> seeds) {
        List<IndexSpec> indexes = new ArrayList<>();
        for (IndexType type : types) {
            if (type.isPla()) {
                for (MaxErr err : errs) {
                    indexes.add(new IndexSpec.Pla(type, err));
                }
            } else if (type.isNet()) {
                nets(indexes, type, losses, rates, shapes, epochs, batches, seeds);
            } else {
                indexes.add(new IndexSpec.Search(type));
            }
        }
        return indexes;
    }

    private static void nets(List<IndexSpec> indexes, IndexType type, List<LossFunction.Choice> losses,
                             List<Double> rates, List<List<Integer>> shapes, List<Integer> epochs,
                             List<Integer> batches, List<Long> seeds) {
        for (LossFunction.Choice loss : losses) {
            for (double rate : rates.isEmpty() ? List.of(loss.learningRate()) : rates) {
                for (List<Integer> shape : shapes) {
                    for (int epoch : epochs) {
                        for (int batch : batches) {
                            for (long seed : seeds) {
                                List<String> varied = new ArrayList<>();
                                if (shapes.size() > 1) {
                                    varied.add(shapeName(shape));
                                }
                                if (rates.size() > 1) {
                                    varied.add("lr=" + rate);
                                }
                                if (epochs.size() > 1) {
                                    varied.add("ep=" + epoch);
                                }
                                if (batches.size() > 1) {
                                    varied.add("bs=" + batch);
                                }
                                if (seeds.size() > 1) {
                                    varied.add("sd=" + seed);
                                }
                                indexes.add(new IndexSpec.Net(type, loss, rate, shape, epoch, batch, seed,
                                        varied.isEmpty() ? "" : " [" + String.join(" ", varied) + "]"));
                            }
                        }
                    }
                }
            }
        }
    }

    private static String shapeName(List<Integer> shape) {
        if (shape.isEmpty()) {
            return Keys.NO_LAYERS;
        }
        StringBuilder sb = new StringBuilder();
        for (int size : shape) {
            sb.append(sb.isEmpty() ? "" : ",").append(size);
        }
        return sb.toString();
    }

    private static List<Run> runs(List<Keyset> keysets, List<DataSize> sizes, List<Long> seeds,
                                  List<IndexSpec> indexes) {
        List<Run> runs = new ArrayList<>();
        for (Keyset keyset : keysets) {
            for (DataSize size : sizes) {
                for (long seed : seeds) {
                    Dataset dataset = new Dataset(keyset, size, seed);
                    runs.add(new Run(dataset, indexes, label(dataset, seeds.size() > 1)));
                }
            }
        }
        return runs;
    }

    private static String label(Dataset dataset, boolean withSeed) {
        String label = dataset.keyset().name().substring(1) + "_" + dataset.size().name().substring(1);
        return withSeed ? label + "_seed" + dataset.seed() : label;
    }
}
