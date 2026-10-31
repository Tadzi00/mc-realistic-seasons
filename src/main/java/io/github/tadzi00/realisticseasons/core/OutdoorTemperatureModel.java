package io.github.tadzi00.realisticseasons.core;

/**
 * Pure outdoor (ambient air) temperature model, in °C.
 *
 * <pre>
 * T = seasonBase
 *   + (biomeTemp - referenceBiomeTemp) * biomeScale        // biome offset (plains = 0.8 -> 0)
 *   + diurnalAmplitude * cos(2π (timeOfDay - peakTick) / 24000)
 *   - lapseRate * max(0, y - lapseStartY)                    // colder high up
 *   + weather                                                // rain / thunder colder
 *   + noise                                                  // small deterministic noise
 * then, underground (well below the local surface), blend toward a stable cave temperature
 * and add a geothermal gradient below geothermalStartY.
 * </pre>
 */
public final class OutdoorTemperatureModel {

    /** Tunable constants; all have sensible defaults (see DESIGN.md). */
    public record Params(
            double referenceBiomeTemp,
            double biomeScale,
            double diurnalAmplitude,
            double peakTick,
            double lapseRatePerBlock,
            int lapseStartY,
            double rainOffset,
            double thunderOffset,
            double caveTemperature,
            double caveBlendStartDepth,
            double caveBlendFullDepth,
            int geothermalStartY,
            double geothermalPerBlock,
            double noiseAmplitude,
            double noiseCellSize
    ) {
        public static Params defaults() {
            return new Params(0.8, 15.0, 5.0, 8000, 0.05, 64, -3.0, -4.5,
                    10.0, 4.0, 30.0, 0, 0.05, 1.5, 32.0);
        }
    }

    /** Everything location/time dependent the model needs. */
    public record Input(
            double seasonBase,
            double biomeTemperature,
            long timeOfDay,
            int x, int y, int z,
            int surfaceY,
            boolean raining,
            boolean thundering,
            long noiseSeed,
            long day
    ) {
    }

    private final Params p;

    public OutdoorTemperatureModel(Params params) {
        this.p = params;
    }

    public Params params() {
        return p;
    }

    public double temperature(Input in) {
        double t = in.seasonBase();
        t += biomeOffset(in.biomeTemperature());
        t += diurnal(in.timeOfDay());
        t -= p.lapseRatePerBlock() * Math.max(0, in.y() - p.lapseStartY());
        if (in.thundering()) t += p.thunderOffset();
        else if (in.raining()) t += p.rainOffset();
        t += noise(in.x(), in.z(), in.day(), in.noiseSeed());

        // Underground: rock insulates from surface weather; blend toward a stable cave temperature.
        double depth = in.surfaceY() - in.y();
        double f = clamp((depth - p.caveBlendStartDepth()) / Math.max(1e-6, p.caveBlendFullDepth() - p.caveBlendStartDepth()), 0, 1);
        double cave = p.caveTemperature() + Math.max(0, p.geothermalStartY() - in.y()) * p.geothermalPerBlock();
        return t * (1 - f) + cave * f;
    }

    public double biomeOffset(double biomeTemperature) {
        return (biomeTemperature - p.referenceBiomeTemp()) * p.biomeScale();
    }

    public double diurnal(long timeOfDay) {
        double phase = 2 * Math.PI * (floorMod(timeOfDay, 24000) - p.peakTick()) / 24000.0;
        return p.diurnalAmplitude() * Math.cos(phase);
    }

    /** Smooth value noise in [-amplitude, amplitude], deterministic in (x, z, day, seed). */
    public double noise(int x, int z, long day, long seed) {
        if (p.noiseAmplitude() <= 0) return 0;
        double cs = Math.max(1, p.noiseCellSize());
        double fx = x / cs, fz = z / cs;
        long x0 = (long) Math.floor(fx), z0 = (long) Math.floor(fz);
        double tx = smooth(fx - x0), tz = smooth(fz - z0);
        double a = hash01(x0, z0, day, seed), b = hash01(x0 + 1, z0, day, seed);
        double c = hash01(x0, z0 + 1, day, seed), d = hash01(x0 + 1, z0 + 1, day, seed);
        double v = SeasonCalculator.lerp(SeasonCalculator.lerp(a, b, tx), SeasonCalculator.lerp(c, d, tx), tz);
        return (v * 2 - 1) * p.noiseAmplitude();
    }

    static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static double hash01(long x, long z, long day, long seed) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h ^= x * 0xBF58476D1CE4E5B9L;
        h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
        h ^= z * 0xD6E8FEB86659FD93L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= day * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    static double floorMod(long v, long m) {
        return Math.floorMod(v, m);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
