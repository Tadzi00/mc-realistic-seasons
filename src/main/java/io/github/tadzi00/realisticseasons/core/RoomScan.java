package io.github.tadzi00.realisticseasons.core;

import java.util.BitSet;
import java.util.List;

/**
 * Result of a room flood fill. When {@code inside} is false the other fields describe what was seen
 * before giving up (useful for debugging) but callers must treat the location as outdoors.
 */
public record RoomScan(
        boolean inside,
        OutsideReason reason,
        int volume,
        int smallLeaks,
        int largeLeaks,
        double averageInsulation,
        List<PlacedHeatSource> heatSources,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        BitSet cells
) {

    public enum OutsideReason { NONE, SKY_AT_START, TOO_LARGE, TOO_MANY_OPENINGS, UNLOADED, START_BLOCKED }

    public boolean sealed() {
        return inside && smallLeaks == 0 && largeLeaks == 0;
    }

    public double totalHeatPower() {
        double s = 0;
        for (PlacedHeatSource h : heatSources) s += h.source().power();
        return s;
    }

    public boolean boxContains(int x, int y, int z, int margin) {
        return x >= minX - margin && x <= maxX + margin
                && y >= minY - margin && y <= maxY + margin
                && z >= minZ - margin && z <= maxZ + margin;
    }

    /** True if the given block is one of the room's air cells. */
    public boolean containsCell(int x, int y, int z) {
        if (!inside || cells == null || !boxContains(x, y, z, 0)) return false;
        return cells.get(index(x, y, z));
    }

    int index(int x, int y, int z) {
        int sx = maxX - minX + 1, sy = maxY - minY + 1;
        return ((z - minZ) * sy + (y - minY)) * sx + (x - minX);
    }

    public static RoomScan outside(OutsideReason reason) {
        return new RoomScan(false, reason, 0, 0, 0, 0, List.of(), 0, 0, 0, 0, 0, 0, null);
    }
}
