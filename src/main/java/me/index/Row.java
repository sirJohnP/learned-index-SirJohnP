package me.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

record Row(String id, double buildMs, int maxErr, long bytes, double bestNs, double meanNs,
           long checksum, String note, Path predictions) {

    static Row started(String id) {
        return skipped(id, "no result");
    }

    static Row skipped(String id, String note) {
        return new Row(id, Double.NaN, -1, -1, Double.NaN, Double.NaN, 0, note, null);
    }

    boolean built() {
        return note == null;
    }

    boolean plotted() {
        return built() && maxErr > 0;
    }

    Row failed(String problem) {
        return skipped(id, "failed: " + problem);
    }

    void write(Path file) throws IOException {
        Properties props = new Properties();
        props.setProperty("id", id);
        if (built()) {
            props.setProperty("build.ms", Double.toString(buildMs));
            props.setProperty("max.err", Integer.toString(maxErr));
            props.setProperty("bytes", Long.toString(bytes));
            props.setProperty("best.ns", Double.toString(bestNs));
            props.setProperty("mean.ns", Double.toString(meanNs));
            props.setProperty("checksum", Long.toString(checksum));
        } else {
            props.setProperty("note", note);
        }
        Path part = file.resolveSibling(file.getFileName() + ".part");
        try (OutputStream out = Files.newOutputStream(part)) {
            props.store(out, null);
        }
        Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
    }

    static Row read(Path file, Path predictions) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        String id = props.getProperty("id");
        String note = props.getProperty("note");
        if (note != null) {
            return skipped(id, note);
        }
        return new Row(id,
                Double.parseDouble(props.getProperty("build.ms")),
                Integer.parseInt(props.getProperty("max.err")),
                Long.parseLong(props.getProperty("bytes")),
                Double.parseDouble(props.getProperty("best.ns")),
                Double.parseDouble(props.getProperty("mean.ns")),
                Long.parseLong(props.getProperty("checksum")),
                null,
                Files.exists(predictions) ? predictions : null);
    }

    static void writePredictions(Path file, int[] predicted) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(predicted.length);
            for (int position : predicted) {
                out.writeInt(position);
            }
        }
    }

    long[] readPredictions() throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(predictions)))) {
            long[] predicted = new long[in.readInt()];
            for (int i = 0; i < predicted.length; i++) {
                predicted[i] = in.readInt();
            }
            return predicted;
        }
    }
}
