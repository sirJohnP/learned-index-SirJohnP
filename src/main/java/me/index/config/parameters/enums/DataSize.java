package me.index.config.parameters.enums;

public enum DataSize {
    _1e4(1e4), _1e5(1e5), _1e6(1e6), _1e7(1e7), _max(2e8);

    public final int size;

    DataSize(double size) {
        this.size = (int) size;
    }
}
