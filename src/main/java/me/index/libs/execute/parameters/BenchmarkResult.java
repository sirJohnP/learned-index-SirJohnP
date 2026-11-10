package me.index.libs.execute.parameters;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public record BenchmarkResult(BenchmarkSummary summary, int[] predictions) {
    private static final int FORMAT = 0x4C494E32;

    public void write(OutputStream output) throws IOException {
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(FORMAT);
        data.writeUTF(summary.id());
        data.writeBoolean(summary.built());
        if (summary.built()) {
            data.writeDouble(summary.buildMs());
            data.writeInt(summary.maxErr());
            data.writeLong(summary.bytes());
            data.writeDouble(summary.minIterationNs());
            data.writeDouble(summary.maxIterationNs());
            data.writeDouble(summary.meanIterationNs());
            data.writeLong(summary.checksum());
        } else {
            data.writeUTF(summary.note());
        }
        data.writeInt(predictions == null ? -1 : predictions.length);
        if (predictions != null) for (int position : predictions) data.writeInt(position);
        data.flush();
    }

    public static BenchmarkResult read(InputStream input, int maxPredictions) throws IOException {
        DataInputStream data = new DataInputStream(input);
        if (data.readInt() != FORMAT) throw new IOException("unknown result format");
        String id = data.readUTF();
        BenchmarkSummary summary = data.readBoolean()
                ? new BenchmarkSummary(id, data.readDouble(), data.readInt(), data.readLong(),
                data.readDouble(), data.readDouble(), data.readDouble(), data.readLong(), null)
                : BenchmarkSummary.skipped(id, data.readUTF());
        int count = data.readInt();
        if (count < -1 || count == 0 || count > maxPredictions || count >= 0 && !summary.shouldPlot()) {
            throw new IOException("invalid prediction count: " + count);
        }
        int[] predictions = count < 0 ? null : new int[count];
        if (predictions != null) {
            for (int i = 0; i < count; i++) {
                predictions[i] = data.readInt();
                if (predictions[i] < 0 || predictions[i] >= count) {
                    throw new IOException("prediction outside keyset: " + predictions[i]);
                }
            }
        }
        if (data.read() != -1) throw new IOException("unexpected data after result");
        return new BenchmarkResult(summary, predictions);
    }
}
