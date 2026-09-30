package me.index;

import me.index.config.Dataset;
import me.index.config.Keys;
import me.index.config.Plan;
import me.index.config.Run;
import me.index.libs.view.Plot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Main {
    private static final Path CONFIG_FILE = Path.of("config.properties");
    private static final int PREVIEW = 3;
    private static final String TABLE_FORMAT = "%-40s %10s %8s %10s %12s %12s%n";

    private static final Plot.Color[] PALETTE = {
            Plot.RED, Plot.GREEN, Plot.ORANGE, Plot.PURPLE, Plot.BROWN, Plot.GRAY};

    static void main(String[] args) {
        try {
            run(CONFIG_FILE, dataDir(args));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(Path configFile, Path dataDir) throws IOException {
        Plan plan = Plan.load(configFile);
        try (Fork fork = Fork.open(configFile, dataDir)) {
            reportRuntime();
            for (int i = 0; i < plan.runs().size(); i++) {
                benchmark(fork, i, plan.runs().get(i), dataDir, plan.outDir());
            }
            System.out.println();
            System.out.println("checksum: " + fork.checksum());
        }
    }

    private static void benchmark(Fork fork, int runNo, Run run, Path dataDir, Path outDir) throws IOException {
        int[] keys = new Context(run.dataset(), dataDir).intKeys();

        describe(run, keys);
        List<Row> rows = measureAll(fork, runNo, run);
        plot(run, outDir, keys, rows);
    }

    private static Path dataDir(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-")) {
                usage("unknown option " + arg);
            }
        }
        if (args.length > 1) {
            usage("too many arguments");
        }
        return Path.of(args.length == 1 ? args[0] : "");
    }

    private static void usage(String problem) {
        System.err.println("error: " + problem);
        System.err.println("usage: Main [sosd-data-dir]");
        System.err.println("the configuration is always read from " + CONFIG_FILE);
        System.exit(2);
    }

    private static void reportRuntime() {
        System.out.println("each row runs in its own JVM: " + String.join(" ", Fork.JVM_OPTIONS));
    }

    private static void describe(Run run, int[] keys) {
        Dataset dataset = run.dataset();
        System.out.println();
        System.out.println("=== " + Keys.DATA_KEYSET + "=" + dataset.keyset()
                + "  " + Keys.DATA_SIZE + "=" + dataset.size()
                + "  " + Keys.DATA_SEED + "=" + dataset.seed() + " ===");
        System.out.println("distinct keys: " + keys.length);
        System.out.println(preview(keys));
        System.out.println("queries: " + Worker.QUERIES + " uniform over the keyset, warmup rounds "
                + Worker.WARMUP_ROUNDS + ", measured rounds " + Worker.MEASURED_ROUNDS);
        System.out.println();
    }

    private static List<Row> measureAll(Fork fork, int runNo, Run run) throws IOException {
        System.out.printf(Locale.US, TABLE_FORMAT, "index", "build ms", "maxErr", "size KiB", "ns/lookup", "mean ns");
        List<Row> rows = new ArrayList<>(run.indexes().size());
        for (int i = 0; i < run.indexes().size(); i++) {
            Row row = fork.measure(runNo, i, run.indexes().get(i));
            report(row);
            rows.add(row);
        }
        return rows;
    }

    private static void report(Row row) {
        if (!row.built()) {
            System.out.printf(Locale.US, "%-40s %s%n", row.id(), row.note());
            return;
        }
        System.out.printf(Locale.US, TABLE_FORMAT, row.id(),
                String.format(Locale.US, "%.1f", row.buildMs()),
                Integer.toString(row.maxErr()),
                String.format(Locale.US, "%.1f", row.bytes() / 1024.0),
                String.format(Locale.US, "%.2f", row.bestNs()),
                String.format(Locale.US, "%.2f", row.meanNs()));
    }

    private static void plot(Run run, Path outDir, int[] keys, List<Row> rows) throws IOException {
        List<Row> models = new ArrayList<>();
        for (Row row : rows) {
            if (row.predictions() != null) {
                models.add(row);
            }
        }
        if (models.isEmpty()) {
            return;
        }

        int n = keys.length;
        long[] xs = new long[n];
        long[] truePos = new long[n];
        for (int i = 0; i < n; i++) {
            xs[i] = keys[i];
            truePos[i] = i;
        }

        Plot plot = new Plot(900, 600)
                .title("Keys - Positions")
                .xlabel("keys")
                .ylabel("positions")
                .squareMarkers(n >= 20_000)
                .scatter(xs, truePos, Plot.BLUE, "true_positions", 0.1);
        for (int m = 0; m < models.size(); m++) {
            Row row = models.get(m);
            plot.scatter(xs, row.readPredictions(), PALETTE[m % PALETTE.length], row.id(), 0.1);
        }

        Files.createDirectories(outDir);
        Path file = outDir.resolve(run.label() + ".pdf");
        plot.save(file.toString());
        System.out.println("plot written to " + file.toAbsolutePath());
    }

    static String preview(int[] keys) {
        StringBuilder sb = new StringBuilder();
        if (keys.length <= 2 * PREVIEW) {
            for (int i = 0; i < keys.length; i++) {
                sb.append(i > 0 ? " " : "").append(keys[i]);
            }
            return sb.toString();
        }
        for (int i = 0; i < PREVIEW; i++) {
            sb.append(keys[i]).append(' ');
        }
        sb.append("...");
        for (int i = keys.length - PREVIEW; i < keys.length; i++) {
            sb.append(' ').append(keys[i]);
        }
        return sb.toString();
    }
}
