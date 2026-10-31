package io.github.tadzi00.realisticseasons.core;

/**
 * A heat emitter definition. {@code power} is in abstract "heat units" (see DESIGN.md: a torch is 6,
 * a campfire 40), {@code radius} in blocks is how far the local (radiant) warmth reaches.
 */
public record HeatSource(double power, double radius) {
}
