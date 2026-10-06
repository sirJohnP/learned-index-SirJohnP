package me.index.task.isegment;

import java.util.ArrayList;

public enum CHT implements Window {
    INSTANCE;

    private final double INF = 4e9;

    private final ArrayList<Line> upper = new ArrayList<>();
    private final ArrayList<Line> lower = new ArrayList<>();
    int l, u;
    double wOpt;

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
            return true;
        }

        int i = lower.size() - 1;
        int nextU = u;
        int nextL = l;

        double leftW = INF;
        double rightW = INF;

        double nextWOpt = wOpt;
        while (i >= 1 && (double)(pos - lower.get(i).b) / (lower.get(i).k - key) <= lower.get(i).from) {
            if (nextWOpt != INF && nextL == i) {
                nextL--;
            }
            i--;
        }

        if (i >= 0) {
            double w = (double)(pos - lower.get(i).b) / (lower.get(i).k - key);
            if (nextWOpt != INF && w < nextWOpt) {
                nextWOpt = w;
                while (nextU < upper.size() - 1 && upper.get(nextU).to >= nextWOpt) {
                    nextU++;
                }
            }
            if (i == 0) {
                if (nextWOpt == INF) {
                    nextWOpt = w;
                    nextL = 0;
                    nextU = 0;
                }
            }
            leftW = w;
        } 

        int j = upper.size() - 1;

        while (j >= 1 && (double)(pos - upper.get(j).b) / (upper.get(j).k - key) >= upper.get(j).from) {
            if (nextWOpt != INF && nextU == j) {
                nextU--;
            }
            j--;
        }

        if (j >= 0) {
            double w = (double)(pos - upper.get(j).b) / (upper.get(j).k - key);
            if (nextWOpt != INF && nextWOpt <= w) {
                nextU++;
                nextWOpt = w;
                while (nextL <= i && Math.min(lower.get(nextL).to, leftW) < nextWOpt) {
                    nextL++;
                }
            }
            rightW = w;
        } 

        double newErr = nextWOpt * (double)((nextU == j + 1 ? key : upper.get(nextU).k) - (nextL == i + 1 ? key : lower.get(nextL).k)) + ((nextU == j + 1 ? pos : upper.get(nextU).b) - (nextL == i + 1 ? pos : lower.get(nextL).b));
        
        if (newErr > 2 * err) {
            return false;
        }
        while (lower.size() > i + 1) {
            lower.removeLast();
        }
        if (lower.size() == 0) {
            lower.add(new Line(key, pos, -INF, INF));
        } else {
            lower.getLast().to = leftW;
            lower.add(new Line(key, pos, leftW, INF));
        }

        while (upper.size() > j + 1) {
            upper.removeLast();
        }
        if (upper.size() == 0) {
            upper.add(new Line(key, pos, INF, -INF));
        } else {
            upper.getLast().to = rightW;
            upper.add(new Line(key, pos, rightW, -INF));
        }

        l = nextL;
        u = nextU;
        wOpt = nextWOpt;
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
        return new double[]{wOpt, wOpt * (double)(upper.get(u).k + lower.get(l).k) / 2 + (double)(upper.get(u).b + lower.get(l).b) / 2};
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
