package me.index.config.parameters.enums;

import me.index.task.inet.Models;
import me.index.task.isearch.Searches;
import me.index.task.isegment.Windows;

public enum IndexType {
    _binary(Searches.BINARY, null, null),
    _exponential(Searches.EXPONENTIAL, null, null),
    _interpolation(Searches.INTERPOLATION, null, null),
    _pla_greedy(null, Windows.GREEDY, null),
    _pla_spline(null, Windows.SPLINE, null),
    _pla_convex(null, Windows.CONVEX, null),
    _net_sigmoid(null, null, Models.SIGMOID),
    _net_relu(null, null, Models.RELU),
    _net_leaky_relu(null, null, Models.LEAKY_RELU),
    _net_softsign(null, null, Models.SOFTSIGN);

    private final Searches search;
    private final Windows window;
    private final Models model;

    IndexType(Searches search, Windows window, Models model) {
        this.search = search;
        this.window = window;
        this.model = model;
    }

    public boolean isSearch() {
        return search != null;
    }

    public boolean isPla() {
        return window != null;
    }

    public boolean isNet() {
        return model != null;
    }

    public Searches search() {
        if (search == null) {
            throw new IllegalStateException(this + " is not a plain search index");
        }
        return search;
    }

    public Windows window() {
        if (window == null) {
            throw new IllegalStateException(this + " is not a piecewise linear index");
        }
        return window;
    }

    public Models model() {
        if (model == null) {
            throw new IllegalStateException(this + " is not a neural index");
        }
        return model;
    }

    public String label() {
        return name().substring(1);
    }
}
