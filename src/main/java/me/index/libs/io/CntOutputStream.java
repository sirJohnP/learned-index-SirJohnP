package me.index.libs.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class CntOutputStream extends FilterOutputStream {
    private long count = 0;

    public CntOutputStream(OutputStream out) {
        super(out);
    }

    public long count() {
        return count;
    }

    public void write(String s) throws IOException {
        write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        count += len;
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }
}
