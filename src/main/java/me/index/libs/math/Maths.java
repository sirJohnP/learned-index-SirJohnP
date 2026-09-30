package me.index.libs.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

public final class Maths {
    private Maths() {
    }

    public static final Int128 ZERO = new Int128(0, 0);
    public static final Int128 ONE = new Int128(0, 1);

    public static final Int128 TWO64 = new Int128(1, 0);
    public static final Int128 NEG_TWO64 = new Int128(-1, 0);
    public static final Int128 U64_MAX = new Int128(0, -1);

    public static final Frac128 F_ZERO = new Frac128(ZERO, ONE);
    public static final Frac128 INF_NEG = new Frac128(NEG_TWO64, ONE);
    public static final Frac128 INF_POS = new Frac128(TWO64, ONE);

    public static boolean lessZero(Int128 o) {
        return o.hi() < 0;
    }

    public static boolean eqZero(Int128 o) {
        return o.hi() == 0 && o.lo() == 0;
    }

    public static boolean greatZero(Int128 o) {
        return o.hi() > 0 || o.hi() == 0 && o.lo() != 0;
    }

    public static boolean greatOrEqZero(Int128 o) {
        return o.hi() >= 0;
    }

    public static boolean lessOrEqZero(Int128 o) {
        return o.hi() < 0 || o.hi() == 0 && o.lo() == 0;
    }

    public static boolean less(Frac128 x, Frac128 y) {
        return lessZero(diff(x, y));
    }

    public static boolean lessEq(Frac128 x, Frac128 y) {
        return lessOrEqZero(diff(x, y));
    }

    public static boolean great(Frac128 x, Frac128 y) {
        return greatZero(diff(x, y));
    }

    public static boolean greatEq(Frac128 x, Frac128 y) {
        return greatOrEqZero(diff(x, y));
    }

    public static boolean eq(Frac128 x, Frac128 y) {
        return eqZero(diff(x, y));
    }

    public static Int128 diff(Frac128 x, Frac128 y) {
        return sub(mul(x.num(), y.den()), mul(y.num(), x.den()));
    }

    public static Int128 neg(Int128 o) {
        long lo = ~o.lo() + 1;
        long hi = ~o.hi() + (lo == 0 ? 1 : 0);
        return new Int128(hi, lo);
    }

    public static Int128 sum(Int128 x, Int128 y) {
        long lo = x.lo() + y.lo();
        long hi = x.hi() + y.hi() + (Long.compareUnsigned(lo, x.lo()) < 0 ? 1L : 0L);
        return new Int128(hi, lo);
    }

    public static Int128 sub(Int128 x, Int128 y) {
        return new Int128(
                x.hi() - y.hi() - ((Long.compareUnsigned(x.lo(), y.lo()) < 0) ? 1L : 0L),
                x.lo() - y.lo());
    }

    public static Int128 sub(long x, long y) {
        return new Int128(
                ((x < 0) ? -1L : 0L) - ((y < 0) ? -1L : 0L) - ((Long.compareUnsigned(x, y) < 0) ? 1L : 0L),
                x - y);
    }

    public static Int128 mul(Int128 x, long y) {
        boolean sign = lessZero(x) ^ (y < 0);
        if (lessZero(x))
            x = neg(x);
        if (y < 0) {
            y = -y;
        }
        Int128 r = new Int128(Math.unsignedMultiplyHigh(x.lo(), y) + x.hi() * y, x.lo() * y);
        return (sign) ? neg(r) : r;
    }

    public static Int128 mul(Int128 x, Int128 y) {
        boolean sign = lessZero(x) ^ lessZero(y);
        if (lessZero(x))
            x = neg(x);
        if (lessZero(y))
            y = neg(y);
        Int128 r = new Int128(
                Math.unsignedMultiplyHigh(x.lo(), y.lo()) + x.hi() * y.lo() + x.lo() * y.hi(),
                x.lo() * y.lo());
        return (sign) ? neg(r) : r;
    }

