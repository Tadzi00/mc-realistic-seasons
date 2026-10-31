package io.github.tadzi00.realisticseasons.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure season calendar math.
 *
 * <p>The calendar is driven by a fractional "season day" value: {@code worldFullTime / 24000 + offset}.
 * One year is {@code 4 * lengthDays} days. Season {@code i} covers days {@code [i*L, (i+1)*L)}.
 */
public final class SeasonCalculator {

    private SeasonCalculator() {
    }

    /** Snapshot of where in the calendar a given day falls. dayOfSeason is 1-based. */
    public record SeasonPosition(Season season, int dayOfSeason, int lengthDays, double fractionOfSeason) {
    }

    public static double yearLength(int lengthDays) {
        return 4.0 * Math.max(1, lengthDays);
    }

    /** Positive modulo on doubles. */
    static double floorMod(double value, double mod) {
        double r = value % mod;
        return r < 0 ? r + mod : r;
    }

    public static SeasonPosition position(double calendarDay, int lengthDays) {
        int len = Math.max(1, lengthDays);
        double inYear = floorMod(calendarDay, yearLength(len));
        int idx = (int) Math.floor(inYear / len);
        if (idx > 3) idx = 3; // guard against floating point edge at the year boundary
        double inSeason = inYear - idx * (double) len;
        int day = (int) Math.floor(inSeason) + 1;
        if (day > len) day = len;
        return new SeasonPosition(Season.values()[idx], day, len, inSeason / len);
    }

    /**
     * Returns the offset that must be added to {@code currentRawDay} (world full time / 24000) so that
     * the calendar reports {@code season} day {@code dayOfSeason} (1-based) right now, keeping the
     * current intra-day fraction.
     */
    public static double offsetFor(double currentRawDay, Season season, int dayOfSeason, int lengthDays) {
        int len = Math.max(1, lengthDays);
        int day = Math.max(1, Math.min(len, dayOfSeason));
        double fraction = floorMod(currentRawDay, 1.0);
        double target = season.ordinal() * (double) len + (day - 1) + fraction;
        return target - currentRawDay;
    }

    /**
     * Season base temperature with optional smooth transitions. Within {@code transitionDays / 2} on
     * either side of a season boundary the base temperature is linearly interpolated between the two
     * neighbouring seasons, so the boundary itself sits exactly at the midpoint. With
     * {@code transitionDays <= 0} the result is a step function.
     */
    public static double seasonalBaseTemperature(double calendarDay, int lengthDays,
                                                 Map<Season, Double> baseTemps, double transitionDays) {
        int len = Math.max(1, lengthDays);
        SeasonPosition pos = position(calendarDay, len);
        double base = baseTemps.getOrDefault(pos.season(), 10.0);
        double half = Math.min(transitionDays / 2.0, len / 2.0);
        if (half <= 0) return base;
        double inSeason = pos.fractionOfSeason() * len; // days since this season started
        double untilEnd = len - inSeason;
        if (inSeason < half) {
            double prev = baseTemps.getOrDefault(pos.season().previous(), base);
            // t = 0.5 at the boundary, 1.0 at `half` days into the season
            double t = 0.5 + 0.5 * (inSeason / half);
            return lerp(prev, base, t);
        }
        if (untilEnd < half) {
            double next = baseTemps.getOrDefault(pos.season().next(), base);
            double t = 0.5 + 0.5 * (untilEnd / half);
            return lerp(next, base, t);
        }
        return base;
    }

    public static Map<Season, Double> defaultBaseTemps() {
        Map<Season, Double> m = new EnumMap<>(Season.class);
        m.put(Season.SPRING, 10.0);
        m.put(Season.SUMMER, 22.0);
        m.put(Season.AUTUMN, 9.0);
        m.put(Season.WINTER, -6.0);
        return m;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
