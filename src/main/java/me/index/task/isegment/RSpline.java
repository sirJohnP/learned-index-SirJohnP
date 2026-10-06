package me.index.task.isegment;

public enum RSpline implements Window {
    INSTANCE;

    long firstKey;
    long lastKey, lastPos;

    Vector lower, upper;

    @Override
    public int init_skip(long key, long err) {
        firstKey = key;
        lastKey = key;
        lastPos = 0;
        lower = new Vector(0, -1);
        upper = new Vector(0, 1);
        return 1;
    }

    @Override
    public boolean can_expand(long key, long pos, int err) {
        Vector vec = new Vector(key - firstKey, pos);

        if (vec.mul(upper) < 0) {
            return false;
        }
        if (vec.mul(lower) > 0) {
            return false;
        }

        Vector vec_up = vec.addY(err);
        Vector vec_down = vec.addY(-err);

        if (vec_up.mul(upper) > 0) {
            upper = vec_up;
        }

        if (vec_down.mul(lower) < 0) {
            lower = vec_down;
        }

        lastKey = key;
        lastPos = pos;
        return true;
    }

    @Override
    public double[] get_result() {
        double k = (double)lastPos / (lastKey - firstKey);
        return new double[]{k, -k * firstKey};
    }

    private class Vector {
        public long x, y;

        public Vector(long x, long y) {
            this.x = x;
            this.y = y;
        }

        public Vector addY(long delta) {
            return new Vector(x, y + delta);
        }

        public long mul(Vector other) {
            return this.x * other.y - this.y * other.x;
        }
    }
}
