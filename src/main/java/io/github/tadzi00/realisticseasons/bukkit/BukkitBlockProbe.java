package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.core.BlockProbe;
import io.github.tadzi00.realisticseasons.core.CellKind;
import io.github.tadzi00.realisticseasons.core.HeatSource;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;

/**
 * Bukkit implementation of {@link BlockProbe}. Never loads chunks: unloaded chunks report UNKNOWN, which makes
 * the scanner treat the position as outside.
 */
public final class BukkitBlockProbe implements BlockProbe {

    private final World world;
    private final Settings s;
    private final int minY;
    private final int maxY;
    private int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
    private boolean lastLoaded;

    public BukkitBlockProbe(World world, Settings settings) {
        this.world = world;
        this.s = settings;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
    }

    private boolean loaded(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (cx != lastCx || cz != lastCz) {
            lastCx = cx;
            lastCz = cz;
            lastLoaded = world.isChunkLoaded(cx, cz);
        }
        return lastLoaded;
    }

    @Override
    public CellKind kind(int x, int y, int z) {
        if (y < minY) return CellKind.SOLID;
        if (y >= maxY) return CellKind.OPEN; // skyAccess() reports true -> opening to the outside
        if (!loaded(x, z)) return CellKind.UNKNOWN;
        Block b = world.getBlockAt(x, y, z);
        return classify(b.getType(), b);
    }

    CellKind classify(Material m, Block b) {
        if (m.isAir()) return CellKind.OPEN;
        if (Tag.DOORS.isTagged(m)) {
            BlockData data = b.getBlockData();
            return data instanceof Door door && door.isOpen() ? CellKind.SMALL_LEAK : CellKind.DOOR_CLOSED;
        }
        if (Tag.TRAPDOORS.isTagged(m)) {
            BlockData data = b.getBlockData();
            return data instanceof TrapDoor td && td.isOpen() ? CellKind.SMALL_LEAK : CellKind.DOOR_CLOSED;
        }
        if (Tag.FENCE_GATES.isTagged(m)) {
            // a fence gate is always a leaky gap (it is a fence): open or closed, it counts as a small leak
            return CellKind.SMALL_LEAK;
        }
        if (s.smallLeakMaterials.contains(m)) return CellKind.SMALL_LEAK;
        if (m.isSolid()) return CellKind.SOLID;
        return CellKind.OPEN;
    }

    @Override
    public double insulation(int x, int y, int z) {
        if (y < minY || y >= maxY || !loaded(x, z)) return s.defaultInsulation;
        Material m = world.getBlockAt(x, y, z).getType();
        if (Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m)) return s.doorInsulation;
        return s.insulation.getOrDefault(m, s.defaultInsulation);
    }

    @Override
    public boolean skyAccess(int x, int y, int z) {
        if (y >= maxY) return true;
        if (y < minY || !loaded(x, z)) return false;
        return y > world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    @Override
    public HeatSource heatSource(int x, int y, int z) {
        if (y < minY || y >= maxY || !loaded(x, z)) return null;
        return heatSourceAt(world.getBlockAt(x, y, z), s);
    }

    static HeatSource heatSourceAt(Block b, Settings s) {
        Settings.HeatSourceDef def = s.heatSources.get(b.getType());
        if (def == null) return null;
        if (def.requiresLit()) {
            BlockData data = b.getBlockData();
            if (!(data instanceof Lightable l) || !l.isLit()) return null;
        }
        return def.source();
    }
}
