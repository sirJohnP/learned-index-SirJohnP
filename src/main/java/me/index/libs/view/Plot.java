package me.index.libs.view;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Plot {
    public record Color(double r, double g, double b) {
    }

    public static final Color BLUE = new Color(0.122, 0.467, 0.706);
    public static final Color ORANGE = new Color(1.000, 0.498, 0.055);
    public static final Color GREEN = new Color(0.173, 0.627, 0.173);
    public static final Color RED = new Color(0.839, 0.153, 0.157);
    public static final Color PURPLE = new Color(0.580, 0.404, 0.741);
    public static final Color BROWN = new Color(0.549, 0.337, 0.294);
    public static final Color GRAY = new Color(0.498, 0.498, 0.498);

    private static final Color[] AUTO_PALETTE = {BLUE, ORANGE, GREEN, RED, PURPLE, BROWN, GRAY};

    private static final Color COLOR_PLOT_BORDER = new Color(0.60, 0.60, 0.60);
    private static final Color COLOR_GRID_LINE = new Color(0.88, 0.88, 0.88);
    private static final Color COLOR_AXIS_LINE = new Color(0.15, 0.15, 0.15);
    private static final Color COLOR_TITLE_TEXT = new Color(0.10, 0.10, 0.10);
    private static final Color COLOR_AXIS_LABEL = new Color(0.20, 0.20, 0.20);
    private static final Color COLOR_TICK_LABEL = new Color(0.30, 0.30, 0.30);
    private static final Color COLOR_LEGEND_TEXT = new Color(0.15, 0.15, 0.15);

    private static final double REFERENCE_WIDTH = 900;
    private static final double REFERENCE_HEIGHT = 600;

    private static final double TITLE_FONT_SIZE = 15;
    private static final double AXIS_LABEL_FONT_SIZE = 11;
    private static final double TICK_LABEL_FONT_SIZE = 9;
    private static final double LEGEND_FONT_SIZE = 9.5;

    private static final double FONT_ASCENT_FRACTION = 0.75;
    private static final double TEXT_VERTICAL_CENTER_FRACTION = 0.32;

    private static final double EDGE_PADDING = 10;
    private static final double TITLE_TO_PLOT_GAP = 10;
    private static final double AXIS_LABEL_TO_TICKS_GAP = 8;
    private static final double TICK_LABEL_TO_AXIS_GAP = 6;

    private static final double DEFAULT_LINE_WIDTH = 1.7;
    private static final double DEFAULT_MARKER_RADIUS = 2.6;
    private static final double AXIS_LINE_WIDTH = 1.2;
    private static final double GRID_LINE_WIDTH = 0.6;
    private static final double PLOT_BORDER_WIDTH = 0.8;

    private static final double LEGEND_SWATCH_SIZE = 11;
    private static final double LEGEND_ROW_HEIGHT = 18;
    private static final double LEGEND_PLOT_TO_SWATCH_GAP = 16;
    private static final double LEGEND_SWATCH_TO_TEXT_GAP = 5;
    private static final double LEGEND_TEXT_RIGHT_PADDING = 10;
    private static final double LEGEND_TEXT_VERTICAL_OFFSET_FRACTION = 0.18;

    private static final int DEFAULT_X_TICKS = 6;
    private static final int DEFAULT_Y_TICKS = 5;

    private static final double TICK_DECIMALS_ZERO_THRESHOLD = 20;
    private static final double TICK_DECIMALS_ONE_THRESHOLD = 2;

    private static final double AUTO_RANGE_PADDING_FRACTION = 0.08;

    private enum Kind {LINE, SCATTER}

    private record Layer(Kind kind, double[] x, double[] y, Color color, String label, double size) {
    }

    private final Document doc;
    private final Page page;
    private final double width, height;
    private final double scale;

    private String title = "";
    private String xLabel = "";
    private String yLabel = "";
    private boolean showGrid = true;
    private Boolean legendOverride = null;
    private boolean squareMarkers = false;
    private int xTicks = DEFAULT_X_TICKS;
    private int yTicks = DEFAULT_Y_TICKS;
    private double minX = Double.NaN, maxX = Double.NaN;
    private double minY = Double.NaN, maxY = Double.NaN;

    private final List<Layer> layers = new ArrayList<>();
    private int autoColorIndex = 0;
    private boolean rendered = false;

    public Plot(double width, double height) {
        this.width = width;
        this.height = height;
        this.doc = new Document();
        this.page = doc.addPage(width, height);
        this.scale = Math.sqrt((width / REFERENCE_WIDTH) * (height / REFERENCE_HEIGHT));
    }

    public Plot title(String text) {
        this.title = text;
        return this;
    }

    public Plot xlabel(String text) {
        this.xLabel = text;
        return this;
    }

    public Plot ylabel(String text) {
        this.yLabel = text;
        return this;
    }

    public Plot grid(boolean show) {
        this.showGrid = show;
        return this;
    }

    public Plot legend(boolean show) {
        this.legendOverride = show;
        return this;
    }

    public Plot xTicks(int count) {
        this.xTicks = requirePositive(count, "xTicks");
        return this;
    }

    public Plot yTicks(int count) {
        this.yTicks = requirePositive(count, "yTicks");
        return this;
    }

    private static int requirePositive(int count, String name) {
        if (count < 1) {
            throw new IllegalArgumentException(name + " must be at least 1, got " + count);
        }
        return count;
    }

    public Plot xlim(double min, double max) {
        this.minX = min;
        this.maxX = max;
        return this;
    }

    public Plot ylim(double min, double max) {
        this.minY = min;
        this.maxY = max;
        return this;
    }

    public Plot squareMarkers(boolean use) {
        this.squareMarkers = use;
        return this;
    }

    public Plot line(double[] x, double[] y, Color color, String label, double lineWidth) {
        layers.add(new Layer(Kind.LINE, x, y, color, label, lineWidth));
        return this;
    }

    public Plot line(double[] x, double[] y, Color color, String label) {
        return line(x, y, color, label, DEFAULT_LINE_WIDTH);
    }

    public Plot line(double[] x, double[] y, String label) {
        return line(x, y, nextAutoColor(), label);
    }

    public Plot line(long[] x, long[] y, Color color, String label, double lineWidth) {
        return line(toDouble(x), toDouble(y), color, label, lineWidth);
    }

    public Plot line(long[] x, long[] y, Color color, String label) {
        return line(toDouble(x), toDouble(y), color, label);
    }

    public Plot line(long[] x, long[] y, String label) {
        return line(toDouble(x), toDouble(y), label);
    }

    public Plot scatter(double[] x, double[] y, Color color, String label, double pointRadius) {
        layers.add(new Layer(Kind.SCATTER, x, y, color, label, pointRadius));
        return this;
    }

    public Plot scatter(double[] x, double[] y, Color color, String label) {
        return scatter(x, y, color, label, DEFAULT_MARKER_RADIUS);
    }

    public Plot scatter(double[] x, double[] y, String label) {
        return scatter(x, y, nextAutoColor(), label);
    }

    public Plot scatter(long[] x, long[] y, Color color, String label, double pointRadius) {
        return scatter(toDouble(x), toDouble(y), color, label, pointRadius);
    }

    public Plot scatter(long[] x, long[] y, Color color, String label) {
        return scatter(toDouble(x), toDouble(y), color, label);
    }

    public Plot scatter(long[] x, long[] y, String label) {
        return scatter(toDouble(x), toDouble(y), label);
    }

    private Color nextAutoColor() {
        Color c = AUTO_PALETTE[autoColorIndex % AUTO_PALETTE.length];
        autoColorIndex++;
        return c;
    }

    private static double[] toDouble(long[] a) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i];
        return r;
    }

    public void save(String path) throws IOException {
        if (!rendered) {
            render();
            rendered = true;
        }
        doc.save(path);
    }

    private boolean hasLegend() {
        if (legendOverride != null) return legendOverride;
        for (Layer l : layers) if (l.label != null && !l.label.isEmpty()) return true;
        return false;
    }

    private void render() {
        double[] xr = resolveRange(true, minX, maxX);
        double[] yr = resolveRange(false, minY, maxY);

        double tickFontSize = TICK_LABEL_FONT_SIZE * scale;
        double yTickLabelMaxWidth = maxYTickLabelWidth(yr, tickFontSize);

        double marginTop = EDGE_PADDING * scale
                + (title.isEmpty() ? 0 : TITLE_FONT_SIZE * scale + TITLE_TO_PLOT_GAP * scale);

        double marginBottom = EDGE_PADDING * scale
                + (xLabel.isEmpty() ? 0 : AXIS_LABEL_FONT_SIZE * scale + AXIS_LABEL_TO_TICKS_GAP * scale)
                + tickFontSize
                + TICK_LABEL_TO_AXIS_GAP * scale;

        double marginLeft = EDGE_PADDING * scale
                + (yLabel.isEmpty() ? 0 : AXIS_LABEL_FONT_SIZE * scale + AXIS_LABEL_TO_TICKS_GAP * scale)
                + yTickLabelMaxWidth
                + TICK_LABEL_TO_AXIS_GAP * scale;

        double marginRight = hasLegend() ? legendWidth() : EDGE_PADDING * scale;

        double px = marginLeft;
        double py = marginBottom;
        double pw = width - marginLeft - marginRight;
        double ph = height - marginTop - marginBottom;

        drawPlotBorder(px, py, pw, ph);
        drawYAxisTicksAndGrid(px, py, pw, ph, yr, tickFontSize);
        drawXAxisTicksAndGrid(px, py, pw, ph, xr, tickFontSize);
        drawAxes(px, py, pw, ph);
        for (Layer l : layers) {
            if (l.kind == Kind.LINE) drawLine(l, px, py, pw, ph, xr, yr);
            else drawScatter(l, px, py, pw, ph, xr, yr);
        }
        drawTitle();
        drawXLabel(px, pw);
        drawYLabel(py, ph);
        if (hasLegend()) drawLegend(px, py, pw, ph);
    }

    private double maxYTickLabelWidth(double[] yr, double tickFontSize) {
        double maxWidth = 0;
        for (int i = 0; i <= yTicks; i++) {
            double value = yr[0] + (yr[1] - yr[0]) * i / yTicks;
            maxWidth = Math.max(maxWidth, Page.approxTextWidth(tickLabel(value, yr[1] - yr[0]), tickFontSize));
        }
        return maxWidth;
    }

    private double legendWidth() {
        double maxLabelWidth = 0;
        for (Layer l : layers) {
            if (l.label == null || l.label.isEmpty()) continue;
            maxLabelWidth = Math.max(maxLabelWidth, Page.approxTextWidth(l.label, LEGEND_FONT_SIZE * scale));
        }
        return LEGEND_PLOT_TO_SWATCH_GAP * scale
                + LEGEND_SWATCH_SIZE * scale
                + LEGEND_SWATCH_TO_TEXT_GAP * scale
                + maxLabelWidth
                + LEGEND_TEXT_RIGHT_PADDING * scale;
    }

    private void drawPlotBorder(double px, double py, double pw, double ph) {
        page.strokeColor(COLOR_PLOT_BORDER.r, COLOR_PLOT_BORDER.g, COLOR_PLOT_BORDER.b)
                .lineWidth(PLOT_BORDER_WIDTH * scale)
                .rect(px, py, pw, ph).stroke();
    }

    private void drawYAxisTicksAndGrid(double px, double py, double pw, double ph, double[] yr, double fontSize) {
        for (int i = 0; i <= yTicks; i++) {
            double value = yr[0] + (yr[1] - yr[0]) * i / yTicks;
            double y = py + ph * i / yTicks;
            if (showGrid && i > 0 && i < yTicks) {
                page.save().strokeColor(COLOR_GRID_LINE.r, COLOR_GRID_LINE.g, COLOR_GRID_LINE.b)
                        .lineWidth(GRID_LINE_WIDTH * scale)
                        .moveTo(px, y).lineTo(px + pw, y).stroke().restore();
            }
            String label = tickLabel(value, yr[1] - yr[0]);
            double labelWidth = Page.approxTextWidth(label, fontSize);
            page.fillColor(COLOR_TICK_LABEL.r, COLOR_TICK_LABEL.g, COLOR_TICK_LABEL.b)
                    .text(label,
                            px - TICK_LABEL_TO_AXIS_GAP * scale - labelWidth,
                            y - fontSize * TEXT_VERTICAL_CENTER_FRACTION,
                            Font.HELVETICA, fontSize);
        }
    }

    private void drawXAxisTicksAndGrid(double px, double py, double pw, double ph, double[] xr, double fontSize) {
        for (int i = 0; i <= xTicks; i++) {
            double value = xr[0] + (xr[1] - xr[0]) * i / xTicks;
            double x = px + pw * i / xTicks;
            if (showGrid && i > 0 && i < xTicks) {
                page.save().strokeColor(COLOR_GRID_LINE.r, COLOR_GRID_LINE.g, COLOR_GRID_LINE.b)
                        .lineWidth(GRID_LINE_WIDTH * scale)
                        .moveTo(x, py).lineTo(x, py + ph).stroke().restore();
            }
            String label = tickLabel(value, xr[1] - xr[0]);
            double labelWidth = Page.approxTextWidth(label, fontSize);
            page.fillColor(COLOR_TICK_LABEL.r, COLOR_TICK_LABEL.g, COLOR_TICK_LABEL.b)
                    .text(label,
                            x - labelWidth / 2,
                            py - TICK_LABEL_TO_AXIS_GAP * scale - fontSize * FONT_ASCENT_FRACTION,
                            Font.HELVETICA, fontSize);
        }
    }

    private void drawAxes(double px, double py, double pw, double ph) {
        page.strokeColor(COLOR_AXIS_LINE.r, COLOR_AXIS_LINE.g, COLOR_AXIS_LINE.b)
                .lineWidth(AXIS_LINE_WIDTH * scale)
                .moveTo(px, py).lineTo(px + pw, py).stroke()
                .moveTo(px, py).lineTo(px, py + ph).stroke();
    }

    private void drawLine(Layer l, double px, double py, double pw, double ph, double[] xr, double[] yr) {
        page.save().clipRect(px, py, pw, ph)
                .strokeColor(l.color.r, l.color.g, l.color.b).lineWidth(l.size * scale);
        boolean started = false;
        for (int i = 0; i < l.x.length && i < l.y.length; i++) {
            if (!Double.isFinite(l.x[i]) || !Double.isFinite(l.y[i])) {
                started = false;
                continue;
            }
            double x = px + (l.x[i] - xr[0]) / (xr[1] - xr[0]) * pw;
            double y = py + (l.y[i] - yr[0]) / (yr[1] - yr[0]) * ph;
            if (!started) {
                page.moveTo(x, y);
                started = true;
            } else {
                page.lineTo(x, y);
            }
        }
        page.stroke().restore();
    }

    private void drawScatter(Layer l, double px, double py, double pw, double ph, double[] xr, double[] yr) {
        double radius = l.size * scale;
        page.save().clipRect(px, py, pw, ph).fillColor(l.color.r, l.color.g, l.color.b);
        for (int i = 0; i < l.x.length && i < l.y.length; i++) {
            if (!Double.isFinite(l.x[i]) || !Double.isFinite(l.y[i])) continue;
            double x = px + (l.x[i] - xr[0]) / (xr[1] - xr[0]) * pw;
            double y = py + (l.y[i] - yr[0]) / (yr[1] - yr[0]) * ph;
            if (squareMarkers) page.square(x, y, radius);
            else page.circle(x, y, radius);
        }
        page.fill().restore();
    }

    private void drawTitle() {
        if (title.isEmpty()) return;
        double fontSize = TITLE_FONT_SIZE * scale;
        double titleWidth = Page.approxTextWidth(title, fontSize);
        double baselineY = height - EDGE_PADDING * scale - fontSize * FONT_ASCENT_FRACTION;
        page.fillColor(COLOR_TITLE_TEXT.r, COLOR_TITLE_TEXT.g, COLOR_TITLE_TEXT.b)
                .text(title, width / 2 - titleWidth / 2, baselineY, Font.HELVETICA_BOLD, fontSize);
    }

    private void drawXLabel(double px, double pw) {
        if (xLabel.isEmpty()) return;
        double fontSize = AXIS_LABEL_FONT_SIZE * scale;
        double labelWidth = Page.approxTextWidth(xLabel, fontSize);
        double baselineY = EDGE_PADDING * scale;
        page.fillColor(COLOR_AXIS_LABEL.r, COLOR_AXIS_LABEL.g, COLOR_AXIS_LABEL.b)
                .text(xLabel, px + pw / 2 - labelWidth / 2, baselineY, Font.HELVETICA, fontSize);
    }

    private void drawYLabel(double py, double ph) {
        if (yLabel.isEmpty()) return;
        double fontSize = AXIS_LABEL_FONT_SIZE * scale;
        double labelWidth = Page.approxTextWidth(yLabel, fontSize);
        double baselineX = EDGE_PADDING * scale + fontSize * FONT_ASCENT_FRACTION;
        page.fillColor(COLOR_AXIS_LABEL.r, COLOR_AXIS_LABEL.g, COLOR_AXIS_LABEL.b)
                .textRotated(yLabel, baselineX, py + ph / 2 - labelWidth / 2, Font.HELVETICA, fontSize, 90);
    }

    private void drawLegend(double px, double py, double pw, double ph) {
        double fontSize = LEGEND_FONT_SIZE * scale;
        double swatch = LEGEND_SWATCH_SIZE * scale;
        double x = px + pw + LEGEND_PLOT_TO_SWATCH_GAP * scale;
        double y = py + ph - swatch;
        for (Layer l : layers) {
            if (l.label == null || l.label.isEmpty()) continue;
            page.fillColor(l.color.r, l.color.g, l.color.b).rect(x, y, swatch, swatch).fill();
            page.fillColor(COLOR_LEGEND_TEXT.r, COLOR_LEGEND_TEXT.g, COLOR_LEGEND_TEXT.b)
                    .text(l.label, x + swatch + LEGEND_SWATCH_TO_TEXT_GAP * scale,
                            y + swatch * LEGEND_TEXT_VERTICAL_OFFSET_FRACTION, Font.HELVETICA, fontSize);
            y -= LEGEND_ROW_HEIGHT * scale;
        }
    }

    private double[] resolveRange(boolean xAxis, double lo, double hi) {
        double mn = Double.POSITIVE_INFINITY, mx = Double.NEGATIVE_INFINITY;
        if (Double.isNaN(lo) || Double.isNaN(hi)) {
            for (Layer l : layers) {
                for (double v : xAxis ? l.x : l.y) {
                    if (!Double.isFinite(v)) continue;
                    mn = Math.min(mn, v);
                    mx = Math.max(mx, v);
                }
            }
            if (mn > mx) {
                mn = 0;
                mx = 1;
            }
            if (mn == mx) {
                mn -= 1;
                mx += 1;
            }
        }
        double pad = (mx - mn) * AUTO_RANGE_PADDING_FRACTION;
        double resLo = Double.isNaN(lo) ? mn - pad : lo;
        double resHi = Double.isNaN(hi) ? mx + pad : hi;
        if (!Double.isFinite(resLo) || !Double.isFinite(resHi)) {
            throw new IllegalStateException("axis limits must be finite, got [" + resLo + ", " + resHi + "]");
        }
        if (resLo == resHi) {
            resLo -= 1;
            resHi += 1;
        } else if (resLo > resHi) {
            throw new IllegalStateException("axis limits are inverted: [" + resLo + ", " + resHi + "]");
        }
        return new double[]{resLo, resHi};
    }

    private static String tickLabel(double value, double range) {
        int decimals = range >= TICK_DECIMALS_ZERO_THRESHOLD ? 0
                : range >= TICK_DECIMALS_ONE_THRESHOLD ? 1 : 2;
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}
