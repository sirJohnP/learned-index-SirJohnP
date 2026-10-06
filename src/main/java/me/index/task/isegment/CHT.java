package me.index.task.isegment;

import java.util.ArrayList;

public enum CHT implements Window {
    INSTANCE;

    private final double INF = 4e9;

    private final ArrayList<Line> upper = new ArrayList<>();
    private final ArrayList<Line> lower = new ArrayList<>();
    private int l, u;
    private double wOpt;

    private Line bestLower;
    private Line bestUpper;
    private double bestWOpt;

    @Override
    public int init_skip(long key, long err) {
        upper.clear();
        lower.clear();
        l = 0;
        u = 0;
        wOpt = INF;
        return 0;
    }

    @Override
    public boolean can_expand(long key, long pos, int err) {
        key *= -1;

        if (lower.size() == 0) {
            lower.add(new Line(key, pos, -INF, INF));
            upper.add(new Line(key, pos, INF, -INF));
            l = 0;
            u = 0;
            wOpt = INF;

            bestWOpt = INF;
            bestLower = lower.getFirst();
            bestUpper = upper.getFirst();
            return true;
        }

        while (lower.size() >= 2 && (double)(pos - lower.getLast().b) / (lower.getLast().k - key) <= lower.getLast().from) {
            if (wOpt != INF && l == lower.size() - 1) {
                l--;
            }
            lower.removeLast();
        }

        double w = (double)(pos - lower.getLast().b) / (lower.getLast().k - key);
        if (wOpt != INF && w < wOpt) {
            wOpt = w;
            while (u < upper.size() - 1 && upper.get(u).to >= wOpt) {
                u++;
            }
        }
        if (lower.size() == 1) {
            if (wOpt == INF) {
                wOpt = w;
                l = 0;
                u = 0;
            }
        }
        lower.getLast().to = w;
        lower.add(new Line(key, pos, w, INF));

        while (upper.size() >= 2 && (double)(pos - upper.getLast().b) / (upper.getLast().k - key) >= upper.getLast().from) {
            if (wOpt != INF && u == upper.size() - 1) {
                u--;
            }
            upper.removeLast();
        }

        w = (double)(pos - upper.getLast().b) / (upper.getLast().k - key);
        if (wOpt != INF && wOpt <= w) {
            u++;
            wOpt = w;
            while (l < lower.size() - 1 && lower.get(l).to < wOpt) {
                l++;
            }
        }
        upper.getLast().to = w;
        upper.add(new Line(key, pos, w, -INF));

        double newErr = wOpt * (double)(upper.get(u).k - lower.get(l).k) + (upper.get(u).b - lower.get(l).b);
        
        if (newErr > 2 * err) {
            return false;
        }

        bestLower = lower.get(l);
        bestUpper = upper.get(u);
        bestWOpt = wOpt;
        return true;
    }

    @Override
    public double[] get_result() {
        if (lower.size() == 0) {
            throw new IllegalStateException("CHT is empty");
        }
        if (lower.size() == 1) {
            return new double[]{0.0, lower.get(0).b};
        }
        return new double[]{bestWOpt, bestWOpt * (double)(bestUpper.k + bestLower.k) / 2 + (double)(bestUpper.b + bestLower.b) / 2};
    }

    private class Line {
        public long k, b;
        public double from, to;

        public Line(long k, long b, double from, double to) {
            this.k = k;
            this.b = b;
            this.from = from;
            this.to = to;
        }
    }
}
