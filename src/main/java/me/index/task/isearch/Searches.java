package me.index.task.isearch;

import me.index.task.Index;

public enum Searches {
    BINARY {
        @Override
        public Index create() {
            return new BinarySearchIndex();
        }
    },
    EXPONENTIAL {
        @Override
        public Index create() {
            return new ExponentialSearchIndex();
        }
    },
    INTERPOLATION {
        @Override
        public Index create() {
            return new InterpolationSearchIndex();
        }
    };

    public abstract Index create();
}
