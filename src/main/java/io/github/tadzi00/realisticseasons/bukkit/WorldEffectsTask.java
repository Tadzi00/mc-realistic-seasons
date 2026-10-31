package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Throttled surface weathering near players: freezes exposed water, accumulates snow layers, thaws ice and
 * snow when warm. Each run samples at most {@code columns-per-run} random columns (shared by all players)
 * inside loaded chunks within {@code radius} of a player. Only the topmost (sky-exposed) block is touched.
 *
 * <p>Thaw rule: any exposed ICE / SNOW layer melts when the local temperature is above {@code thaw.above}
 * <b>unless</b> the biome is a cold biome (vanilla temperature below {@code cold-biome-threshold}). The plugin
 * does not track which ice it created (that would need persistent per-block storage).
 */
public final class WorldEffectsTask implements Runnable {

    private final RealisticSeasonsPlugin plugin;

    public WorldEffectsTask(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Settings s = plugin.settings();
        if (!s.effectsEnabled || s.effectsColumnsPerRun <= 0) return;
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (w.getEnvironment() == World.Environment.NORMAL && s.isWorldEnabled(w)) players.add(p);
        }
        if (players.isEmpty()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int perPlayer = Math.max(1, s.effectsColumnsPerRun / players.size());
        int budget = s.effectsColumnsPerRun;
        for (Player p : players) {
            World w = p.getWorld();
            int px = p.getLocation().getBlockX(), pz = p.getLocation().getBlockZ();
            for (int i = 0; i < perPlayer && budget > 0; i++, budget--) {
                int x = px + rnd.nextInt(-s.effectsRadius, s.effectsRadius + 1);
                int z = pz + rnd.nextInt(-s.effectsRadius, s.effectsRadius + 1);
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                processColumn(w, x, z, s, rnd);
            }
        }
    }

    private void processColumn(World w, int x, int z, Settings s, ThreadLocalRandom rnd) {
        Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        Block above = top.getRelative(BlockFace.UP);
        if (above.getY() >= w.getMaxHeight()) return;
        double temp = plugin.temperatures().outdoor(w, x, above.getY(), z);
        Material type = top.getType();
        Material aboveType = above.getType();

        // thin snow layers do not block motion, so they sit above the heightmap block
        if (aboveType == Material.SNOW) {
            handleSnowLayer(above, temp, s, rnd, w);
            return;
        }
        if (type == Material.SNOW) {
            handleSnowLayer(top, temp, s, rnd, w);
            return;
        }

        if (type == Material.WATER && s.freezeEnabled && temp < s.freezeBelow) {
            BlockData data = top.getBlockData();
            if (data instanceof Levelled l && l.getLevel() == 0
                    && rnd.nextDouble() < s.freezeChance * coldFactor(s.freezeBelow - temp)) {
                top.setType(Material.ICE);
            }
            return;
        }

        if (type == Material.ICE && s.thawEnabled && s.thawIce && temp > s.thawAbove && !coldBiome(top, s)
                && rnd.nextDouble() < s.thawChance * coldFactor(temp - s.thawAbove)) {
            top.setType(Material.WATER);
            return;
        }

        if (s.snowEnabled && temp < s.snowBelow && aboveType.isAir()
                && (!s.snowRequiresPrecipitation || w.hasStorm())
                && type.isOccluding() && type != Material.ICE && type != Material.PACKED_ICE
                && type != Material.BLUE_ICE && type != Material.BARRIER
                && rnd.nextDouble() < s.snowChance * coldFactor(s.snowBelow - temp)) {
            above.setType(Material.SNOW);
        }
    }

    private void handleSnowLayer(Block b, double temp, Settings s, ThreadLocalRandom rnd, World w) {
        if (!(b.getBlockData() instanceof Snow snow)) return;
        if (s.snowEnabled && temp < s.snowBelow && (!s.snowRequiresPrecipitation || w.hasStorm())
                && snow.getLayers() < s.snowMaxLayers
                && rnd.nextDouble() < s.snowChance * coldFactor(s.snowBelow - temp)) {
            snow.setLayers(snow.getLayers() + 1);
            b.setBlockData(snow);
        } else if (s.thawEnabled && s.thawSnow && temp > s.thawAbove && !coldBiome(b, s)
                && rnd.nextDouble() < s.thawChance * coldFactor(temp - s.thawAbove)) {
            if (snow.getLayers() <= 1) b.setType(Material.AIR);
            else {
                snow.setLayers(snow.getLayers() - 1);
                b.setBlockData(snow);
            }
        }
    }

    /** 0.2 just past the threshold, 1.0 at 4°C past it: colder = faster freezing, warmer = faster thaw. */
    static double coldFactor(double degreesPast) {
        return Math.min(1.0, 0.2 + Math.max(0, degreesPast) * 0.2);
    }

    private static boolean coldBiome(Block b, Settings s) {
        return b.getTemperature() < s.coldBiomeThreshold;
    }
}
