package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.Season;
import io.github.tadzi00.realisticseasons.core.SeasonCalculator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Per-world season state. The calendar is {@code world.getFullTime() / 24000 + offset}; the offset (and an
 * optional per-world season length override) are stored in the world's PersistentDataContainer, so they are
 * saved with the world (level.dat) and survive restarts. Time skipped by sleeping advances the calendar.
 */
public final class SeasonService {

    private final RealisticSeasonsPlugin plugin;
    private final NamespacedKey offsetKey;
    private final NamespacedKey lengthKey;

    public SeasonService(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
        this.offsetKey = new NamespacedKey(plugin, "season_day_offset");
        this.lengthKey = new NamespacedKey(plugin, "season_length_days");
    }

    public double rawDay(World w) {
        return w.getFullTime() / 24000.0;
    }

    public double offset(World w) {
        Double d = w.getPersistentDataContainer().get(offsetKey, PersistentDataType.DOUBLE);
        return d == null ? 0 : d;
    }

    public double calendarDay(World w) {
        return rawDay(w) + offset(w);
    }

    public int lengthDays(World w) {
        Integer v = w.getPersistentDataContainer().get(lengthKey, PersistentDataType.INTEGER);
        return v != null && v > 0 ? v : plugin.settings().seasonLengthDays;
    }

    public SeasonCalculator.SeasonPosition position(World w) {
        return SeasonCalculator.position(calendarDay(w), lengthDays(w));
    }

    public Season season(World w) {
        return position(w).season();
    }

    /** Season base temperature (with smooth transition) for the world right now. */
    public double baseTemperature(World w) {
        Settings s = plugin.settings();
        if (!s.seasonsEnabled) return s.baseTemps.getOrDefault(Season.SPRING, 10.0);
        return SeasonCalculator.seasonalBaseTemperature(calendarDay(w), lengthDays(w), s.baseTemps, s.transitionDays);
    }

    public void set(World w, Season season, int dayOfSeason) {
        double off = SeasonCalculator.offsetFor(rawDay(w), season, dayOfSeason, lengthDays(w));
        w.getPersistentDataContainer().set(offsetKey, PersistentDataType.DOUBLE, off);
    }

    /** Changes the season length for this world while keeping the current season and day (clamped). */
    public void setLength(World w, int days) {
        SeasonCalculator.SeasonPosition pos = position(w);
        PersistentDataContainer pdc = w.getPersistentDataContainer();
        pdc.set(lengthKey, PersistentDataType.INTEGER, Math.max(1, days));
        set(w, pos.season(), Math.min(pos.dayOfSeason(), Math.max(1, days)));
    }
}
