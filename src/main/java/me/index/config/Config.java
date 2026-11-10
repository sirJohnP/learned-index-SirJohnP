package me.index.config;

import me.index.config.parameters.DataParams;
import me.index.config.parameters.IndexParams;
import me.index.config.parameters.NeuralNetworkParams;
import me.index.config.parameters.PiecewiseLinearParams;
import me.index.config.parameters.SearchParams;
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
import java.util.Set;

public final class Config {
    private static final Set<String> PROPERTIES = Set.of(
            "data.keyset", "data.size", "data.seed",
            "index.types", "index.err",
            "net.layers", "net.train.loss", "net.train.rate",
            "net.train.epochs", "net.train.batch.size", "net.train.seed",
            "out.path");

    private final List<DataParams> dataParams;
    private final List<IndexParams> indexParams;
    private final Path outDir;
    private final boolean includeDataSeedInFileNames;

    private Config(List<DataParams> dataParams,
                   List<IndexParams> indexParams,
                   Path outDir,
                   boolean includeDataSeedInFileNames
    ) {
        this.dataParams = List.copyOf(dataParams);
        this.indexParams = List.copyOf(indexParams);
        this.outDir = outDir;
        this.includeDataSeedInFileNames = includeDataSeedInFileNames;
    }

    public List<DataParams> dataParams() {
        return dataParams;
    }

    public List<IndexParams> indexParams() {
        return indexParams;
    }

    public Path outDir() {
        return outDir;
    }

    public Path plotPath(DataParams data) {
        String name = data.keyset().name().substring(1) + "_" +
                data.size().name().substring(1);
        if (includeDataSeedInFileNames) name += "_seed" + data.seed();
        return outDir.resolve(name + ".pdf");
    }

    public static Config read(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            throw new IOException("cannot read config file " + file.toAbsolutePath(), e);
        }
        try {
            return Config.parse(properties);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid config " +
                    file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public static Config parse(Properties properties) {
        PropertyParser parser = new PropertyParser(properties);
        List<Keyset> keysets = parser.requiredEnums("data.keyset", Keyset.values());
        List<DataSize> sizes = parser.requiredEnums("data.size", DataSize.values());
        List<Long> dataSeeds = parser.longs("data.seed", 42L);

        List<IndexType> types = parser.requiredEnums("index.types", IndexType.values());
        List<MaxErr> errs = parser.enums("index.err", MaxErr.values(), MaxErr._32);

        List<List<Integer>> shapes = parser.layerShapes("net.layers", List.of(4, 4));
        List<LossFunction.Choice> losses = parser.losses("net.train.loss", LossFunction._squared);
        List<Double> rates = parser.positiveDoubles("net.train.rate");
        List<Integer> epochs = parser.positiveInts("net.train.epochs", 100);
        List<Integer> batches = parser.positiveInts("net.train.batch.size", 32);
        List<Long> netSeeds = parser.longs("net.train.seed", 42L);

        Path outDir = Path.of(parser.single("out.path", "out"));
        parser.rejectUnknown(PROPERTIES);

        return new Config(
                dataParams(keysets, sizes, dataSeeds),
                indexParams(types, errs, losses, rates, shapes, epochs, batches, netSeeds),
                outDir,
                dataSeeds.size() > 1
        );
    }

    private static List<DataParams> dataParams(List<Keyset> keysets, List<DataSize> sizes, List<Long> seeds) {
        List<DataParams> datasets = new ArrayList<>();
        for (Keyset keyset : keysets) {
            for (DataSize size : sizes) {
                for (long seed : seeds) datasets.add(new DataParams(keyset, size, seed));
            }
        }
        return datasets;
    }

    private static List<IndexParams> indexParams(List<IndexType> types, List<MaxErr> errs,
                                                 List<LossFunction.Choice> losses, List<Double> rates,
                                                 List<List<Integer>> shapes, List<Integer> epochs,
                                                 List<Integer> batches, List<Long> seeds) {
        List<IndexParams> indexes = new ArrayList<>();
        for (IndexType type : types) {
            if (type.isPla()) {
                for (MaxErr err : errs) {
                    indexes.add(new PiecewiseLinearParams(type, err));
                }
            } else if (type.isNet()) {
                indexes.addAll(neuralNetworks(type, losses, rates, shapes, epochs, batches, seeds));
            } else {
                indexes.add(new SearchParams(type));
            }
        }
        return indexes;
    }

    private static List<IndexParams> neuralNetworks(
            IndexType type, List<LossFunction.Choice> losses,
            List<Double> rates, List<List<Integer>> shapes, List<Integer> epochs,
            List<Integer> batches, List<Long> seeds
    ) {
        List<IndexParams> indexes = new ArrayList<>();
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
                                indexes.add(new NeuralNetworkParams(type, loss, rate, shape, epoch, batch, seed,
                                        varied.isEmpty() ? "" : " [" + String.join(" ", varied) + "]"));
                            }
                        }
                    }
                }
            }
        }
        return List.copyOf(indexes);
    }

    private static String shapeName(List<Integer> shape) {
        if (shape.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int size : shape) {
            sb.append(sb.isEmpty() ? "" : ",").append(size);
        }
        return sb.toString();
    }
}
