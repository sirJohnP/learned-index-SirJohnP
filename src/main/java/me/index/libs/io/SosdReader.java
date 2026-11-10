package me.index.libs.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class SosdReader {
    private SosdReader() {
    }

    private static long readLittleEndian(DataInputStream in, boolean eightBytes) throws IOException {
        long result = 0;
        for (int i = 0; i < (eightBytes ? 8 : 4); i++) {
            result |= ((long) in.readUnsignedByte()) << (8 * i);
        }
        return result;
    }

    public static long[] read(InputStream input, int maxSize, boolean eightBytes) throws IOException {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
        }
        DataInputStream in = new DataInputStream(new BufferedInputStream(input));
        try {
            long declared = readLittleEndian(in, true);
            if (declared < 0 || declared > Integer.MAX_VALUE) {
                throw new IOException("unsupported keyset element count: " + Long.toUnsignedString(declared));
            }
            int total = (int) declared;
            int wanted = Math.min(total, maxSize);

            long[] keys = new long[wanted];
            int kept = 0;
            for (int read = 0; kept < wanted && read < total; read++) {
                long key = readLittleEndian(in, eightBytes);
                key = eightBytes ? key ^ Long.MIN_VALUE : key + Integer.MIN_VALUE;
                if (kept == 0 || keys[kept - 1] != key) {
                    keys[kept++] = key;
                }
            }
            return (kept == keys.length) ? keys : Arrays.copyOf(keys, kept);
        } catch (EOFException e) {
            throw new IOException("keyset ended earlier than its header promised", e);
        }
    }
}
