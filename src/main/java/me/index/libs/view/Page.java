package me.index.libs.view;

import java.util.Locale;

public final class Page {
    final Document doc;
    final double width, height;
    final StringBuilder content;

    Page(Document doc, double w, double h) {
        this.doc = doc;
        this.width = w;
        this.height = h;
        this.content = new StringBuilder();
    }

    public Page moveTo(double x, double y) {
        content.append(fmt(x)).append(' ').append(fmt(y)).append(" m\n");
        return this;
    }

    public Page lineTo(double x, double y) {
        content.append(fmt(x)).append(' ').append(fmt(y)).append(" l\n");
        return this;
    }

    public Page curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        content.append(fmt(x1)).append(' ').append(fmt(y1)).append(' ')
                .append(fmt(x2)).append(' ').append(fmt(y2)).append(' ')
                .append(fmt(x3)).append(' ').append(fmt(y3)).append(" c\n");
        return this;
    }

    public Page closePath() {
        content.append("h\n");
        return this;
    }

    public Page stroke() {
        content.append("S\n");
        return this;
    }

    public Page fill() {
        content.append("f\n");
        return this;
    }

    public Page fillAndStroke() {
        content.append("B\n");
        return this;
    }

    public Page rect(double x, double y, double w, double h) {
        content.append(fmt(x)).append(' ').append(fmt(y)).append(' ')
                .append(fmt(w)).append(' ').append(fmt(h)).append(" re\n");
        return this;
    }

    public Page circle(double cx, double cy, double r) {
        double k = r * 0.5522847498;
        moveTo(cx + r, cy);
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        closePath();
        return this;
    }

    public Page square(double cx, double cy, double r) {
        return rect(cx - r, cy - r, 2 * r, 2 * r);
    }

    public Page lineWidth(double w) {
        content.append(fmt(w)).append(" w\n");
        return this;
    }

    public Page strokeColor(double r, double g, double b) {
        content.append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" RG\n");
        return this;
    }

    public Page fillColor(double r, double g, double b) {
        content.append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" rg\n");
        return this;
    }

    public Page dash(double... pattern) {
        content.append('[');
        for (double d : pattern) content.append(fmt(d)).append(' ');
        content.append("] 0 d\n");
        return this;
    }

    public Page solidLine() {
        content.append("[] 0 d\n");
        return this;
    }

    public Page save() {
        content.append("q\n");
        return this;
    }

    public Page restore() {
        content.append("Q\n");
        return this;
    }

    public Page clipRect(double x, double y, double w, double h) {
        rect(x, y, w, h);
        content.append("W n\n");
        return this;
    }

    public Page text(String s, double x, double y, Font font, double size) {
        doc.registerFont(font);
        content.append("BT\n/").append(doc.getRegFontName(font)).append(' ').append(fmt(size)).append(" Tf\n")
                .append(fmt(x)).append(' ').append(fmt(y)).append(" Td\n(")
                .append(escape(s)).append(") Tj\nET\n");
        return this;
    }

    public Page textRotated(String s, double x, double y, Font font, double size, double angleDeg) {
        doc.registerFont(font);
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        content.append("BT\n/").append(doc.getRegFontName(font)).append(' ').append(fmt(size)).append(" Tf\n")
                .append(fmt(cos)).append(' ').append(fmt(sin)).append(' ').append(fmt(-sin)).append(' ')
                .append(fmt(cos)).append(' ').append(fmt(x)).append(' ').append(fmt(y)).append(" Tm\n(")
                .append(escape(s)).append(") Tj\nET\n");
        return this;
    }

    public static double approxTextWidth(String s, double size) {
        return s.length() * size * 0.52;
    }

    public static String fmt(double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("PDF numbers must be finite, got " + v);
        }
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static final char UNASSIGNED = '\0';

    private static final char[] WIN_ANSI_80_TO_9F = {
            '€', UNASSIGNED, '‚', 'ƒ', '„', '…', '†', '‡',
            'ˆ', '‰', 'Š', '‹', 'Œ', UNASSIGNED, 'Ž', UNASSIGNED,
            UNASSIGNED, '‘', '’', '“', '”', '•', '–', '—',
            '˜', '™', 'š', '›', 'œ', UNASSIGNED, 'ž', 'Ÿ'
    };

    private static final int WIN_ANSI_FALLBACK = '?';

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            int code = toWinAnsi(s.charAt(i));
            if (code == '(' || code == ')' || code == '\\') {
                sb.append('\\').append((char) code);
            } else if (code >= 0x20 && code < 0x7F) {
                sb.append((char) code);
            } else {
                sb.append('\\').append(String.format(Locale.US, "%03o", code));
            }
        }
        return sb.toString();
    }

    private static int toWinAnsi(char c) {
        if (c >= 0x20 && c < 0x7F) return c;
        if (c >= 0xA0 && c <= 0xFF) return c;
        for (int i = 0; i < WIN_ANSI_80_TO_9F.length; i++) {
            if (WIN_ANSI_80_TO_9F[i] != UNASSIGNED && WIN_ANSI_80_TO_9F[i] == c) return 0x80 + i;
        }
        return WIN_ANSI_FALLBACK;
    }
}
