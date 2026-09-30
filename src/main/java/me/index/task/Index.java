package me.index.task;

public interface Index {
    void build(int[] keys);

    int predict(int key);

    int lower_bound(int key);

    String id();

    int maxError();

    long sizeInBytes();
}
