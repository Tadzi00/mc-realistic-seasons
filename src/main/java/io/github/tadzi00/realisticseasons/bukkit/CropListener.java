package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.RawContract;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Stops plant growth below {@code crops.min-temperature}. Outdoors the plain outdoor temperature is used;
 * inside a room the (heated) room temperature is used, so winter farming works only indoors.
 * If the answer is unknown (room scan budget exhausted) growth is cancelled; the plant simply retries on
 * its next random tick, by which time the room is usually cached.
 */
public final class CropListener implements Listener {

    private final RealisticSeasonsPlugin plugin;

    public CropListener(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean tooCold(Block b) {
        Settings s = plugin.settings();
        if (!s.cropsEnabled || !s.isWorldEnabled(b.getWorld())) return false;
        double t = plugin.temperatures().plantTemperature(b);
        return Double.isNaN(t) || t < s.cropMinTemperature;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (tooCold(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (!plugin.settings().cropAffectSpread) return;
        Material m = e.getNewState().getType();
        if (m == Material.FIRE || m == Material.SOUL_FIRE) return; // fire spread is not plant growth
        if (tooCold(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        Settings s = plugin.settings();
        if (!s.cropAffectSaplings) return;
        if (e.isFromBonemeal() && !s.cropAffectBonemeal) return;
        Block b = e.getLocation().getBlock();
        if (tooCold(b)) {
            e.setCancelled(true);
            if (e.getPlayer() != null) tell(e.getPlayer(), b);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        if (!plugin.settings().cropAffectBonemeal) return;
        if (tooCold(e.getBlock())) {
            e.setCancelled(true);
            if (e.getPlayer() != null) tell(e.getPlayer(), e.getBlock());
        }
    }

    private void tell(Player p, Block b) {
        double t = plugin.temperatures().plantTemperature(b);
        String temp = Double.isNaN(t) ? "?" : RawContract.oneDecimal(t);
        p.sendActionBar(Component.text("Too cold for this plant to grow (" + temp + "°C, needs "
                + RawContract.oneDecimal(plugin.settings().cropMinTemperature) + "°C)", NamedTextColor.AQUA));
    }
}
