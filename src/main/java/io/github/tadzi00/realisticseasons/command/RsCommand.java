package io.github.tadzi00.realisticseasons.command;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /rs reload | /rs thermometer [player] | /rs info */
public final class RsCommand implements CommandExecutor, TabCompleter {

    private final RealisticSeasonsPlugin plugin;

    public RsCommand(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage("RealisticSeasons reloaded (difficulty: "
                        + plugin.settings().preset.name().toLowerCase(Locale.ROOT) + ").");
            }
            case "thermometer" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                target.getInventory().addItem(plugin.thermometer().create());
                sender.sendMessage("Gave a thermometer to " + target.getName() + ".");
            }
            case "info" -> sender.sendMessage("RealisticSeasons " + plugin.getPluginMeta().getVersion()
                    + " | difficulty " + plugin.settings().preset.name().toLowerCase(Locale.ROOT)
                    + " | cached rooms " + plugin.rooms().cachedRoomCount());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) out.addAll(List.of("reload", "thermometer", "info"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("thermometer")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
