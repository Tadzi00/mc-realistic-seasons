package io.github.tadzi00.realisticseasons.command;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.Season;
import io.github.tadzi00.realisticseasons.core.SeasonCalculator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /season, /season set &lt;season&gt; [day] [world], /season length &lt;days&gt; [world]. */
public final class SeasonCommand implements CommandExecutor, TabCompleter {

    private final RealisticSeasonsPlugin plugin;

    public SeasonCommand(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    private static World worldOf(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getWorld(args[idx]);
        return sender instanceof Entity e ? e.getWorld() : Bukkit.getWorlds().get(0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            World w = worldOf(sender, args, 99);
            SeasonCalculator.SeasonPosition pos = plugin.seasons().position(w);
            sender.sendMessage("Season in " + w.getName() + ": " + pos.season().name() + " (day "
                    + pos.dayOfSeason() + "/" + pos.lengthDays() + ")");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("set") && !sub.equals("length")) return false;
        if (!sender.hasPermission("realisticseasons.season.admin")) {
            sender.sendMessage("You don't have permission to change seasons.");
            return true;
        }
        if (sub.equals("set")) {
            if (args.length < 2) return false;
            Season season = Season.parse(args[1]);
            if (season == null) {
                sender.sendMessage("Unknown season: " + args[1]);
                return true;
            }
            int day = 1;
            int worldIdx = 2;
            if (args.length >= 3) {
                try {
                    day = Integer.parseInt(args[2]);
                    worldIdx = 3;
                } catch (NumberFormatException ignored) {
                    // third argument is a world name
                }
            }
            World w = worldOf(sender, args, worldIdx);
            if (w == null) {
                sender.sendMessage("Unknown world.");
                return true;
            }
            plugin.seasons().set(w, season, day);
            SeasonCalculator.SeasonPosition pos = plugin.seasons().position(w);
            sender.sendMessage("Season of " + w.getName() + " set to " + pos.season().name() + " day "
                    + pos.dayOfSeason() + "/" + pos.lengthDays() + ".");
            return true;
        }
        if (args.length < 2) return false;
        int days;
        try {
            days = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Not a number: " + args[1]);
            return true;
        }
        if (days < 1) {
            sender.sendMessage("Length must be at least 1 day.");
            return true;
        }
        World w = worldOf(sender, args, 2);
        if (w == null) {
            sender.sendMessage("Unknown world.");
            return true;
        }
        plugin.seasons().setLength(w, days);
        sender.sendMessage("Season length of " + w.getName() + " set to " + days + " days per season.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("realisticseasons.season.admin")) return out;
        if (args.length == 1) {
            out.add("set");
            out.add("length");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (Season s : Season.values()) out.add(s.name().toLowerCase(Locale.ROOT));
        } else if ((args.length == 4 && args[0].equalsIgnoreCase("set")) || (args.length == 3 && args[0].equalsIgnoreCase("length"))) {
            for (World w : Bukkit.getWorlds()) out.add(w.getName());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
