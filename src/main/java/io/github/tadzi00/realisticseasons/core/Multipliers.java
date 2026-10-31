package io.github.tadzi00.realisticseasons.core;

/**
 * Difficulty knobs. Fields ending in "Offset" are additive (°C), all others are multiplicative.
 */
public record Multipliers(
        double coolingRate,
        double freezeDamage,
        double waterFreezeSpeed,
        double snowAccumulation,
        double cropCutoffOffset,
        double winterTemperatureOffset,
        double seasonLength,
        double roomMaxVolume,
        double heatSourcePower,
        double winterHostileSpawn
) {
    public static final Multipliers IDENTITY = new Multipliers(1, 1, 1, 1, 0, 0, 1, 1, 1, 1);

    /** Multiplies multiplicative fields and adds additive offsets. */
    public Multipliers combine(Multipliers o) {
        return new Multipliers(
                coolingRate * o.coolingRate,
                freezeDamage * o.freezeDamage,
                waterFreezeSpeed * o.waterFreezeSpeed,
                snowAccumulation * o.snowAccumulation,
                cropCutoffOffset + o.cropCutoffOffset,
                winterTemperatureOffset + o.winterTemperatureOffset,
                seasonLength * o.seasonLength,
                roomMaxVolume * o.roomMaxVolume,
                heatSourcePower * o.heatSourcePower,
                winterHostileSpawn * o.winterHostileSpawn);
    }
}
