package me.index.libs.view;

public enum Font {
    HELVETICA("Helvetica"),
    HELVETICA_BOLD("Helvetica-Bold"),
    HELVETICA_OBLIQUE("Helvetica-Oblique"),
    TIMES_ROMAN("Times-Roman"),
    TIMES_BOLD("Times-Bold"),
    COURIER("Courier");

    final String baseName;

    Font(String n) {
        baseName = n;
    }
}
