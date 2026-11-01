package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.HeatModel;
import io.github.tadzi00.realisticseasons.core.RoomScan;
import io.github.tadzi00.realisticseasons.core.RoomScanner;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Room cache + thermal state.
 *
 * <ul>
 *   <li>Rooms are cached by their cell set (BitSet over the bounding box). A lookup is a cache hit when the
 *       queried block (or the block above it) is one of the room's air cells.</li>
 *   <li>Any block change inside the bounding box expanded by 1 invalidates the room. Its last temperature
 *       is kept as a "ghost" so a rescan of the same space continues with the same temperature (thermal
 *       inertia survives a door being opened).</li>
 *   <li>Positions found to be outside are cached for {@code performance.outside-cache-seconds}.</li>
 *   <li>Scans are budgeted per tick; {@code force=true} (commands, API) bypasses the budget.</li>
 * </ul>
 * Main thread only.
 */
public final class RoomService {

    public enum State { INSIDE, OUTSIDE, UNKNOWN }

    public record Lookup(State state, Room room) {
        public boolean inside() {
            return state == State.INSIDE;
        }
    }

    private static final Lookup OUTSIDE = new Lookup(State.OUTSIDE, null);
    private static final Lookup UNKNOWN = new Lookup(State.UNKNOWN, null);
    private static final long GHOST_MILLIS = 10 * 60 * 1000L;

    /** A cached room and its stored (inertial) air temperature. */
    public static final class Room {
        public final UUID world;
        public final RoomScan scan;
        public final int anchorX, anchorY, anchorZ;
        final long scannedAt;
        long lastUpdate;
        long lastAccess;
        double temperature;
        double lastOutside;
        double lastEquilibrium;

        Room(UUID world, RoomScan scan, int ax, int ay, int az, long now) {
            this.world = world;
            this.scan = scan;
            this.anchorX = ax;
            this.anchorY = ay;
            this.anchorZ = az;
            this.scannedAt = now;
            this.lastUpdate = now;
            this.lastAccess = now;
        }

        public double temperature() {
            return temperature;
        }

        public double outside() {
            return lastOutside;
        }

        public double equilibrium() {
            return lastEquilibrium;
        }
    }

    private record Ghost(RoomScan box, double temperature, long at) {
    }

    private final RealisticSeasonsPlugin plugin;
    private final Map<UUID, List<Room>> rooms = new HashMap<>();
    private final Map<UUID, List<Ghost>> ghosts = new HashMap<>();
    private final Map<UUID, Map<Long, Long>> outsideUntil = new HashMap<>();
    private int budgetTick = -1;
    private int scansThisTick;
    private int cropScansThisTick;

