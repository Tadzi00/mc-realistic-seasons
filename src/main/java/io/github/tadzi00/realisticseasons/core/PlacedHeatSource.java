package io.github.tadzi00.realisticseasons.core;

/** A heat source at a concrete block position (block centre is x+0.5, y+0.5, z+0.5). */
public record PlacedHeatSource(int x, int y, int z, HeatSource source) {

    public double distanceTo(double px, double py, double pz) {
        double dx = px - (x + 0.5), dy = py - (y + 0.5), dz = pz - (z + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
