package me.index.libs.execute.parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

public record BenchmarkArguments(
        DataParams dataset,
        IndexParams index,
        Path dataDir,
        Path resultFile,
        boolean plot
) {
    public BenchmarkArguments {
        dataDir = dataDir.toAbsolutePath().normalize();
        resultFile = resultFile.toAbsolutePath().normalize();
    }

    public List<String> arguments() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("--keyset", dataset.keyset().name());
        values.put("--size", dataset.size().name());
        values.put("--data-seed", Long.toString(dataset.seed()));
        values.put("--data-dir", dataDir.toString());
        values.put("--index", index.type().name());
        values.put("--result", resultFile.toString());
        values.put("--plot", Boolean.toString(plot));
        switch (index) {
            case SearchParams ignored -> {
            }
            case PiecewiseLinearParams pla -> values.put("--eps", Integer.toString(pla.err().value));
            case NeuralNetworkParams net -> {
                values.put("--layers", net.layers().isEmpty() ? "none"
                        : net.layers().stream().map(String::valueOf).collect(Collectors.joining(",")));
                values.put("--loss", net.loss().loss().name());
                if (net.loss().loss().takesParameter()) {
                    values.put("--loss-parameter", Double.toString(net.loss().parameter()));
                }
                values.put("--learning-rate", Double.toString(net.rate()));
                values.put("--epochs", Integer.toString(net.epochs()));
                values.put("--batch-size", Integer.toString(net.batchSize()));
                values.put("--model-seed", Long.toString(net.seed()));
                values.put("--suffix", net.suffix());
            }
        }
        List<String> arguments = new ArrayList<>(2 * values.size());
        values.forEach((key, value) -> {
            arguments.add(key);
            arguments.add(value);
        });
        return List.copyOf(arguments);
    }

    public static BenchmarkArguments parse(String[] args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("each option needs a value");
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("expected an option, got " + args[i]);
            if (values.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("duplicate option " + args[i]);
            }
        }
        DataParams dataset = new DataParams(Keyset.valueOf(take(values, "--keyset")),
                DataSize.valueOf(take(values, "--size")), Long.parseLong(take(values, "--data-seed")));
        Path dataDir = Path.of(take(values, "--data-dir"));
        Path result = Path.of(take(values, "--result"));
        boolean plot = switch (take(values, "--plot")) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("--plot must be true or false");
        };
        IndexType type = IndexType.valueOf(take(values, "--index"));
        IndexParams index;
        if (type.isSearch()) {
            index = new SearchParams(type);
        } else if (type.isPla()) {
            int eps = Integer.parseInt(take(values, "--eps"));
            index = new PiecewiseLinearParams(type, MaxErr.valueOf("_" + eps));
        } else {
            String shape = take(values, "--layers");
            List<Integer> layers = shape.equals("none") ? List.of()
                    : Arrays.stream(shape.split(",", -1))
                    .map(String::trim)
                    .map(BenchmarkArguments::positiveInt)
                    .toList();
            LossFunction loss = LossFunction.valueOf(take(values, "--loss"));
            double parameter = loss.takesParameter() ? positiveDouble(take(values, "--loss-parameter"))
                    : loss.defaultParameter();
            double rate = positiveDouble(take(values, "--learning-rate"));
            int epochs = positiveInt(take(values, "--epochs"));
            int batchSize = positiveInt(take(values, "--batch-size"));
            long seed = Long.parseLong(take(values, "--model-seed"));
            String suffix = values.remove("--suffix");
            if (suffix == null) throw new IllegalArgumentException("missing --suffix");
            index = new NeuralNetworkParams(type, new LossFunction.Choice(loss, parameter), rate,
                    layers, epochs, batchSize, seed, suffix);
        }
        if (!values.isEmpty()) throw new IllegalArgumentException("unexpected options " + values.keySet());
        return new BenchmarkArguments(dataset, index, dataDir, result, plot);
    }

    private static String take(Map<String, String> values, String key) {
        String value = values.remove(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }

    private static int positiveInt(String text) {
        int value = Integer.parseInt(text);
        if (value <= 0) throw new IllegalArgumentException("expected a positive integer, got " + text);
        return value;
    }

    private static double positiveDouble(String text) {
        double value = Double.parseDouble(text);
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("expected a finite positive number, got " + text);
        }
        return value;
    }
}
