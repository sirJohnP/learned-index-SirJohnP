package me.index.task.isegment;

public enum Windows {
    GREEDY(GreedyWindow.INSTANCE),
    SPLINE(RSpline.INSTANCE),
    CONVEX(CHT.INSTANCE);

    private final Window window;

    Windows(Window window) {
        this.window = window;
    }

    public Window window() {
        return window;
    }
}
