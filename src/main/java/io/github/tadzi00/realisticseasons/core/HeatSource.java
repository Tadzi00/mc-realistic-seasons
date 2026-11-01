package io.github.tadzi00.realisticseasons.core;

/**
 * A heat emitter definition. {@code power} is in abstract "heat units" (see DESIGN.md: a torch is 6,
 * a campfire 40), {@code radius} in blocks is how far the local (radiant) warmth reaches.
 * {@code maxTempC}: this source never heats a room (or a spot near it) above this temperature; NaN = no limit.
 */
public record HeatSource(double power, double radius, double maxTempC) {

    public HeatSource(double power, double radius) {
        this(power, radius, Double.NaN);
    }

    public boolean capped() {
        return !Double.isNaN(maxTempC);
    }
}
