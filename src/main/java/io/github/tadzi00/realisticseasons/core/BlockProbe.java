package io.github.tadzi00.realisticseasons.core;

/**
 * Abstraction over the world used by {@link RoomScanner}, so the flood fill can be unit tested with a
 * fake grid. The Bukkit implementation lives in the bukkit package.
 */
public interface BlockProbe {

    CellKind kind(int x, int y, int z);

    /** Insulation of a boundary block (only called for SOLID / DOOR_CLOSED). 1.0 = stone baseline. */
    double insulation(int x, int y, int z);

    /** True if nothing above this cell blocks the sky (precipitation/sky reaches it). */
    boolean skyAccess(int x, int y, int z);

    /** Heat source at this position, or null. */
    HeatSource heatSource(int x, int y, int z);
}
