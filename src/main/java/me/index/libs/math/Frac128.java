package me.index.libs.math;

public record Frac128(Int128 num, Int128 den) {
    public Frac128 {
        if (Maths.eqZero(den)) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        if (Maths.lessZero(den)) {
            if (isMin(num) || isMin(den)) {
                throw new IllegalArgumentException("cannot normalize the sign of " + num + " / " + den
                        + ": negating -2^127 overflows");
            }
            num = Maths.neg(num);
            den = Maths.neg(den);
        }
    }

    private static boolean isMin(Int128 v) {
        return v.hi() == Long.MIN_VALUE && v.lo() == 0;
    }
}
