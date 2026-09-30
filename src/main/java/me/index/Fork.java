package me.index;

import me.index.config.IndexSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

final class Fork implements AutoCloseable {
    static final List<String> JVM_OPTIONS = List.of(
            "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-Xms4g", "-Xmx4g", "-XX:+AlwaysPreTouch");
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final int OUT_OF_MEMORY = 3;

    private final Path dir;
    private final Path config;
    private final Path dataDir;
    private final Thread hook = new Thread(this::cleanUp);
    private volatile Process running;
    private long checksum;

    private Fork(Path dir, Path dataDir) {
        this.dir = dir;
        this.config = dir.resolve("config.properties");
        this.dataDir = dataDir.toAbsolutePath();
    }

    static Fork open(Path configFile, Path dataDir) throws IOException {
        Fork fork = new Fork(Files.createTempDirectory("hw-template-"), dataDir);
        Runtime.getRuntime().addShutdownHook(fork.hook);
        Files.copy(configFile, fork.config);
        return fork;
    }

    long checksum() {
        return checksum;
    }

    Row measure(int run, int row, IndexSpec spec) throws IOException {
        String name = "run" + run + "-row" + row;
        Path result = dir.resolve(name + ".properties");
        Path predictions = dir.resolve(name + ".predictions");

        System.out.flush();
        Process process = new ProcessBuilder(command(run, row, result, predictions)).inheritIO().start();
        running = process;
        boolean exited = waitFor(process);
        running = null;

        Row measured = Files.exists(result)
                ? Row.read(result, predictions)
                : Row.skipped(spec.label(), "no result");
        if (!exited) {
            return measured.failed("timed out after " + TIMEOUT.toMinutes() + " min");
        }
        if (process.exitValue() == OUT_OF_MEMORY) {
            return measured.failed("out of memory");
        }
        if (process.exitValue() != 0) {
            return measured.failed("JVM exited with code " + process.exitValue());
        }
        checksum += measured.checksum();
        return measured;
    }

    @Override
    public void close() {
        Runtime.getRuntime().removeShutdownHook(hook);
        delete(dir);
    }

    private List<String> command(int run, int row, Path result, Path predictions) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(JVM_OPTIONS);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Worker.class.getName());
        command.add(config.toString());
        command.add(dataDir.toString());
        command.add(Integer.toString(run));
        command.add(Integer.toString(row));
        command.add(result.toString());
        command.add(predictions.toString());
        return command;
    }

    private static boolean waitFor(Process process) throws IOException {
        try {
            if (process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
            process.destroyForcibly().waitFor();
            return false;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for the JVM of a row", e);
        }
    }

    private void cleanUp() {
        Process process = running;
        if (process != null) {
            process.destroyForcibly();
        }
        delete(dir);
    }

    private static void delete(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            System.err.println("[WARNING] cannot delete " + dir + ": " + e.getMessage());
        }
    }
}
