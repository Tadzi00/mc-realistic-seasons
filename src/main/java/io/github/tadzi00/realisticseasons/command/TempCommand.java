package io.github.tadzi00.realisticseasons.command;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.bukkit.RoomService;
import io.github.tadzi00.realisticseasons.bukkit.TemperatureService;
import io.github.tadzi00.realisticseasons.core.BodyStage;
import io.github.tadzi00.realisticseasons.core.RawContract;
import io.github.tadzi00.realisticseasons.core.SeasonCalculator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /temp and /temp raw [x y z] [world]. Also handles /room via {@link #roomMode}. */
public final class TempCommand implements CommandExecutor, TabCompleter {

    private final RealisticSeasonsPlugin plugin;
    private final boolean roomMode;

    public TempCommand(RealisticSeasonsPlugin plugin, boolean roomMode) {
        this.plugin = plugin;
        this.roomMode = roomMode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("raw")) {
            raw(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Console usage: /" + label + " raw <x> <y> <z> [world]");
            return true;
        }
        if (roomMode) room(p);
        else temp(p);
        return true;
    }

    private void raw(CommandSender sender, String[] args) {
        String kind = roomMode ? "room" : "temp";
        if (!sender.hasPermission("realisticseasons.raw")) {
            sender.sendMessage(RawContract.errorLine("no permission"));
            return;
        }
        // raw player <name>: that player's position, plus body temperature and stage
        if (args.length >= 3 && args[1].equalsIgnoreCase("player")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(RawContract.errorLine("player not online: " + args[2]));
                return;
            }
            sender.sendMessage(RawContract.lineWithBody(kind, plugin.temperatures().report(target.getLocation(), roomMode),
                    plugin.body().bodyTemperature(target), plugin.body().stage(target).name()));
            return;
        }
        RawTarget.Parsed t = RawTarget.parse(sender, args, 1);
        if (t.error() != null) {
            sender.sendMessage(RawContract.errorLine(t.error()));
            return;
        }
        Location loc = t.location();
        if (!loc.isChunkLoaded()) {
            sender.sendMessage(RawContract.errorLine("chunk not loaded"));
            return;
        }
        sender.sendMessage(RawContract.line(kind, plugin.temperatures().report(loc, roomMode)));
    }

    private void temp(Player p) {
        TemperatureService.Felt f = plugin.temperatures().felt(p.getLocation(), RoomService.Budget.FORCE);
        SeasonCalculator.SeasonPosition pos = plugin.seasons().position(p.getWorld());
        double body = plugin.body().bodyTemperature(p);
        BodyStage stage = plugin.body().stage(p);
        boolean comfy = plugin.temperatures().isComfortable(f.felt());
        p.sendMessage(Component.text("Temperature here: ", NamedTextColor.GRAY)
                .append(Component.text(RawContract.oneDecimal(f.felt()) + "°C", comfy ? NamedTextColor.GREEN : NamedTextColor.AQUA))
                .append(Component.text(comfy ? " (comfortable)" : " (uncomfortable)", NamedTextColor.DARK_GRAY)));
        p.sendMessage(Component.text("Outside: " + RawContract.oneDecimal(f.outside()) + "°C  |  "
                + (f.inside() ? (f.scan().sealed() ? "inside a sealed room" : "inside a leaky room") : "outdoors"), NamedTextColor.GRAY));
        p.sendMessage(Component.text("Body: " + RawContract.oneDecimal(body) + "°C ("
                + stage.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Season: " + pos.season().name() + " day " + pos.dayOfSeason() + "/" + pos.lengthDays(),
                NamedTextColor.GRAY));
    }

    private void room(Player p) {
        TemperatureService.Felt f = plugin.temperatures().felt(p.getLocation(), RoomService.Budget.FORCE);
        if (!f.inside()) {
            p.sendMessage(Component.text("You are not inside a room (no roof, too large, or too many openings). Outside: "
                    + RawContract.oneDecimal(f.outside()) + "°C", NamedTextColor.YELLOW));
            return;
        }
        var sc = f.scan();
        RoomService.Room room = f.lookup().room();
        p.sendMessage(Component.text("Room: " + sc.volume() + " blocks, "
                + (sc.sealed() ? "sealed" : "NOT sealed"), sc.sealed() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        p.sendMessage(Component.text("Leaks: " + sc.smallLeaks() + " small (open doors, panes, fences), "
                + sc.largeLeaks() + " large (holes to outside)", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Heat sources: " + sc.heatSources().size() + " (total power "
                + RawContract.oneDecimal(sc.totalHeatPower()) + "), wall insulation "
                + RawContract.oneDecimal(sc.averageInsulation()), NamedTextColor.GRAY));
        p.sendMessage(Component.text("Room air: " + RawContract.oneDecimal(f.air()) + "°C (heading to "
                + RawContract.oneDecimal(room.equilibrium()) + "°C), here: " + RawContract.oneDecimal(f.felt())
                + "°C, outside: " + RawContract.oneDecimal(f.outside()) + "°C", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("realisticseasons.raw")) return List.of("raw");
        return List.of();
    }
}