    public RoomService(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void clear() {
        rooms.clear();
        ghosts.clear();
        outsideUntil.clear();
    }

    private void resetBudget() {
        int t = Bukkit.getCurrentTick();
        if (t != budgetTick) {
            budgetTick = t;
            scansThisTick = 0;
            cropScansThisTick = 0;
        }
    }

    /** Budget kinds. */
    public enum Budget { FORCE, PLAYER, CROP, CACHE_ONLY }

    public Lookup lookup(World w, int x, int y, int z, Budget budget) {
        Settings s = plugin.settings();
        if (!s.roomsEnabled) return OUTSIDE;
        long now = System.currentTimeMillis();
        UUID wid = w.getUID();

        List<Room> list = rooms.get(wid);
        if (list != null) {
            for (Iterator<Room> it = list.iterator(); it.hasNext(); ) {
                Room r = it.next();
                if (r.scan.containsCell(x, y, z) || r.scan.containsCell(x, y + 1, z)) {
                    if (now - r.scannedAt > s.roomRescanMillis && budget != Budget.CACHE_ONLY && hasBudget(budget)) {
                        // periodic refresh: rescan, keep temperature through the ghost mechanism
                        it.remove();
                        addGhost(wid, r);
                        break;
                    }
                    r.lastAccess = now;
                    return new Lookup(State.INSIDE, r);
                }
            }
        }

        Map<Long, Long> out = outsideUntil.get(wid);
        long key = RoomScanner.pack(x, y, z);
        if (out != null) {
            Long until = out.get(key);
            if (until != null) {
                if (until > now) return OUTSIDE;
                out.remove(key);
            }
        }

        if (budget == Budget.CACHE_ONLY || !hasBudget(budget)) return UNKNOWN;
        consumeBudget(budget);

        RoomScanner scanner = new RoomScanner(s.roomMaxVolume, s.roomMaxLargeLeaks);
        RoomScan scan = scanner.scan(new BukkitBlockProbe(w, s), x, y, z);
        if (!scan.inside()) {
            if (s.outsideCacheMillis > 0) {
                Map<Long, Long> m = outsideUntil.computeIfAbsent(wid, k -> new HashMap<>());
                if (m.size() > 50_000) m.clear();
                m.put(key, now + s.outsideCacheMillis);
            }
            return OUTSIDE;
        }
        Room room = new Room(wid, scan, x, y, z, now);
        double outside = plugin.temperatures().outdoor(w, x, y, z);
        double eq = equilibrium(room, outside);
        Double ghost = takeGhost(wid, scan, x, y, z, now);
        room.temperature = ghost != null ? ghost : (s.seedAtEquilibrium ? eq : outside);
        room.lastOutside = outside;
        room.lastEquilibrium = eq;
        rooms.computeIfAbsent(wid, k -> new ArrayList<>()).add(room);
        return new Lookup(State.INSIDE, room);
    }

    private boolean hasBudget(Budget b) {
        resetBudget();
        Settings s = plugin.settings();
        return switch (b) {
            case FORCE -> true;
            case PLAYER -> scansThisTick < s.maxRoomScansPerTick;
            case CROP -> cropScansThisTick < s.cropScansPerTick;
            case CACHE_ONLY -> false;
        };
    }

    private void consumeBudget(Budget b) {
        resetBudget();
        if (b == Budget.CROP) cropScansThisTick++;
        else scansThisTick++;
    }

    double equilibrium(Room r, double outside) {
        HeatModel hm = plugin.settings().heatModel;
        RoomScan sc = r.scan;
        return hm.roomEquilibrium(outside, sc.heatSources(), sc.volume(), sc.averageInsulation(),
                sc.smallLeaks(), sc.largeLeaks());
    }

    /** Advances the room's stored temperature to "now" (exact exponential integration) and returns it. */
    public double roomTemperature(World w, Room r) {
        long now = System.currentTimeMillis();
        double outside = plugin.temperatures().outdoor(w, r.anchorX, r.anchorY, r.anchorZ);
        double eq = equilibrium(r, outside);
        double dt = (now - r.lastUpdate) / 1000.0;
        r.temperature = plugin.settings().heatModel.approach(r.temperature, eq, dt);
        r.lastUpdate = now;
        r.lastOutside = outside;
        r.lastEquilibrium = eq;
        return r.temperature;
    }

    /** Invalidates every cached room whose bounding box (+1) contains the block, and nearby outside entries. */
    public void invalidate(World w, int x, int y, int z) {
        UUID wid = w.getUID();
        List<Room> list = rooms.get(wid);
        if (list != null) {
            for (Iterator<Room> it = list.iterator(); it.hasNext(); ) {
                Room r = it.next();
                if (r.scan.boxContains(x, y, z, 1)) {
                    it.remove();
                    addGhost(wid, r);
                }
            }
        }
        Map<Long, Long> out = outsideUntil.get(wid);
        if (out != null && !out.isEmpty()) {
            // closing the last gap can turn "outside" into a room: forget outside results nearby
            int r = 16;
            out.keySet().removeIf(k -> {
                int kx = RoomScanner.unpackX(k), ky = RoomScanner.unpackY(k), kz = RoomScanner.unpackZ(k);
                return Math.abs(kx - x) <= r && Math.abs(ky - y) <= r && Math.abs(kz - z) <= r;
            });
        }
    }

    private void addGhost(UUID wid, Room r) {
        ghosts.computeIfAbsent(wid, k -> new ArrayList<>())
                .add(new Ghost(r.scan, r.temperature, System.currentTimeMillis()));
    }

    private Double takeGhost(UUID wid, RoomScan scan, int x, int y, int z, long now) {
        List<Ghost> list = ghosts.get(wid);
        if (list == null) return null;
        Double best = null;
        for (Iterator<Ghost> it = list.iterator(); it.hasNext(); ) {
            Ghost g = it.next();
            if (now - g.at > GHOST_MILLIS) {
                it.remove();
                continue;
            }
            if (best == null && (g.box.boxContains(x, y, z, 1) || overlaps(g.box, scan))) {
                best = g.temperature;
                it.remove();
            }
        }
        return best;
    }

    private static boolean overlaps(RoomScan a, RoomScan b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
                && a.minY() <= b.maxY() && a.maxY() >= b.minY()
                && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    /** Periodic housekeeping: drop rooms not accessed recently, old ghosts and expired outside entries. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long expire = plugin.settings().roomExpireMillis;
        for (List<Room> list : rooms.values()) list.removeIf(r -> now - r.lastAccess > expire);
        for (List<Ghost> list : ghosts.values()) list.removeIf(g -> now - g.at > GHOST_MILLIS);
        for (Map<Long, Long> m : outsideUntil.values()) m.values().removeIf(until -> until <= now);
    }

    public int cachedRoomCount() {
        int n = 0;
        for (List<Room> l : rooms.values()) n += l.size();
        return n;
    }
}
