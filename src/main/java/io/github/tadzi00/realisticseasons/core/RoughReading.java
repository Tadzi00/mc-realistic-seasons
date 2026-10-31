package io.github.tadzi00.realisticseasons.core;

/**
 * The "gut feeling" temperature a player gets without a thermometer: the felt temperature plus a random
 * error, rounded to a coarse step, shown as e.g. {@code ~5°C}. Deliberately inaccurate; the thermometer
 * is the precise instrument.
 */
public final class RoughReading {

    private RoughReading() {
    }

    /**
     * @param tempC    actual felt temperature
     * @param step     rounding step in °C (e.g. 5 → readings like 0, 5, 10)
     * @param maxError maximum random error in °C added before rounding
     * @param rand01   random value in [0, 1)
     */
    public static int estimate(double tempC, int step, double maxError, double rand01) {
        int st = Math.max(1, step);
        double noisy = tempC + (rand01 * 2 - 1) * Math.max(0, maxError);
        return (int) Math.round(noisy / st) * st;
    }

    public static String format(int estimate) {
        return "~" + estimate + "°C";
    }

    /** Ticks until the next rough reading: uniform in [minSeconds, maxSeconds]. */
    public static int nextDelayTicks(int minSeconds, int maxSeconds, double rand01) {
        int lo = Math.max(1, Math.min(minSeconds, maxSeconds));
        int hi = Math.max(lo, maxSeconds);
        return (int) Math.round((lo + rand01 * (hi - lo)) * 20);
    }
}
