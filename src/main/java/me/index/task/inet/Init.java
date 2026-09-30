package me.index.task.inet;

public enum Init {
    HE {
        @Override
        public double scale(int fanIn, int fanOut) {
            return Math.sqrt(2.0 / fanIn);
        }
    },
    XAVIER {
        @Override
        public double scale(int fanIn, int fanOut) {
            return Math.sqrt(2.0 / (fanIn + fanOut));
        }
    };

    public abstract double scale(int fanIn, int fanOut);
}
