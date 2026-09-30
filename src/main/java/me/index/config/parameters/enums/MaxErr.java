package me.index.config.parameters.enums;

public enum MaxErr {
    _0(0), _1(1), _2(2), _4(4), _8(8), _16(16), _32(32), _64(64);

    public final int value;

    MaxErr(int value) {
        this.value = value;
    }
}
