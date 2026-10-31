package io.github.tadzi00.realisticseasons.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

/**
 * Parses {@code [x y z] [world]} for the raw commands. Coordinates accept decimals and, for players, "~" /
 * "~1.5" relative notation. Without coordinates a player/entity sender uses its own location; console/RCON
 * must pass coordinates. World defaults to the sender's world, else the main (first) world.
 */
final class RawTarget {

    private RawTarget() {
    }

    /** Result: either a location or an error message. */
    record Parsed(Location location, String error) {
    }

    static Parsed parse(CommandSender sender, String[] args, int from) {
        int n = args.length - from;
        Location base = sender instanceof Entity e ? e.getLocation() : null;
        if (n == 0) {
            if (base == null) return new Parsed(null, "coordinates required: raw <x> <y> <z> [world]");
            return new Parsed(base, null);
        }
        if (n != 3 && n != 4) return new Parsed(null, "usage: raw [x y z] [world]");
        World world;
        if (n == 4) {
            world = Bukkit.getWorld(args[from + 3]);
            if (world == null) return new Parsed(null, "unknown world: " + args[from + 3]);
        } else {
            world = base != null ? base.getWorld() : Bukkit.getWorlds().get(0);
        }
        try {
            double x = coord(args[from], base == null ? null : base.getX());
            double y = coord(args[from + 1], base == null ? null : base.getY());
            double z = coord(args[from + 2], base == null ? null : base.getZ());
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return new Parsed(null, "invalid coordinates");
            }
            return new Parsed(new Location(world, x, y, z), null);
        } catch (NumberFormatException ex) {
            return new Parsed(null, "invalid coordinates");
        }
    }

    private static double coord(String s, Double base) {
        if (s.startsWith("~")) {
            if (base == null) throw new NumberFormatException("relative coordinates need a player");
            return base + (s.length() == 1 ? 0 : Double.parseDouble(s.substring(1)));
        }
        return Double.parseDouble(s);
    }
}
