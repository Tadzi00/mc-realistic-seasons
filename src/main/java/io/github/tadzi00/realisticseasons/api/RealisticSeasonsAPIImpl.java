package io.github.tadzi00.realisticseasons.api;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.bukkit.RoomService;
import io.github.tadzi00.realisticseasons.bukkit.TemperatureService;
import io.github.tadzi00.realisticseasons.core.RawContract;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Implementation registered in the ServicesManager. Behaviour is part of the stable v1 contract. */
public final class RealisticSeasonsAPIImpl implements RealisticSeasonsAPI {

    private final RealisticSeasonsPlugin plugin;

    public RealisticSeasonsAPIImpl(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getSeason(World world) {
        return plugin.seasons().season(world).name();
    }

    @Override
    public double getTemperature(Location loc) {
        return plugin.temperatures().felt(loc, RoomService.Budget.FORCE).felt();
    }

    @Override
    public boolean isInsideSealedRoom(Location loc) {
        TemperatureService.Felt f = plugin.temperatures().felt(loc, RoomService.Budget.FORCE);
        return f.inside() && f.scan().sealed();
    }

    @Override
    public boolean isComfortable(Location loc) {
        return plugin.temperatures().isComfortable(getTemperature(loc));
    }

    @Override
    public String getRoomJson(Location loc) {
        return RawContract.json(plugin.temperatures().report(loc, true));
    }

    @Override
    public double getBodyTemperature(Player p) {
        return plugin.body().bodyTemperature(p);
    }
}
