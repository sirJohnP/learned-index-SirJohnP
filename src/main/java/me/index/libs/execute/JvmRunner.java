package me.index.libs.execute;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class JvmRunner implements AutoCloseable {
    private Process running;
    private boolean closed;

    public Exit run(List<String> command, Duration timeout, PrintStream out, PrintStream err) throws IOException {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        Process process;
        synchronized (this) {
            if (closed) throw new IllegalStateException("runner is closed");
            if (running != null) throw new IllegalStateException("another process is running");
            out.flush();
            err.flush();
            process = new ProcessBuilder(command).start();
            running = process;
        }
        try (var streams = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                process.getOutputStream().close();
                Future<Void> stdout = streams.submit(() -> {
                    copy(process.getInputStream(), out);
                    return null;
                });
                Future<Void> stderr = streams.submit(() -> {
                    copy(process.getErrorStream(), err);
                    return null;
                });
                boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!exited) {
                    stop(process);
                    process.waitFor();
                }
                stdout.get();
                stderr.get();
                return new Exit(process.exitValue(), !exited);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for the JVM", e);
            } catch (ExecutionException e) {
                throw new IOException("cannot forward JVM output", e.getCause());
            } finally {
                if (process.isAlive()) stop(process);
                synchronized (this) {
                    running = null;
                }
            }
        }
    }

    private static void copy(InputStream input, PrintStream output) throws IOException {
        try (input) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0; ) {
                output.write(buffer, 0, count);
                output.flush();
                if (output.checkError()) throw new IOException("output stream failed");
            }
        }
    }

    private static void stop(Process process) {
        process.destroyForcibly();
    }

    @Override
    public void close() {
        Process process;
        synchronized (this) {
            closed = true;
            process = running;
        }
        if (process == null) return;
        stop(process);
        boolean interrupted = false;
        while (process.isAlive()) {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    public record Exit(int code, boolean timedOut) {
    }
}
