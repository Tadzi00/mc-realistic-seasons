package io.github.tadzi00.realisticseasons.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory grid for room scanner tests. Everything is OPEN air unless set; sky is above y=TOP. */
final class FakeWorld implements BlockProbe {
    static final int TOP = 64;

    private final Map<Long, CellKind> kinds = new HashMap<>();
    private final Map<Long, Double> insulation = new HashMap<>();
    private final Map<Long, HeatSource> heat = new HashMap<>();
    private final Set<Long> unknown = new HashSet<>();

    void set(int x, int y, int z, CellKind k, double ins) {
        kinds.put(RoomScanner.pack(x, y, z), k);
        insulation.put(RoomScanner.pack(x, y, z), ins);
    }

    void solid(int x, int y, int z) {
        set(x, y, z, CellKind.SOLID, 1.0);
    }

    void open(int x, int y, int z) {
        kinds.remove(RoomScanner.pack(x, y, z));
    }

    void heat(int x, int y, int z, HeatSource hs) {
        heat.put(RoomScanner.pack(x, y, z), hs);
    }

    void unknown(int x, int y, int z) {
        unknown.add(RoomScanner.pack(x, y, z));
    }

    /** Hollow box: shell from (x0,y0,z0) to (x1,y1,z1) inclusive, interior is air. */
    void box(int x0, int y0, int z0, int x1, int y1, int z1, double ins) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    if (shell) set(x, y, z, CellKind.SOLID, ins);
                }
    }

    @Override
    public CellKind kind(int x, int y, int z) {
        if (unknown.contains(RoomScanner.pack(x, y, z))) return CellKind.UNKNOWN;
        return kinds.getOrDefault(RoomScanner.pack(x, y, z), CellKind.OPEN);
    }

    @Override
    public double insulation(int x, int y, int z) {
        return insulation.getOrDefault(RoomScanner.pack(x, y, z), 1.0);
    }

    @Override
    public boolean skyAccess(int x, int y, int z) {
        for (int yy = y + 1; yy <= TOP; yy++) {
            if (kinds.containsKey(RoomScanner.pack(x, yy, z))) return false;
        }
        return true;
    }

    @Override
    public HeatSource heatSource(int x, int y, int z) {
        return heat.get(RoomScanner.pack(x, y, z));
    }
}
