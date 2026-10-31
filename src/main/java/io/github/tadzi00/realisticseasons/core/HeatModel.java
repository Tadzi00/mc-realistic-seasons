package io.github.tadzi00.realisticseasons.core;

import java.util.Collection;

/**
 * Pure room heat model. See DESIGN.md "Heat model" for the full rationale.
 *
 * <pre>
 * sealedRise   = Σpower · insulation / (lossCoefficient · volume^volumeExponent)
 * retention    = exp(-(smallLeaks · smallLeakWeight + largeLeaks · largeLeakWeight))
 * T_room_eq    = T_out + retention · sealedRise
 * local(d)     = power · localGain / (1 + d) · (1 - d / radius)      for d < radius, else 0
 * localWeight  = minLocalWeight + (1 - minLocalWeight) · (1 - retention)   (inside a room)
 *              = 1                                                     (outside)
 * felt         = T_room (or T_out) + localWeight · min(maxLocal, Σ local)
 * inertia      : T(t+dt) = T_eq + (T(t) - T_eq) · exp(-dt / tau)
 * </pre>
 */
public final class HeatModel {

    public record Params(
            double lossCoefficient,
            double volumeExponent,
            double smallLeakWeight,
            double largeLeakWeight,
            double localGain,
            double minLocalWeight,
            double maxLocalContribution,
            double maxRoomRise,
            double thermalTimeConstantSeconds,
            double heatPowerMultiplier
    ) {
        public static Params defaults() {
            return new Params(0.1, 2.0 / 3.0, 0.12, 0.6, 1.0, 0.3, 40.0, 45.0, 120.0, 1.0);
        }
    }

    private final Params p;

    public HeatModel(Params params) {
        this.p = params;
    }

    public Params params() {
        return p;
    }

    /** Temperature rise above outside of a perfectly sealed room. */
    public double sealedRise(double totalPower, int volume, double insulation) {
        if (volume <= 0 || totalPower <= 0) return 0;
        double denom = p.lossCoefficient() * Math.pow(volume, p.volumeExponent());
        double rise = totalPower * p.heatPowerMultiplier() * Math.max(0, insulation) / Math.max(1e-6, denom);
        return Math.min(rise, p.maxRoomRise());
    }

    /** Fraction (0..1] of the sealed temperature rise that a leaky room retains. */
    public double retention(int smallLeaks, int largeLeaks) {
        return Math.exp(-(Math.max(0, smallLeaks) * p.smallLeakWeight() + Math.max(0, largeLeaks) * p.largeLeakWeight()));
    }

    public double roomEquilibrium(double outside, double totalPower, int volume, double insulation,
                                  int smallLeaks, int largeLeaks) {
        return outside + retention(smallLeaks, largeLeaks) * sealedRise(totalPower, volume, insulation);
    }

    /** Local radiant contribution of a single source at distance d. */
    public double localContribution(HeatSource src, double distance) {
        if (src.radius() <= 0 || distance >= src.radius()) return 0;
        double d = Math.max(0, distance);
        return src.power() * p.heatPowerMultiplier() * p.localGain() / (1 + d) * (1 - d / src.radius());
    }

    public double localSum(Collection<PlacedHeatSource> sources, double x, double y, double z) {
        double sum = 0;
        for (PlacedHeatSource s : sources) {
            sum += localContribution(s.source(), s.distanceTo(x, y, z));
        }
        return Math.min(sum, p.maxLocalContribution());
    }

    public double localWeight(boolean inside, int smallLeaks, int largeLeaks) {
        if (!inside) return 1.0;
        double r = retention(smallLeaks, largeLeaks);
        return p.minLocalWeight() + (1 - p.minLocalWeight()) * (1 - r);
    }

    /** Exponential approach of a stored temperature toward equilibrium over dtSeconds. */
    public double approach(double current, double equilibrium, double dtSeconds) {
        if (dtSeconds <= 0) return current;
        double tau = p.thermalTimeConstantSeconds();
        if (tau <= 0) return equilibrium;
        return equilibrium + (current - equilibrium) * Math.exp(-dtSeconds / tau);
    }
}
