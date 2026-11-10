package me.index.libs.execute;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class TempDirectory implements AutoCloseable {
    private final Path directory;
    private final Runnable beforeCleanup;
    private final PrintStream err;
    private final Thread shutdown = new Thread(this::cleanup);
    private boolean cleaned;

    private TempDirectory(Path directory, Runnable beforeCleanup, PrintStream err) {
        this.directory = directory;
        this.beforeCleanup = beforeCleanup;
        this.err = err;
    }

    public static TempDirectory create(Path tempRoot, Runnable beforeCleanup, PrintStream err) throws IOException {
        TempDirectory tempDirectory = new TempDirectory(
                Files.createTempDirectory(tempRoot, "hw-template-"), beforeCleanup, err);
        try {
            Runtime.getRuntime().addShutdownHook(tempDirectory.shutdown);
        } catch (RuntimeException e) {
            tempDirectory.cleanup();
            throw e;
        }
        return tempDirectory;
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void close() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdown);
        } finally {
            cleanup();
        }
    }

    private synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        beforeCleanup.run();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        } catch (IOException e) {
            err.println("[WARNING] cannot delete " + directory + ": " + e.getMessage());
        }
    }
}
