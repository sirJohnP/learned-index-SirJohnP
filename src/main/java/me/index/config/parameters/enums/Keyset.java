package me.index.config.parameters.enums;

public enum Keyset {
    _gauss_int32(Source.GAUSSIAN, false),
    _gauss_int64(Source.GAUSSIAN, true),
    _log_norm_int32(Source.LOGNORMAL, false),
    _log_norm_int64(Source.LOGNORMAL, true),
    _wiki_ts_200M_uint64(Source.SOSD, true),
    _books_200M_uint32(Source.SOSD, false),
    _books_800M_uint64(Source.SOSD, true),
    _osm_cellids_800M_uint64(Source.SOSD, true),
    _fb_200M_uint64(Source.SOSD, true);

    public enum Source {GAUSSIAN, LOGNORMAL, SOSD}

    public final Source source;
    public final boolean isLong;

    Keyset(Source source, boolean isLong) {
        this.source = source;
        this.isLong = isLong;
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
