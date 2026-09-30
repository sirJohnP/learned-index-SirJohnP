package me.index.config.parameters.enums;

public enum Keyset {
    _gauss_int32(Source.GAUSSIAN, false, false, false),
    _gauss_int64(Source.GAUSSIAN, true, false, false),
    _log_norm_int32(Source.LOGNORMAL, false, false, false),
    _log_norm_int64(Source.LOGNORMAL, true, false, false),
    _wiki_ts_200M_uint64(Source.SOSD, true, false, false),
    _books_200M_uint32(Source.SOSD, false, false, false),
    _books_800M_uint64(Source.SOSD, true, true, true),
    _osm_cellids_800M_uint64(Source.SOSD, true, true, false),
    _fb_200M_uint64(Source.SOSD, true, true, false);

    public enum Source {GAUSSIAN, LOGNORMAL, SOSD}

    public final Source source;
    public final boolean isLong;
    public final boolean needShift;
    public final boolean needPlusOne;

    Keyset(Source source, boolean isLong, boolean needShift, boolean needPlusOne) {
        this.source = source;
        this.isLong = isLong;
        this.needShift = needShift;
        this.needPlusOne = needPlusOne;
    }

    public boolean isSOSD() {
        return source == Source.SOSD;
    }

    public String fileName() {
        if (!isSOSD()) {
            throw new IllegalStateException("keyset " + this + " is synthetic and has no data file");
        }
        return name().substring(1);
    }
}
