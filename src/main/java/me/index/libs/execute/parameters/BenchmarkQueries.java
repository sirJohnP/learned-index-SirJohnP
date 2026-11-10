package me.index.libs.execute.parameters;

import me.index.libs.math.Sorted;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record BenchmarkQueries(int[] keys, int[] expected) {
    public static BenchmarkQueries create(int[] keys, int count, long seed) {
        return createBatches(keys, count, 1, seed).getFirst();
    }

    public static List<BenchmarkQueries> createBatches(int[] keys, int count, int batches, long seed) {
        Sorted.checkKeys(keys);
        if (count <= 0) throw new IllegalArgumentException("query count must be positive");
        if (batches <= 0) throw new IllegalArgumentException("batch count must be positive");
        Random random = new Random(seed);
        List<BenchmarkQueries> result = new ArrayList<>(batches);
        for (int i = 0; i < batches; i++) {
            result.add(create(keys, count, random));
        }
        return List.copyOf(result);
    }

    private static BenchmarkQueries create(int[] keys, int count, Random random) {
        int[] asked = new int[count];
        int[] expected = new int[count];
        for (int i = 0; i < count; i++) {
            int position = random.nextInt(keys.length);
            asked[i] = keys[position];
            expected[i] = position;
        }
        return new BenchmarkQueries(asked, expected);
    }
}
