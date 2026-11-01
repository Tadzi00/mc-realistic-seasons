package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.HeatModel;
import io.github.tadzi00.realisticseasons.core.HeatSource;
import io.github.tadzi00.realisticseasons.core.OutdoorTemperatureModel;
import io.github.tadzi00.realisticseasons.core.PlacedHeatSource;
import io.github.tadzi00.realisticseasons.core.RawContract;
import io.github.tadzi00.realisticseasons.core.RoomScan;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Glue between the pure temperature/heat models and the world. Main thread only. */
public final class TemperatureService {

    /** Neutral temperature reported for worlds where the plugin is disabled. */
    public static final double DISABLED_WORLD_TEMPERATURE = 20.0;

    /**
     * @param felt    felt temperature at the point (room or outdoor + local heat)
     * @param air     air temperature without local radiant heat (room temperature inside, outdoor outside)
     * @param outside outdoor temperature at the point
     */
    public record Felt(double felt, double air, double outside, RoomService.Lookup lookup) {
        public boolean inside() {
            return lookup != null && lookup.inside();
        }

        public RoomScan scan() {
            return inside() ? lookup.room().scan : null;
        }
    }

    private final RealisticSeasonsPlugin plugin;

    public TemperatureService(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Outdoor air temperature at a block position. Never loads chunks. */
    public double outdoor(World w, int x, int y, int z) {
        Settings s = plugin.settings();
        if (!s.isWorldEnabled(w)) return DISABLED_WORLD_TEMPERATURE;
        switch (w.getEnvironment()) {
            case NETHER:
                return s.netherTemp;
            case THE_END:
                return s.endTemp;
            default:
                break;
        }
        boolean loaded = w.isChunkLoaded(x >> 4, z >> 4);
        double biomeTemp = s.outdoorModel.params().referenceBiomeTemp();
        int surface = y;
        boolean raining = false;
        if (loaded) {
            int cy = Math.max(w.getMinHeight(), Math.min(w.getMaxHeight() - 1, y));
            Block b = w.getBlockAt(x, cy, z);
            biomeTemp = b.getTemperature();
            surface = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            raining = w.hasStorm() && b.getHumidity() > 0.0;
        }
        long fullTime = w.getFullTime();
        OutdoorTemperatureModel.Input in = new OutdoorTemperatureModel.Input(
                plugin.seasons().baseTemperature(w), biomeTemp, w.getTime(), x, y, z, surface,
                raining, raining && w.isThundering(), w.getSeed(), Math.floorDiv(fullTime, 24000L));
        return s.outdoorModel.temperature(in);
    }

    public double outdoor(Location l) {
        return outdoor(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    /** Felt temperature at an exact location. */
    public Felt felt(Location loc, RoomService.Budget budget) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        double outside = outdoor(w, bx, by, bz);
        Settings s = plugin.settings();
        if (!s.isWorldEnabled(w)) return new Felt(outside, outside, outside, null);
        RoomService.Lookup lookup = plugin.rooms().lookup(w, bx, by, bz, budget);
        HeatModel hm = s.heatModel;
        double px = loc.getX(), py = loc.getY() + 1.0, pz = loc.getZ(); // roughly chest height for entities
        if (lookup.inside()) {
            RoomService.Room room = lookup.room();
            double air = plugin.rooms().roomTemperature(w, room);
            RoomScan sc = room.scan;
            double local = hm.localAbove(sc.heatSources(), px, py, pz, air, hm.localWeight(true, sc.smallLeaks(), sc.largeLeaks()));
            return new Felt(air + local, air, outside, lookup);
        }
        double local = s.heatEnabled ? hm.localAbove(scanLocalSources(w, bx, by, bz, s), px, py, pz, outside, 1.0) : 0;
        return new Felt(outside + local, outside, outside, lookup);
    }

    /** Collects heat sources in a cube around a point (outdoor case). Skips unloaded chunks. */
    List<PlacedHeatSource> scanLocalSources(World w, int cx, int cy, int cz, Settings s) {
        List<PlacedHeatSource> out = new ArrayList<>();
        int r = s.localScanRadius;
        if (r <= 0 || s.heatSources.isEmpty()) return out;
        int minY = Math.max(w.getMinHeight(), cy - r), maxY = Math.min(w.getMaxHeight() - 1, cy + r);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    HeatSource hs = BukkitBlockProbe.heatSourceAt(b, s);
                    if (hs != null) out.add(new PlacedHeatSource(x, y, z, hs));
                }
            }
        }
        return out;
    }

    /**
     * Temperature relevant for plant growth. Outdoors this is the plain outdoor temperature (a campfire
     * next to a field does not make it summer); inside a room it is room air + local heat.
     *
     * @return NaN if the answer is unknown because the scan budget is exhausted
     */
    public double plantTemperature(Block block) {
        Settings s = plugin.settings();
        World w = block.getWorld();
        double outside = outdoor(w, block.getX(), block.getY(), block.getZ());
        if (outside >= s.cropMinTemperature) return outside;
        RoomService.Lookup lookup = plugin.rooms().lookup(w, block.getX(), block.getY(), block.getZ(), RoomService.Budget.CROP);
        if (lookup.state() == RoomService.State.UNKNOWN) return Double.NaN;
        if (!lookup.inside()) return outside;
        RoomService.Room room = lookup.room();
        double air = plugin.rooms().roomTemperature(w, room);
        RoomScan sc = room.scan;
        HeatModel hm = s.heatModel;
        return air + hm.localAbove(sc.heatSources(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5,
                air, hm.localWeight(true, sc.smallLeaks(), sc.largeLeaks()));
    }

    public boolean isComfortable(double temp) {
        Settings s = plugin.settings();
        return temp >= s.comfortMin && temp <= s.comfortMax;
    }

    /**
     * Builds the RS|v1 report. For kind "temp" tempC is the felt temperature; for kind "room" it is the room
     * air temperature (inside) or the felt outdoor temperature (outside).
     */
    /** Room air temperature the room is heading to (thermal inertia), or NaN when not inside a room. */
    public double equilibrium(Location loc) {
        Felt f = felt(loc, RoomService.Budget.FORCE);
        return f.inside() ? f.lookup().room().equilibrium() : Double.NaN;
    }

    public RawContract.Report report(Location loc, boolean roomMode) {
        Felt f = felt(loc, RoomService.Budget.FORCE);
        RoomScan sc = f.scan();
        boolean inside = sc != null;
        double temp = roomMode && inside ? f.air() : f.felt();
        String season = plugin.seasons().season(loc.getWorld()).name();
        return new RawContract.Report(inside, inside && sc.sealed(), inside ? sc.volume() : 0,
                inside ? sc.smallLeaks() : 0, inside ? sc.largeLeaks() : 0,
                temp, f.outside(), season, isComfortable(temp));
    }
}
