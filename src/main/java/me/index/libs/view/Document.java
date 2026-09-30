package me.index.libs.view;

import me.index.libs.io.CntOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static me.index.libs.view.Page.fmt;

public final class Document {
    private final List<Page> pages;
    private final LinkedHashSet<Font> usedFonts;

    public Document() {
        pages = new ArrayList<>();
        usedFonts = new LinkedHashSet<>();
    }

    public Page addA4VerticalPage() {
        Page p = new Page(this, 595, 842);
        pages.add(p);
        return p;
    }

    public Page addA4HorizontalPage() {
        Page p = new Page(this, 842, 595);
        pages.add(p);
        return p;
    }

    public Page addPage(double widthPt, double heightPt) {
        Page p = new Page(this, widthPt, heightPt);
        pages.add(p);
        return p;
    }

    public void registerFont(Font f) {
        usedFonts.add(f);
    }

    public String getRegFontName(Font f) {
        int i = 0;
        for (Font x : usedFonts) {
            if (x == f) return "F" + i;
            i++;
        }
        throw new IllegalStateException("font not registered: " + f);
    }

    public void save(String path) throws IOException {
        if (pages.isEmpty()) {
            throw new IllegalStateException("cannot save a document without pages");
        }
        try (CntOutputStream out = new CntOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
            out.write("%PDF-1.4\n");

            int n = pages.size();

            int CATALOG = 1;
            int PAGES_TREE = 2;

            int obj = 3;

            int[] PAGE_TYPE = new int[n];
            int[] PAGE_CONTENT = new int[n];
            for (int i = 0; i < n; i++) {
                PAGE_TYPE[i] = obj++;
                PAGE_CONTENT[i] = obj++;
            }

            List<Font> fontList = new ArrayList<>(usedFonts);
            int[] FONTS = new int[fontList.size()];
            for (int i = 0; i < fontList.size(); i++)
                FONTS[i] = obj++;

            int totalObjects = obj - 1;

            long[] offsets = new long[totalObjects + 1];

            offsets[CATALOG] = out.count();
            out.write(CATALOG + " 0 obj\n<< /Type /Catalog /Pages " + PAGES_TREE + " 0 R >>\nendobj\n");

            offsets[PAGES_TREE] = out.count();
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < n; i++)
                kids.append(PAGE_TYPE[i]).append(" 0 R ");
            out.write(PAGES_TREE + " 0 obj\n<< /Type /Pages /Count " + n + " /Kids [ " + kids + "] >>\nendobj\n");

            StringBuilder fontResDict = new StringBuilder();
            for (int i = 0; i < fontList.size(); i++) {
                fontResDict.append("/F").append(i).append(' ').append(FONTS[i]).append(" 0 R ");
            }

            for (int i = 0; i < n; i++) {
                Page p = pages.get(i);
                byte[] contentBytes = p.content.toString().getBytes(StandardCharsets.ISO_8859_1);

                offsets[PAGE_TYPE[i]] = out.count();
                out.write(PAGE_TYPE[i] + " 0 obj\n<< /Type /Page /Parent " + PAGES_TREE + " 0 R "
                        + "/MediaBox [0 0 " + fmt(p.width) + " " + fmt(p.height) + "] "
                        + "/Resources << /Font << " + fontResDict + ">> >> "
                        + "/Contents " + PAGE_CONTENT[i] + " 0 R >>\nendobj\n");

                offsets[PAGE_CONTENT[i]] = out.count();
                out.write(PAGE_CONTENT[i] + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
                out.write(contentBytes);
                out.write("\nendstream\nendobj\n");
            }

            for (int i = 0; i < fontList.size(); i++) {
                offsets[FONTS[i]] = out.count();
                out.write(FONTS[i] + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /"
                        + fontList.get(i).baseName + " /Encoding /WinAnsiEncoding >>\nendobj\n");
            }

            long xrefStart = out.count();
            out.write("xref\n0 " + (totalObjects + 1) + "\n");
            out.write("0000000000 65535 f \n");
            for (int i = 1; i <= totalObjects; i++) {
                out.write(String.format(Locale.US, "%010d %05d n \n", offsets[i], 0));
            }

            out.write("trailer\n<< /Size " + (totalObjects + 1) + " /Root " + CATALOG + " 0 R >>\nstartxref\n"
                    + xrefStart + "\n%%EOF");
            out.flush();
        }
    }
}
