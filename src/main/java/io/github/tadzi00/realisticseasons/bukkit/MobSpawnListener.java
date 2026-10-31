package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.Season;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Seasonal multipliers for NATURAL spawns. Multiplier &lt; 1 cancels that fraction of spawns; &gt; 1 spawns
 * extra copies next tick (reason CUSTOM, so they never re-trigger this listener). Winter replacements turn
 * e.g. skeletons into strays when it is below freezing.
 */
public final class MobSpawnListener implements Listener {

    private final RealisticSeasonsPlugin plugin;

    public MobSpawnListener(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Settings s = plugin.settings();
        World w = e.getLocation().getWorld();
        if (!s.mobsEnabled || !s.seasonsEnabled || !s.isWorldEnabled(w) || w.getEnvironment() != World.Environment.NORMAL) return;
        Season season = plugin.seasons().season(w);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LivingEntity ent = e.getEntity();
        EntityType type = e.getEntityType();
        Location loc = e.getLocation();

        if (season == Season.WINTER && s.replacementsEnabled) {
            Settings.Replacement rep = s.replacements.get(type);
            if (rep != null && rnd.nextDouble() < rep.chance()
                    && plugin.temperatures().outdoor(loc) < s.replacementsBelow) {
                e.setCancelled(true);
                Location at = loc.clone();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (at.isChunkLoaded()) at.getWorld().spawnEntity(at, rep.to());
                });
                return;
            }
        }

        Settings.MobSeason ms = s.mobSeasons.get(season);
        if (ms == null) return;
        double m = ms.entities().getOrDefault(type, ms.defaultMultiplier());
        if (season == Season.WINTER && ent instanceof Monster) m *= s.mult.winterHostileSpawn();
        if (m < 1.0) {
            if (rnd.nextDouble() >= m) e.setCancelled(true);
        } else if (m > 1.0 && s.mobsMaxExtra > 0) {
            double extra = m - 1.0;
            int n = (int) Math.floor(extra) + (rnd.nextDouble() < extra - Math.floor(extra) ? 1 : 0);
            n = Math.min(n, s.mobsMaxExtra);
            if (n <= 0) return;
            Location at = loc.clone();
            int count = n;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!at.isChunkLoaded()) return;
                for (int i = 0; i < count; i++) at.getWorld().spawnEntity(at, type);
            });
        }
    }
}
