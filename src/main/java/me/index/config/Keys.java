package me.index.config;

import me.index.config.parameters.enums.LossFunction;
import me.index.config.parameters.enums.MaxErr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

public final class Keys {
    public static final String DATA_KEYSET = "data.keyset";
    public static final String DATA_SIZE = "data.size";
    public static final String DATA_SEED = "data.seed";

    public static final String INDEX_TYPES = "index.types";
    public static final String INDEX_ERR = "index.err";

    public static final String NET_LAYERS = "net.layers";
    public static final String NET_LOSS = "net.train.loss";
    public static final String NET_RATE = "net.train.rate";
    public static final String NET_EPOCHS = "net.train.epochs";
    public static final String NET_BATCH = "net.train.batch.size";
    public static final String NET_SEED = "net.train.seed";

    public static final String OUT_PATH = "out.path";

    public static final long DEFAULT_DATA_SEED = 42L;
    public static final MaxErr DEFAULT_ERR = MaxErr._32;
    public static final List<Integer> DEFAULT_LAYERS = List.of(4, 4);
    public static final LossFunction DEFAULT_LOSS = LossFunction._squared;
    public static final int DEFAULT_EPOCHS = 100;
    public static final int DEFAULT_BATCH = 32;
    public static final long DEFAULT_NET_SEED = 42L;
    public static final String DEFAULT_OUT_PATH = "out";

    public static final String NO_LAYERS = "none";

    private static final String SEPARATOR = ";";

    private final Properties props;
    private final Set<String> asked = new LinkedHashSet<>();

    public Keys(Properties props) {
        this.props = props;
    }

    public <E extends Enum<E>> List<E> enums(String key, E[] allowed, E fallback) {
        return values(key, allowed(allowed), text -> constant(key, allowed, text), List.of(fallback));
    }

    public <E extends Enum<E>> List<E> requiredEnums(String key, E[] allowed) {
        return required(key, allowed(allowed), text -> constant(key, allowed, text));
    }

    public List<Integer> positiveInts(String key, int fallback) {
        return values(key, "a positive int", text -> positive(key, Integer.parseInt(text)), List.of(fallback));
    }

    public List<Long> longs(String key, long fallback) {
        return values(key, "an integer", Long::parseLong, List.of(fallback));
    }

    public List<Double> positiveDoubles(String key) {
        return values(key, "a finite positive number", text -> finitePositive(key, Double.parseDouble(text)), List.of());
    }

    public List<List<Integer>> layerShapes(String key, List<Integer> fallback) {
        return values(key, "comma-separated positive ints, or " + NO_LAYERS,
                text -> shape(key, text), List.of(fallback));
    }

    public List<LossFunction.Choice> losses(String key, LossFunction fallback) {
        return values(key, allowed(LossFunction.values()) + ", optionally with a parameter in parentheses",
                text -> loss(key, text), List.of(new LossFunction.Choice(fallback, fallback.defaultParameter())));
    }

    public String single(String key, String fallback) {
        List<String> parts = raw(key);
        if (parts.isEmpty()) {
            return fallback;
        }
        if (parts.size() > 1) {
            throw new IllegalArgumentException("property '" + key + "': expected a single value, got "
                    + parts.size() + "; this key does not support '" + SEPARATOR + "' lists");
        }
        return parts.get(0);
    }

    public void rejectUnknown() {
        List<String> unknown = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!asked.contains(key)) {
                unknown.add(key);
            }
        }
        if (unknown.isEmpty()) {
            return;
        }
        List<String> known = new ArrayList<>(asked);
        unknown.sort(null);
        known.sort(null);
        throw new IllegalArgumentException("unknown propert" + (unknown.size() == 1 ? "y " : "ies ")
                + unknown + "; known properties: " + known);
    }

    private List<String> raw(String key) {
        asked.add(key);
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(SEPARATOR, -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("property '" + key + "': empty value in the list '"
                        + value.trim() + "'");
            }
            parts.add(trimmed);
        }
        return parts;
    }

    private <T> List<T> values(String key, String expected, Function<String, T> parse, List<T> fallback) {
        List<String> parts = raw(key);
        return parts.isEmpty() ? fallback : parseAll(key, expected, parse, parts);
    }

    private <T> List<T> required(String key, String expected, Function<String, T> parse) {
        List<String> parts = raw(key);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("missing required property '" + key + "'; expected " + expected);
        }
        return parseAll(key, expected, parse, parts);
    }

    private <T> List<T> parseAll(String key, String expected, Function<String, T> parse, List<String> parts) {
        List<T> out = new ArrayList<>(parts.size());
        for (String part : parts) {
            try {
                out.add(parse.apply(part));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("property '" + key + "': expected " + expected
                        + ", got '" + part + "'", e);
            }
        }
        return List.copyOf(out);
    }

    private static <E extends Enum<E>> String allowed(E[] values) {
        return "one of " + Arrays.toString(values);
    }

    private static <E extends Enum<E>> E constant(String key, E[] allowed, String text) {
        for (E candidate : allowed) {
            if (candidate.name().equals(text)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("property '" + key + "': unknown value '" + text
                + "'; allowed values: " + Arrays.toString(allowed));
    }

    private static int positive(String key, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("property '" + key + "': expected a positive int, got " + value);
        }
        return value;
    }

    private static double finitePositive(String key, double value) {
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("property '" + key
                    + "': expected a finite positive number, got " + value);
        }
        return value;
    }

    private static List<Integer> shape(String key, String text) {
        if (NO_LAYERS.equals(text)) {
            return List.of();
        }
        List<Integer> sizes = new ArrayList<>();
        for (String part : text.split(",", -1)) {
            sizes.add(positive(key, Integer.parseInt(part.trim())));
        }
        return List.copyOf(sizes);
    }

    private static LossFunction.Choice loss(String key, String text) {
        int open = text.indexOf('(');
        if (open < 0) {
            LossFunction loss = constant(key, LossFunction.values(), text);
            return new LossFunction.Choice(loss, loss.defaultParameter());
        }
        if (!text.endsWith(")")) {
            throw new IllegalArgumentException("property '" + key + "': unbalanced parentheses in '" + text + "'");
        }
        LossFunction loss = constant(key, LossFunction.values(), text.substring(0, open).trim());
        if (!loss.takesParameter()) {
            throw new IllegalArgumentException("property '" + key + "': " + loss
                    + " takes no parameter, got '" + text + "'");
        }
        String inner = text.substring(open + 1, text.length() - 1).trim();
        return new LossFunction.Choice(loss, finitePositive(key, Double.parseDouble(inner)));
    }
}
