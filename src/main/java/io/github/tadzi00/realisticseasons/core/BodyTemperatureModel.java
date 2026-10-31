package io.github.tadzi00.realisticseasons.core;

/**
 * Pure body (core) temperature model. Rates are in °C of core temperature per minute.
 *
 * <pre>
 * feltEff = felt - (wet ? wetFeltPenalty : 0)
 * cold drive  = -coolingRate · (comfortMin - feltEff) · (wet ? wetMultiplier : 1) · coolingMultiplier / (1 + clothing)
 * hot drive   = +heatingRate · (feltEff - comfortMax) · (1 + clothing · clothingHeatFactor)   (only if heat stroke enabled)
 * cold drive: core += (drive + metabolicHeat + sprint) · dt          (metabolic heat only while core < normal)
 * hot drive : core += (drive - sweating) · dt
 * no drive  : core approaches normal at recoveryRate (no overshoot); sprinting may lift it toward normal
 * </pre>
 */
public final class BodyTemperatureModel {

    public record Params(
            double normal,
            double comfortMin,
            double comfortMax,
            double coolingRate,
            double heatingRate,
            double recoveryRate,
            double metabolicHeat,
            double sweating,
            double sprintWarm,
            double wetFeltPenalty,
            double wetMultiplier,
            double clothingHeatFactor,
            double coolingMultiplier,
            boolean heatStrokeEnabled,
            double coldThreshold,
            double shiveringThreshold,
            double hypothermiaThreshold,
            double hotThreshold,
            double heatStrokeThreshold,
            double minCore,
            double maxCore
    ) {
        public static Params defaults() {
            return new Params(37.0, 12.0, 28.0, 0.03, 0.02, 0.6, 0.1, 0.1, 0.3, 4.0, 1.5, 0.3, 1.0, false,
                    36.0, 35.0, 33.0, 38.0, 39.5, 25.0, 43.0);
        }
    }

    public record Input(double core, double felt, double clothingInsulation, boolean wet, boolean sprinting,
                        double dtSeconds) {
    }

    private final Params p;

    public BodyTemperatureModel(Params params) {
        this.p = params;
    }

    public Params params() {
        return p;
    }

    /** Environmental drive in °C/min (negative = cooling). Zero inside the comfort band. */
    public double drive(double felt, double clothing, boolean wet) {
        double feltEff = felt - (wet ? p.wetFeltPenalty() : 0);
        double c = Math.max(0, clothing);
        if (feltEff < p.comfortMin()) {
            return -p.coolingRate() * (p.comfortMin() - feltEff) * (wet ? p.wetMultiplier() : 1.0)
                    * p.coolingMultiplier() / (1 + c);
        }
        if (p.heatStrokeEnabled() && feltEff > p.comfortMax()) {
            return p.heatingRate() * (feltEff - p.comfortMax()) * (1 + c * p.clothingHeatFactor());
        }
        return 0;
    }

    public double step(Input in) {
        double dtMin = in.dtSeconds() / 60.0;
        if (dtMin <= 0) return in.core();
        double core = in.core();
        double drive = drive(in.felt(), in.clothingInsulation(), in.wet());
        double sprint = in.sprinting() ? p.sprintWarm() : 0;
        if (drive < 0) {
            double metabolic = core < p.normal() ? p.metabolicHeat() : 0;
            core += (drive + metabolic + sprint) * dtMin;
            // the body never warms itself above normal while the environment is cold
            if (drive + metabolic + sprint > 0) core = Math.min(core, Math.max(in.core(), p.normal()));
        } else if (drive > 0) {
            core += (drive - p.sweating() + sprint) * dtMin;
            if (drive - p.sweating() + sprint < 0) core = Math.max(core, Math.min(in.core(), p.normal()));
        } else {
            double step = p.recoveryRate() * dtMin;
            if (core < p.normal()) {
                core = Math.min(p.normal(), core + step + sprint * dtMin);
            } else if (core > p.normal()) {
                core = Math.max(p.normal(), core - step);
            }
        }
        return Math.max(p.minCore(), Math.min(p.maxCore(), core));
    }

    public BodyStage stage(double core) {
        if (p.heatStrokeEnabled() && core >= p.heatStrokeThreshold()) return BodyStage.HEAT_STROKE;
        if (p.heatStrokeEnabled() && core >= p.hotThreshold()) return BodyStage.HOT;
        if (core < p.hypothermiaThreshold()) return BodyStage.HYPOTHERMIA;
        if (core < p.shiveringThreshold()) return BodyStage.SHIVERING;
        if (core < p.coldThreshold()) return BodyStage.COLD;
        return BodyStage.COMFORTABLE;
    }

    public boolean isComfortable(double feltTemperature) {
        return feltTemperature >= p.comfortMin() && feltTemperature <= p.comfortMax();
    }
}
