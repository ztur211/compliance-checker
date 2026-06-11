package nz.compliance.engine.rules;

public enum Comparator {
    LTE("≤") { public boolean test(double v, double t) { return v <= t; } },
    GTE("≥") { public boolean test(double v, double t) { return v >= t; } },
    EQ("=")       { public boolean test(double v, double t) { return v == t; } };

    private final String symbol;

    Comparator(String symbol) { this.symbol = symbol; }

    public abstract boolean test(double value, double threshold);

    public String symbol() { return symbol; }
}
