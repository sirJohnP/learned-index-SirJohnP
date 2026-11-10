package me.index;

import me.index.config.Config;
import me.index.config.parameters.DataParams;
import me.index.config.parameters.IndexParams;
import me.index.libs.execute.parameters.BenchmarkArguments;
import me.index.libs.execute.JvmRunner;
import me.index.libs.execute.parameters.BenchmarkResult;
import me.index.libs.execute.parameters.BenchmarkSummary;
import me.index.libs.execute.TempDirectory;
import me.index.libs.view.PdfReport;
import me.index.libs.view.Plot;
import me.index.libs.view.StringReport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

public class Main {
    private static final Path CONFIG_FILE = Path.of("config.properties");
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    static void main(String[] args) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        Options opts;
        try {
            opts = parse(args);
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            err.println("usage: Main [--plot] [sosd-data-dir]");
            System.exit(2);
            return;
        }
        try (JvmRunner runner = new JvmRunner();
             TempDirectory tempDir = TempDirectory.create(
                     Path.of(System.getProperty("java.io.tmpdir")),
                     runner::close,
                     err
             )
        ) {
            Config config = Config.read(CONFIG_FILE);
            long checksum = 0;
            out.print(StringReport.runtime(Benchmark.JVM_OPTIONS));
            for (DataParams data : config.dataParams()) {
                int[] keys = Context.load(data, opts.dir()).intKeys();
                out.print(StringReport.begin(data, keys));
                Plot plot = null;
                long[] plotKeys = null;
                int series = 0;
                for (IndexParams index : config.indexParams()) {
                    BenchmarkArguments arguments = new BenchmarkArguments(
                            data,
                            index,
                            opts.dir(),
                            tempDir.directory().resolve("result.bin"),
                            opts.plot()
                    );
                    BenchmarkResult result = Benchmark.fork(
                            arguments,
                            runner,
                            Path.of(System.getProperty("java.home"), "bin", "java"),
                            System.getProperty("java.class.path"),
                            TIMEOUT,
                            out,
                            err
                    );
                    BenchmarkSummary summary = result.summary();
                    checksum += summary.checksum();
                    out.print(StringReport.row(summary));
                    if (result.predictions() != null) {
                        if (plot == null) {
                            plotKeys = Arrays.stream(keys).asLongStream().toArray();
                            plot = PdfReport.plot(plotKeys);
                        }
                        PdfReport.addPrediction(plot, plotKeys,
                                Arrays.stream(result.predictions()).asLongStream().toArray(), summary.id(), series++);
                    }
                }
                if (plot != null) {
                    Path file = config.plotPath(data);
                    Files.createDirectories(file.toAbsolutePath().getParent());
                    try (OutputStream output = Files.newOutputStream(file)) {
                        plot.write(output);
                    }
                    out.println("plot written to " + file.toAbsolutePath());
                }
            }
            out.print(StringReport.finish(checksum));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    private record Options(Path dir, boolean plot) {
    }

    private static Options parse(String[] args) {
        Path dir = null;
        boolean plot = false;
        for (String arg : args) {
            if (arg.equals("--plot")) {
                if (plot) throw new IllegalArgumentException("duplicate option --plot");
                plot = true;
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("unknown option " + arg);
            } else {
                if (dir != null) throw new IllegalArgumentException("too many arguments");
                dir = Path.of(arg);
            }
        }
        return new Options(dir == null ? Path.of("") : dir, plot);
    }
}
