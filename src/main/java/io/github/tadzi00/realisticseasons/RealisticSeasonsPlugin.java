package io.github.tadzi00.realisticseasons;

import io.github.tadzi00.realisticseasons.api.RealisticSeasonsAPI;
import io.github.tadzi00.realisticseasons.api.RealisticSeasonsAPIImpl;
import io.github.tadzi00.realisticseasons.bukkit.BodyService;
import io.github.tadzi00.realisticseasons.bukkit.CropListener;
import io.github.tadzi00.realisticseasons.bukkit.MobSpawnListener;
import io.github.tadzi00.realisticseasons.bukkit.RoomInvalidationListener;
import io.github.tadzi00.realisticseasons.bukkit.RoomService;
import io.github.tadzi00.realisticseasons.bukkit.SeasonService;
import io.github.tadzi00.realisticseasons.bukkit.Settings;
import io.github.tadzi00.realisticseasons.bukkit.TemperatureService;
import io.github.tadzi00.realisticseasons.bukkit.Thermometer;
import io.github.tadzi00.realisticseasons.bukkit.WorldEffectsTask;
import io.github.tadzi00.realisticseasons.command.RsCommand;
import io.github.tadzi00.realisticseasons.command.SeasonCommand;
import io.github.tadzi00.realisticseasons.command.TempCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class RealisticSeasonsPlugin extends JavaPlugin {

    private volatile Settings settings;
    private SeasonService seasons;
    private RoomService rooms;
    private TemperatureService temperatures;
    private BodyService body;
    private Thermometer thermometer;
    private RealisticSeasonsAPIImpl api;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig(), getLogger());
        seasons = new SeasonService(this);
        rooms = new RoomService(this);
        temperatures = new TemperatureService(this);
        body = new BodyService(this);
        thermometer = new Thermometer(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(body, this);
        pm.registerEvents(new RoomInvalidationListener(this), this);
        pm.registerEvents(new CropListener(this), this);
        pm.registerEvents(new MobSpawnListener(this), this);

        command("temp", new TempCommand(this, false));
        command("room", new TempCommand(this, true));
        command("season", new SeasonCommand(this));
        command("rs", new RsCommand(this));

        api = new RealisticSeasonsAPIImpl(this);
        getServer().getServicesManager().register(RealisticSeasonsAPI.class, api, this, ServicePriority.Normal);

        startTasks();
        getLogger().info("RealisticSeasons enabled (difficulty " + settings.preset + ").");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (body != null) body.shutdown();
        if (thermometer != null) thermometer.unregisterRecipe();
        getServer().getServicesManager().unregisterAll(this);
        if (rooms != null) rooms.clear();
    }

    private <T extends CommandExecutor & TabCompleter> void command(String name, T handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command '" + name + "' missing from plugin.yml");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }

    private void startTasks() {
        var sched = getServer().getScheduler();
        tasks.add(sched.runTaskTimer(this, body::tick, 20L, settings.bodyIntervalTicks));
        tasks.add(sched.runTaskTimer(this, new WorldEffectsTask(this), 40L, settings.effectsIntervalTicks));
        tasks.add(sched.runTaskTimer(this, rooms::cleanup, 1200L, 1200L));
        if (settings.thermometerEnabled) thermometer.registerRecipe();
        else thermometer.unregisterRecipe();
    }

    private void stopTasks() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    /** /rs reload: re-reads config.yml, rebuilds settings, clears room cache and restarts tasks. */
    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        stopTasks();
        rooms.clear();
        startTasks();
    }

    public Settings settings() {
        return settings;
    }

    public SeasonService seasons() {
        return seasons;
    }

    public RoomService rooms() {
        return rooms;
    }

    public TemperatureService temperatures() {
        return temperatures;
    }

    public BodyService body() {
        return body;
    }

    public Thermometer thermometer() {
        return thermometer;
    }

    public RealisticSeasonsAPI api() {
        return api;
    }
}
