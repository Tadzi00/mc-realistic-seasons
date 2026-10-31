package io.github.tadzi00.realisticseasons.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded BFS flood fill that decides whether a point is inside an enclosed room.
 *
 * <p>Algorithm (see DESIGN.md "Room detection"):
 * <ol>
 *   <li>Start at the given cell (if it is not OPEN, try the cell above, e.g. feet in a slab/carpet).</li>
 *   <li>If the start has sky access -> outside.</li>
 *   <li>BFS over OPEN cells. Neighbours that are SOLID / DOOR_CLOSED are boundary faces and contribute
 *       their insulation; SMALL_LEAK neighbours are counted (unique positions) and not traversed.</li>
 *   <li>An OPEN neighbour with sky access is an opening to the outside: it is counted as a LARGE leak
 *       (unique position) and is NOT traversed.</li>
 *   <li>If the volume exceeds {@code maxVolume} or the number of large leaks exceeds
 *       {@code maxLargeLeaks} -> outside.</li>
 * </ol>
 */
public final class RoomScanner {

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final int maxVolume;
    private final int maxLargeLeaks;

    public RoomScanner(int maxVolume, int maxLargeLeaks) {
        this.maxVolume = Math.max(1, maxVolume);
        this.maxLargeLeaks = Math.max(0, maxLargeLeaks);
    }

    public int maxVolume() {
        return maxVolume;
    }

    public RoomScan scan(BlockProbe probe, int sx, int sy, int sz) {
        CellKind startKind = probe.kind(sx, sy, sz);
        if (startKind == CellKind.UNKNOWN) return RoomScan.outside(RoomScan.OutsideReason.UNLOADED);
        if (startKind != CellKind.OPEN) {
            sy += 1;
            startKind = probe.kind(sx, sy, sz);
            if (startKind == CellKind.UNKNOWN) return RoomScan.outside(RoomScan.OutsideReason.UNLOADED);
            if (startKind != CellKind.OPEN) return RoomScan.outside(RoomScan.OutsideReason.START_BLOCKED);
        }
        if (probe.skyAccess(sx, sy, sz)) return RoomScan.outside(RoomScan.OutsideReason.SKY_AT_START);

        Set<Long> visited = new HashSet<>();
        Set<Long> smallLeaks = new HashSet<>();
        Set<Long> largeLeaks = new HashSet<>();
        Map<Long, Double> boundary = new HashMap<>();
        List<PlacedHeatSource> sources = new ArrayList<>();
        Set<Long> sourcePositions = new HashSet<>();
        List<int[]> cellList = new ArrayList<>();

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy, sz});
        visited.add(pack(sx, sy, sz));
        int minX = sx, minY = sy, minZ = sz, maxX = sx, maxY = sy, maxZ = sz;
        double insulationSum = 0;
        int faces = 0;

        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            cellList.add(c);
            if (cellList.size() > maxVolume) return RoomScan.outside(RoomScan.OutsideReason.TOO_LARGE);
            minX = Math.min(minX, c[0]); minY = Math.min(minY, c[1]); minZ = Math.min(minZ, c[2]);
            maxX = Math.max(maxX, c[0]); maxY = Math.max(maxY, c[1]); maxZ = Math.max(maxZ, c[2]);
            addSource(probe, c[0], c[1], c[2], sources, sourcePositions);

            for (int[] d : DIRS) {
                int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                long key = pack(nx, ny, nz);
                if (visited.contains(key)) continue;
                CellKind k = probe.kind(nx, ny, nz);
                switch (k) {
                    case UNKNOWN -> {
                        return RoomScan.outside(RoomScan.OutsideReason.UNLOADED);
                    }
                    case SOLID, DOOR_CLOSED -> {
                        double ins = probe.insulation(nx, ny, nz);
                        insulationSum += ins;
                        faces++;
                        boundary.putIfAbsent(key, ins);
                        addSource(probe, nx, ny, nz, sources, sourcePositions);
                    }
                    case SMALL_LEAK -> {
                        smallLeaks.add(key);
                        addSource(probe, nx, ny, nz, sources, sourcePositions);
                    }
                    case OPEN -> {
                        if (probe.skyAccess(nx, ny, nz)) {
                            largeLeaks.add(key);
                            if (largeLeaks.size() > maxLargeLeaks) {
                                return RoomScan.outside(RoomScan.OutsideReason.TOO_MANY_OPENINGS);
                            }
                        } else {
                            visited.add(key);
                            queue.add(new int[]{nx, ny, nz});
                        }
                    }
                }
            }
        }

        int sxSize = maxX - minX + 1, sySize = maxY - minY + 1;
        BitSet bits = new BitSet();
        for (int[] c : cellList) {
            bits.set(((c[2] - minZ) * sySize + (c[1] - minY)) * sxSize + (c[0] - minX));
        }
        double avgIns = faces == 0 ? 1.0 : insulationSum / faces;
        return new RoomScan(true, RoomScan.OutsideReason.NONE, cellList.size(), smallLeaks.size(), largeLeaks.size(),
                avgIns, List.copyOf(sources), minX, minY, minZ, maxX, maxY, maxZ, bits);
    }

    private static void addSource(BlockProbe probe, int x, int y, int z, List<PlacedHeatSource> out, Set<Long> seen) {
        HeatSource hs = probe.heatSource(x, y, z);
        if (hs != null && seen.add(pack(x, y, z))) {
            out.add(new PlacedHeatSource(x, y, z, hs));
        }
    }

    /** Packs block coordinates (x,z within ±33M, y within ±2048) into a long. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int unpackX(long key) {
        return (int) (key >> 38);
    }

    public static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
