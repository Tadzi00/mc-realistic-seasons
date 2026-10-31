package io.github.tadzi00.realisticseasons.core;

import java.util.Locale;

/**
 * STABLE CONTRACT (v1). Another project parses this output; do not change field names, order or
 * formatting without bumping the version tag (RS|v2|...). See DESIGN.md "Raw contract".
 *
 * <pre>RS|v1|temp|{"inside":true,"sealed":true,"volume":75,"leaks":{"small":0,"large":0},"tempC":18.3,"outsideC":-5.2,"season":"WINTER","comfortable":true}</pre>
 */
public final class RawContract {

    public static final String PREFIX = "RS|v1|";

    private RawContract() {
    }

    public record Report(boolean inside, boolean sealed, int volume, int smallLeaks, int largeLeaks,
                         double tempC, double outsideC, String season, boolean comfortable) {
    }

    public static String json(Report r) {
        return "{\"inside\":" + r.inside()
                + ",\"sealed\":" + r.sealed()
                + ",\"volume\":" + (r.inside() ? r.volume() : 0)
                + ",\"leaks\":{\"small\":" + r.smallLeaks() + ",\"large\":" + r.largeLeaks() + "}"
                + ",\"tempC\":" + oneDecimal(r.tempC())
                + ",\"outsideC\":" + oneDecimal(r.outsideC())
                + ",\"season\":\"" + escape(r.season().toUpperCase(Locale.ROOT)) + "\""
                + ",\"comfortable\":" + r.comfortable()
                + "}";
    }

    public static String line(String kind, Report r) {
        return PREFIX + kind + "|" + json(r);
    }

    /** Appends extra JSON fields (e.g. {@code "equilibriumC":21.4}) before the closing brace. Additive v1. */
    public static String withFields(String line, String fields) {
        if (fields == null || fields.isEmpty() || !line.endsWith("}")) return line;
        return line.substring(0, line.length() - 1) + "," + fields + "}";
    }

    /**
     * Same line plus the player's body: {@code ...,"comfortable":false,"bodyC":34.1,"stage":"SHIVERING"}.
     * Additive (v1 readers ignore unknown fields); produced by {@code /temp raw player <name>}.
     */
    public static String lineWithBody(String kind, Report r, double bodyC, String stage) {
        String j = json(r);
        return PREFIX + kind + "|" + j.substring(0, j.length() - 1)
                + ",\"bodyC\":" + oneDecimal(bodyC)
                + ",\"stage\":\"" + escape(stage.toUpperCase(Locale.ROOT)) + "\"}";
    }

    public static String errorLine(String message) {
        return PREFIX + "error|{\"error\":\"" + escape(message) + "\"}";
    }

    /** One decimal place, '.' separator, never "-0.0", never NaN/Infinity (JSON-safe). */
    public static String oneDecimal(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) v = 0;
        double rounded = Math.round(v * 10.0) / 10.0;
        if (rounded == 0) rounded = 0; // normalises -0.0
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