    public static byte[] toByteArray(Int128 first) {
        return new byte[]{
                (byte) (first.hi() >>> 56),
                (byte) (first.hi() >>> 48),
                (byte) (first.hi() >>> 40),
                (byte) (first.hi() >>> 32),
                (byte) (first.hi() >>> 24),
                (byte) (first.hi() >>> 16),
                (byte) (first.hi() >>> 8),
                (byte) first.hi(),
                (byte) (first.lo() >>> 56),
                (byte) (first.lo() >>> 48),
                (byte) (first.lo() >>> 40),
                (byte) (first.lo() >>> 32),
                (byte) (first.lo() >>> 24),
                (byte) (first.lo() >>> 16),
                (byte) (first.lo() >>> 8),
                (byte) first.lo()
        };
    }

    public static BigDecimal toBigDecimal(Int128 first) {
        return new BigDecimal(new BigInteger(toByteArray(first)));
    }

    public static double toDouble(Frac128 first) {
        return (toBigDecimal(first.num()).divide(toBigDecimal(first.den()), MathContext.DECIMAL128)).doubleValue();
    }

    public static int predict(double slope, double intercept, long key) {
        return Math.max((int) (slope * key + intercept), 0);
    }

    public static double[] mul(double[][] m, double[] v) {
        double[] out = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            double[] row = m[i];
            if (row.length != v.length) {
                throw new IllegalArgumentException("cannot multiply a " + m.length + "x" + row.length
                        + " matrix by a vector of length " + v.length);
            }
            double acc = 0.0;
            for (int j = 0; j < row.length; j++) {
                acc += row[j] * v[j];
            }
            out[i] = acc;
        }
        return out;
    }

    public static double[] mulTransposed(double[][] m, double[] v) {
        if (m.length != v.length) {
            throw new IllegalArgumentException("cannot multiply the transpose of a " + m.length
                    + "-row matrix by a vector of length " + v.length);
        }
        double[] out = new double[m.length == 0 ? 0 : m[0].length];
        for (int i = 0; i < m.length; i++) {
            double[] row = m[i];
            double scale = v[i];
            for (int j = 0; j < row.length; j++) {
                out[j] += row[j] * scale;
            }
        }
        return out;
    }

    public static double[] add(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("cannot add vectors of length " + a.length
                    + " and " + b.length);
        }
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] + b[i];
        }
        return out;
    }

    public static double[][] outer(double[] a, double[] b) {
        double[][] out = new double[a.length][b.length];
        for (int i = 0; i < a.length; i++) {
            double scale = a[i];
            for (int j = 0; j < b.length; j++) {
                out[i][j] = scale * b[j];
            }
        }
        return out;
    }

    public static double[] hadamard(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("cannot multiply element-wise vectors of length " + a.length
                    + " and " + b.length);
        }
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] * b[i];
        }
        return out;
    }

    public static double[] map(double[] v, DoubleUnaryOperator f) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = f.applyAsDouble(v[i]);
        }
        return out;
    }

    public static void subtractScaled(double[][] m, double[][] d, double factor) {
        if (m.length != d.length) {
            throw new IllegalArgumentException("cannot subtract a " + d.length + "-row matrix from a "
                    + m.length + "-row matrix");
        }
        for (int i = 0; i < m.length; i++) {
            subtractScaled(m[i], d[i], factor);
        }
    }

    public static void subtractScaled(double[] v, double[] d, double factor) {
        if (v.length != d.length) {
            throw new IllegalArgumentException("cannot subtract a vector of length " + d.length
                    + " from a vector of length " + v.length);
        }
        for (int i = 0; i < v.length; i++) {
            v[i] -= factor * d[i];
        }
    }

    public static double[][] gaussian(int rows, int cols, double scale, Random rnd) {
        double[][] out = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                out[i][j] = rnd.nextGaussian() * scale;
            }
        }
        return out;
    }
}
