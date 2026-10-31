package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

/**
 * Invalidates cached rooms whenever a block inside (or bordering) their bounding box changes. Invalidation is
 * applied immediately AND one tick later, because the block state only changes after the event.
 */
public final class RoomInvalidationListener implements Listener {

    private final RealisticSeasonsPlugin plugin;

    public RoomInvalidationListener(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    private void invalidate(Block b) {
        plugin.rooms().invalidate(b.getWorld(), b.getX(), b.getY(), b.getZ());
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.rooms().invalidate(b.getWorld(), b.getX(), b.getY(), b.getZ()));
    }

    private void invalidateAll(List<Block> blocks) {
        for (Block b : blocks) plugin.rooms().invalidate(b.getWorld(), b.getX(), b.getY(), b.getZ());
        List<Block> copy = List.copyOf(blocks);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Block b : copy) plugin.rooms().invalidate(b.getWorld(), b.getX(), b.getY(), b.getZ());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        invalidate(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        invalidateAll(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        invalidateAll(e.blockList());
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        invalidate(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        invalidateAll(e.getBlocks());
        invalidate(e.getBlock().getRelative(e.getDirection()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        invalidateAll(e.getBlocks());
        invalidate(e.getBlock().getRelative(e.getDirection()));
    }

    /** Redstone-powered doors/trapdoors. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(BlockRedstoneEvent e) {
        if (e.getBlock().getBlockData() instanceof Openable) invalidate(e.getBlock());
    }

    /** Doors, trapdoors, fence gates opened by hand; campfires/candles lit or doused by hand. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getBlockData() instanceof Openable || plugin.settings().heatSources.containsKey(b.getType())) {
            invalidate(b);
        }
    }
}
