package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomScannerTest {

    /** 5x3x5 interior (75 air blocks): shell from (0,0,0) to (6,4,6). */
    private static FakeWorld house(double ins) {
        FakeWorld w = new FakeWorld();
        // ground plane so the floor is not floating (irrelevant for the scan, but realistic)
        w.box(0, 0, 0, 6, 4, 6, ins);
        return w;
    }

    private final RoomScanner scanner = new RoomScanner(1000, 8);

    @Test
    void sealedBoxIsSealedRoom() {
        RoomScan r = scanner.scan(house(1.0), 3, 1, 3);
        assertTrue(r.inside());
        assertTrue(r.sealed());
        assertEquals(75, r.volume());
        assertEquals(0, r.smallLeaks());
        assertEquals(0, r.largeLeaks());
        assertEquals(1.0, r.averageInsulation(), 1e-9);
        assertEquals(1, r.minX());
        assertEquals(5, r.maxX());
        assertEquals(3, r.maxY());
    }

    @Test
    void closedDoorKeepsRoomSealed() {
        FakeWorld w = house(1.0);
        w.set(0, 1, 3, CellKind.DOOR_CLOSED, 0.8);
        w.set(0, 2, 3, CellKind.DOOR_CLOSED, 0.8);
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertTrue(r.sealed());
        assertTrue(r.averageInsulation() < 1.0);
    }

    @Test
    void openDoorIsTwoSmallLeaks() {
        FakeWorld w = house(1.0);
        w.set(0, 1, 3, CellKind.SMALL_LEAK, 1.0);
        w.set(0, 2, 3, CellKind.SMALL_LEAK, 1.0);
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertTrue(r.inside());
        assertFalse(r.sealed());
        assertEquals(2, r.smallLeaks());
        assertEquals(0, r.largeLeaks());
        assertEquals(75, r.volume(), "an open door is not traversed");
    }

    @Test
    void holeInWallIsLargeLeakButStillInside() {
        FakeWorld w = house(1.0);
        w.open(0, 2, 3); // missing wall block
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertTrue(r.inside());
        // the hole cell (0,2,3) is covered by the roof block (0,4,3) -> traversed; beyond it (-1,2,3) is sky -> large leak
        assertEquals(1, r.largeLeaks());
        assertEquals(76, r.volume());
        assertFalse(r.sealed());
    }

    @Test
    void missingWholeWallIsOutside() {
        FakeWorld w = house(1.0);
        for (int y = 1; y <= 3; y++) for (int z = 1; z <= 5; z++) w.open(0, y, z);
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertFalse(r.inside());
        assertEquals(RoomScan.OutsideReason.TOO_MANY_OPENINGS, r.reason());
    }

    @Test
    void noRoofIsOutside() {
        FakeWorld w = house(1.0);
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) w.open(x, 4, z);
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertFalse(r.inside());
        assertEquals(RoomScan.OutsideReason.SKY_AT_START, r.reason());
    }

    @Test
    void tooLargeIsOutside() {
        FakeWorld w = new FakeWorld();
        w.box(0, 0, 0, 30, 10, 30, 1.0); // 29*9*29 = 7569 > 1000
        RoomScan r = scanner.scan(w, 5, 1, 5);
        assertFalse(r.inside());
        assertEquals(RoomScan.OutsideReason.TOO_LARGE, r.reason());
        assertTrue(new RoomScanner(10_000, 8).scan(w, 5, 1, 5).inside());
    }

    @Test
    void unloadedChunkIsOutside() {
        FakeWorld w = house(1.0);
        w.unknown(3, 2, 3);
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertFalse(r.inside());
        assertEquals(RoomScan.OutsideReason.UNLOADED, r.reason());
    }

    @Test
    void startInsideBlockShiftsUp() {
        FakeWorld w = house(1.0);
        w.solid(3, 1, 3); // e.g. standing on a slab: feet block is solid
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertTrue(r.inside());
        assertEquals(74, r.volume());
    }

    @Test
    void woolInsulatesBetterThanGlass() {
        RoomScan wool = scanner.scan(house(2.5), 3, 1, 3);
        RoomScan glass = scanner.scan(house(0.4), 3, 1, 3);
        assertEquals(2.5, wool.averageInsulation(), 1e-9);
        assertEquals(0.4, glass.averageInsulation(), 1e-9);
    }

    @Test
    void heatSourcesInAirAndWallsAreCollectedOnce() {
        FakeWorld w = house(1.0);
        w.heat(2, 1, 2, new HeatSource(6, 4));     // torch in the room
        w.heat(6, 1, 3, new HeatSource(30, 5));    // furnace built into the wall, touched by several cells
        RoomScan r = scanner.scan(w, 3, 1, 3);
        assertEquals(2, r.heatSources().size());
        assertEquals(36, r.totalHeatPower(), 1e-9);
    }

    @Test
    void cellMembership() {
        RoomScan r = scanner.scan(house(1.0), 3, 1, 3);
        assertTrue(r.containsCell(1, 1, 1));
        assertTrue(r.containsCell(5, 3, 5));
        assertFalse(r.containsCell(0, 1, 1), "wall is not a room cell");
        assertFalse(r.containsCell(3, 4, 3), "roof is not a room cell");
        assertFalse(r.containsCell(100, 1, 1));
        assertTrue(r.boxContains(0, 1, 1, 1));
    }

    @Test
    void lShapedRoomCellsAreExact() {
        FakeWorld w = new FakeWorld();
        w.box(0, 0, 0, 10, 4, 10, 1.0);
        // fill the x>5,z>5 quadrant to make an L
        for (int x = 6; x <= 9; x++) for (int y = 1; y <= 3; y++) for (int z = 6; z <= 9; z++) w.solid(x, y, z);
        RoomScan r = scanner.scan(w, 2, 1, 2);
        assertTrue(r.inside());
        assertEquals(9 * 3 * 9 - 4 * 3 * 4, r.volume());
        assertFalse(r.containsCell(7, 2, 7));
        assertTrue(r.containsCell(7, 2, 2));
    }

    @Test
    void packRoundTrip() {
        int[][] pts = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {-123456, 2047, 987654}, {5, -2048, 7}};
        for (int[] p : pts) {
            long k = RoomScanner.pack(p[0], p[1], p[2]);
            assertEquals(p[0], RoomScanner.unpackX(k));
            assertEquals(p[1], RoomScanner.unpackY(k));
            assertEquals(p[2], RoomScanner.unpackZ(k));
        }
    }
}
