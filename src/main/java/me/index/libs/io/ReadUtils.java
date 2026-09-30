package me.index.libs.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class ReadUtils {
    private ReadUtils() {
    }

    private static long readLittleEndian(DataInputStream in, boolean eightBytes) throws IOException {
        long result = 0;
        for (int i = 0; i < (eightBytes ? 8 : 4); i++) {
            result |= ((long) in.readUnsignedByte()) << (8 * i);
        }
        return result;
    }

    public static long[] read(Path file, int maxSize, boolean eightBytes, boolean shift, boolean plusOne)
            throws IOException {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
        }
        if (!Files.isReadable(file)) {
            throw new IOException("keyset file is missing or not readable: " + file.toAbsolutePath()
                    + " (SOSD datasets are not bundled with the repo; pass their directory as the second argument)");
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            long declared = readLittleEndian(in, true);
            if (declared < 0 || declared > Integer.MAX_VALUE) {
                throw new IOException("keyset file " + file.toAbsolutePath()
                        + " declares an unsupported element count: " + Long.toUnsignedString(declared));
            }
            int total = (int) declared;
            int wanted = Math.min(total, maxSize);

            long[] keys = new long[wanted];
            int kept = 0;
            for (int read = 0; kept < wanted && read < total; read++) {
                long key = readLittleEndian(in, eightBytes);
                if (shift) {
                    key += Long.MIN_VALUE + (plusOne ? 1L : 0L);
                }
                if (kept == 0 || keys[kept - 1] != key) {
                    keys[kept++] = key;
                }
            }
            return (kept == keys.length) ? keys : Arrays.copyOf(keys, kept);
        } catch (EOFException e) {
            throw new IOException("keyset file " + file.toAbsolutePath()
                    + " ended earlier than its header promised", e);
        }
    }
}
