package me.index.libs.math;

import java.util.Arrays;
import java.util.Random;

public final class Utils {
    private Utils() {
    }

    public static long[] genKeysGaussian(int size, boolean isLong, Random rnd) {
        if (size <= 0) return new long[0];
        long[] arr = new long[size];
        long maxVal = isLong ? Long.MAX_VALUE : Integer.MAX_VALUE;
        long scale = maxVal / 6L;
        for (int i = 0; i < size; i++) {
            double val = rnd.nextGaussian();
            long t = Math.round(scale * ((val > 6.0) ? 6.0 : Math.max(val, -6.0)));
            arr[i] = Math.max(Math.min(t, maxVal), -maxVal);
        }
        Arrays.sort(arr);
        int j = 0;
        for (int i = 1; i < size; i++)
            if (arr[i] != arr[j])
                arr[++j] = arr[i];
        return Arrays.copyOf(arr, j + 1);
    }

    public static long[] genKeysLognormal(int size, boolean isLong, Random rnd) {
        if (size <= 0) return new long[0];
        long[] arr = new long[size];
        long maxVal = isLong ? Long.MAX_VALUE : Integer.MAX_VALUE;
        double sigma = Math.log(maxVal) / 6.0;
        for (int i = 0; i < size; i++) {
            double val = rnd.nextGaussian();
            double expVal = Math.exp(sigma * ((val > 6.0) ? 6.0 : (Math.max(val, -6.0))));
            long t = (expVal > (double) maxVal) ? maxVal : Math.round(expVal);
            arr[i] = Math.max(1L, t);
        }
        Arrays.sort(arr);
        int j = 0;
        for (int i = 1; i < size; i++) {
            if (arr[i] != arr[j]) {
                arr[++j] = arr[i];
            }
        }
        return Arrays.copyOf(arr, j + 1);
    }
}
