package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.core.BodyTemperatureModel;
import io.github.tadzi00.realisticseasons.core.DifficultyPreset;
import io.github.tadzi00.realisticseasons.core.HeatModel;
import io.github.tadzi00.realisticseasons.core.HeatSource;
import io.github.tadzi00.realisticseasons.core.Multipliers;
import io.github.tadzi00.realisticseasons.core.OutdoorTemperatureModel;
import io.github.tadzi00.realisticseasons.core.Season;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Immutable snapshot of config.yml with the difficulty preset and multipliers already applied.
 * Rebuilt on /rs reload; services always read {@code plugin.settings()} so they pick up the new instance.
 */
public final class Settings {

    public record HeatSourceDef(HeatSource source, boolean requiresLit) {
    }

    public record MobSeason(double defaultMultiplier, Map<EntityType, Double> entities) {
    }

    public record Replacement(EntityType to, double chance) {
    }

    // difficulty
    public final DifficultyPreset preset;
    public final Multipliers mult;

    // worlds
    public final Set<String> enabledWorlds;
    public final Set<String> disabledWorlds;

    // seasons
    public final boolean seasonsEnabled;
    public final int seasonLengthDays;
    public final double transitionDays;
    public final Map<Season, Double> baseTemps;

    // temperature
    public final OutdoorTemperatureModel outdoorModel;
    public final double netherTemp;
    public final double endTemp;
    public final double comfortMin;
    public final double comfortMax;

    // rooms
    public final boolean roomsEnabled;
    public final int roomMaxVolume;
    public final int roomMaxLargeLeaks;
    public final long roomRescanMillis;
    public final long roomExpireMillis;
    public final boolean seedAtEquilibrium;
    public final double defaultInsulation;
    public final double doorInsulation;
    public final Map<Material, Double> insulation;
    public final Set<Material> smallLeakMaterials;

    // heat
    public final boolean heatEnabled;
    public final HeatModel heatModel;
    public final int localScanRadius;
    public final Map<Material, HeatSourceDef> heatSources;

    // body
    public final boolean bodyEnabled;
    public final int bodyIntervalTicks;
    public final int feltRefreshTicks;
    public final BodyTemperatureModel bodyModel;
    public final double wetLingerSeconds;
    public final double freezeDamagePerSecond;
    public final double heatDamagePerSecond;
    public final Map<String, Double> clothing;
    public final boolean freezeOverlay;
    public final boolean bodyMessages;

    // crops
    public final boolean cropsEnabled;
    public final double cropMinTemperature;
    public final boolean cropAffectSaplings;
    public final boolean cropAffectSpread;
    public final boolean cropAffectBonemeal;
    public final int cropScansPerTick;

    // world effects
    public final boolean effectsEnabled;
    public final int effectsIntervalTicks;
    public final int effectsColumnsPerRun;
    public final int effectsRadius;
    public final boolean freezeEnabled;
    public final double freezeBelow;
    public final double freezeChance;
    public final boolean snowEnabled;
    public final double snowBelow;
    public final boolean snowRequiresPrecipitation;
    public final int snowMaxLayers;
    public final double snowChance;
    public final boolean thawEnabled;
    public final double thawAbove;
    public final double thawChance;
    public final boolean thawIce;
    public final boolean thawSnow;
    public final double coldBiomeThreshold;

    // mobs
    public final boolean mobsEnabled;
    public final int mobsMaxExtra;
    public final Map<Season, MobSeason> mobSeasons;
    public final boolean replacementsEnabled;
    public final double replacementsBelow;
    public final Map<EntityType, Replacement> replacements;

    public final boolean thermometerEnabled;

    public final int maxRoomScansPerTick;
    public final long outsideCacheMillis;

    public final String hypothermiaDeathMessage;
    public final String heatStrokeDeathMessage;

    public Settings(FileConfiguration c, Logger log) {
        preset = DifficultyPreset.parse(c.getString("difficulty", "normal"), DifficultyPreset.NORMAL);
        Multipliers user = new Multipliers(
                c.getDouble("multipliers.cooling-rate", 1),
                c.getDouble("multipliers.freeze-damage", 1),
                c.getDouble("multipliers.water-freeze-speed", 1),
                c.getDouble("multipliers.snow-accumulation", 1),
                c.getDouble("multipliers.crop-cutoff-offset", 0),
                c.getDouble("multipliers.winter-temperature-offset", 0),
                c.getDouble("multipliers.season-length", 1),
                c.getDouble("multipliers.room-max-volume", 1),
                c.getDouble("multipliers.heat-source-power", 1),
                c.getDouble("multipliers.winter-hostile-spawn", 1));
        mult = preset.multipliers().combine(user);

        enabledWorlds = new HashSet<>(c.getStringList("worlds.enabled"));
        disabledWorlds = new HashSet<>(c.getStringList("worlds.disabled"));

        seasonsEnabled = c.getBoolean("seasons.enabled", true);
        seasonLengthDays = Math.max(1, (int) Math.round(c.getInt("seasons.length-days", 14) * mult.seasonLength()));
        transitionDays = c.getBoolean("seasons.smooth-transition", true) ? Math.max(0, c.getDouble("seasons.transition-days", 4)) : 0;
        Map<Season, Double> bt = new EnumMap<>(Season.class);
        Map<Season, Double> defaults = io.github.tadzi00.realisticseasons.core.SeasonCalculator.defaultBaseTemps();
        for (Season s : Season.values()) {
            bt.put(s, c.getDouble("seasons.base-temperature." + s.name(), defaults.get(s)));
        }
        bt.put(Season.WINTER, bt.get(Season.WINTER) + mult.winterTemperatureOffset());
        baseTemps = Collections.unmodifiableMap(bt);

        OutdoorTemperatureModel.Params d = OutdoorTemperatureModel.Params.defaults();
        outdoorModel = new OutdoorTemperatureModel(new OutdoorTemperatureModel.Params(
                c.getDouble("temperature.biome-reference", d.referenceBiomeTemp()),
                c.getDouble("temperature.biome-scale", d.biomeScale()),
                c.getDouble("temperature.diurnal-amplitude", d.diurnalAmplitude()),
                c.getDouble("temperature.peak-tick", d.peakTick()),
                c.getDouble("temperature.lapse-rate-per-block", d.lapseRatePerBlock()),
                c.getInt("temperature.lapse-start-y", d.lapseStartY()),
                c.getDouble("temperature.rain-offset", d.rainOffset()),
                c.getDouble("temperature.thunder-offset", d.thunderOffset()),
                c.getDouble("temperature.cave-temperature", d.caveTemperature()),
                c.getDouble("temperature.cave-blend-start-depth", d.caveBlendStartDepth()),
                c.getDouble("temperature.cave-blend-full-depth", d.caveBlendFullDepth()),
                c.getInt("temperature.geothermal-start-y", d.geothermalStartY()),
                c.getDouble("temperature.geothermal-per-block", d.geothermalPerBlock()),
                c.getDouble("temperature.noise-amplitude", d.noiseAmplitude()),
                c.getDouble("temperature.noise-cell-size", d.noiseCellSize())));
        netherTemp = c.getDouble("temperature.nether", 40);
        endTemp = c.getDouble("temperature.the-end", 5);
        comfortMin = c.getDouble("temperature.comfort-min", 12);
        comfortMax = c.getDouble("temperature.comfort-max", 28);

        roomsEnabled = c.getBoolean("rooms.enabled", true);
        roomMaxVolume = Math.max(8, (int) Math.round(c.getInt("rooms.max-volume", 1000) * mult.roomMaxVolume()));
        roomMaxLargeLeaks = Math.max(0, c.getInt("rooms.max-large-leaks", 8));
        roomRescanMillis = Math.max(1, c.getLong("rooms.rescan-seconds", 30)) * 1000L;
        roomExpireMillis = Math.max(10, c.getLong("rooms.cache-expire-seconds", 300)) * 1000L;
        seedAtEquilibrium = c.getBoolean("rooms.seed-at-equilibrium", true);
        defaultInsulation = c.getDouble("rooms.default-insulation", 1.0);
        doorInsulation = c.getDouble("rooms.door-insulation", 0.8);
        Map<Material, Double> ins = new EnumMap<>(Material.class);
        ConfigurationSection insSec = c.getConfigurationSection("rooms.insulation");
        if (insSec != null) {
            for (String pattern : insSec.getKeys(false)) {
                double v = insSec.getDouble(pattern);
                List<Material> matched = MaterialPatterns.match(pattern);
                if (matched.isEmpty()) log.warning("rooms.insulation: pattern '" + pattern + "' matches no block");
                // exact names override wildcard matches regardless of order
                for (Material m : matched) {
                    if (!pattern.contains("*") || !ins.containsKey(m)) ins.put(m, v);
                }
            }
        }
        insulation = Collections.unmodifiableMap(ins);
        Set<Material> leaks = new HashSet<>();
        for (String pattern : c.getStringList("rooms.small-leak-materials")) {
            leaks.addAll(MaterialPatterns.match(pattern));
        }
        smallLeakMaterials = Collections.unmodifiableSet(leaks);

        heatEnabled = c.getBoolean("heat.enabled", true);
        HeatModel.Params hd = HeatModel.Params.defaults();
        heatModel = new HeatModel(new HeatModel.Params(
                c.getDouble("heat.loss-coefficient", hd.lossCoefficient()),
                c.getDouble("heat.volume-exponent", hd.volumeExponent()),
                c.getDouble("heat.small-leak-weight", hd.smallLeakWeight()),
                c.getDouble("heat.large-leak-weight", hd.largeLeakWeight()),
                c.getDouble("heat.local-gain", hd.localGain()),
                c.getDouble("heat.min-local-weight", hd.minLocalWeight()),
                c.getDouble("heat.max-local-contribution", hd.maxLocalContribution()),
                c.getDouble("heat.max-room-rise", hd.maxRoomRise()),
                c.getDouble("heat.thermal-time-constant-seconds", hd.thermalTimeConstantSeconds()),
                mult.heatSourcePower()));
        localScanRadius = Math.max(0, Math.min(8, c.getInt("heat.local-scan-radius", 5)));
        Map<Material, HeatSourceDef> hs = new EnumMap<>(Material.class);
        ConfigurationSection hsSec = c.getConfigurationSection("heat.sources");
        if (hsSec != null && heatEnabled) {
            for (String pattern : hsSec.getKeys(false)) {
                ConfigurationSection s = hsSec.getConfigurationSection(pattern);
                if (s == null) continue;
                HeatSourceDef def = new HeatSourceDef(new HeatSource(s.getDouble("power", 0), s.getDouble("radius", 0)),
                        s.getBoolean("requires-lit", false));
                List<Material> matched = MaterialPatterns.match(pattern);
                if (matched.isEmpty()) log.warning("heat.sources: pattern '" + pattern + "' matches no block");
                for (Material m : matched) hs.put(m, def);
            }
        }
        heatSources = Collections.unmodifiableMap(hs);

        bodyEnabled = c.getBoolean("body.enabled", true);
        bodyIntervalTicks = Math.max(1, c.getInt("body.update-interval-ticks", 20));
        feltRefreshTicks = Math.max(1, c.getInt("body.felt-refresh-ticks", 40));
        BodyTemperatureModel.Params bd = BodyTemperatureModel.Params.defaults();
        bodyModel = new BodyTemperatureModel(new BodyTemperatureModel.Params(
                c.getDouble("body.normal", bd.normal()),
                comfortMin,
                comfortMax,
                c.getDouble("body.cooling-rate", bd.coolingRate()),
                c.getDouble("body.heating-rate", bd.heatingRate()),
                c.getDouble("body.recovery-rate", bd.recoveryRate()),
                c.getDouble("body.metabolic-heat", bd.metabolicHeat()),
                c.getDouble("body.sweating", bd.sweating()),
                c.getDouble("body.sprint-warm", bd.sprintWarm()),
                c.getDouble("body.wet-felt-penalty", bd.wetFeltPenalty()),
                c.getDouble("body.wet-multiplier", bd.wetMultiplier()),
                c.getDouble("body.clothing-heat-factor", bd.clothingHeatFactor()),
                mult.coolingRate(),
                c.getBoolean("body.heat-stroke.enabled", false),
                c.getDouble("body.thresholds.cold", bd.coldThreshold()),
                c.getDouble("body.thresholds.shivering", bd.shiveringThreshold()),
                c.getDouble("body.thresholds.hypothermia", bd.hypothermiaThreshold()),
                c.getDouble("body.thresholds.hot", bd.hotThreshold()),
                c.getDouble("body.thresholds.heat-stroke", bd.heatStrokeThreshold()),
                c.getDouble("body.min-core", bd.minCore()),
                c.getDouble("body.max-core", bd.maxCore())));
        wetLingerSeconds = Math.max(0, c.getDouble("body.wet-linger-seconds", 30));
        freezeDamagePerSecond = c.getDouble("body.freeze-damage-per-second", 0.5) * mult.freezeDamage();
        heatDamagePerSecond = c.getDouble("body.heat-stroke.damage-per-second", 0.5) * mult.freezeDamage();
        Map<String, Double> cl = new HashMap<>();
        ConfigurationSection clSec = c.getConfigurationSection("body.clothing");
        if (clSec != null) {
            for (String k : clSec.getKeys(false)) cl.put(k.toUpperCase(Locale.ROOT), clSec.getDouble(k));
        }
        clothing = Collections.unmodifiableMap(cl);
        freezeOverlay = c.getBoolean("body.freeze-overlay", true);
        bodyMessages = c.getBoolean("body.messages", true);

        cropsEnabled = c.getBoolean("crops.enabled", true);
        cropMinTemperature = c.getDouble("crops.min-temperature", 5) + mult.cropCutoffOffset();
        cropAffectSaplings = c.getBoolean("crops.affect-saplings", true);
        cropAffectSpread = c.getBoolean("crops.affect-spread", true);
        cropAffectBonemeal = c.getBoolean("crops.affect-bonemeal", true);
        cropScansPerTick = Math.max(0, c.getInt("crops.max-scans-per-tick", 2));

        effectsEnabled = c.getBoolean("world-effects.enabled", true);
        effectsIntervalTicks = Math.max(1, c.getInt("world-effects.interval-ticks", 10));
        effectsColumnsPerRun = Math.max(0, c.getInt("world-effects.columns-per-run", 48));
        effectsRadius = Math.max(8, c.getInt("world-effects.radius", 64));
        freezeEnabled = c.getBoolean("world-effects.water-freezing.enabled", true);
        freezeBelow = c.getDouble("world-effects.water-freezing.below", 0);
        freezeChance = c.getDouble("world-effects.water-freezing.chance", 0.15) * mult.waterFreezeSpeed();
        snowEnabled = c.getBoolean("world-effects.snow.enabled", true);
        snowBelow = c.getDouble("world-effects.snow.below", 0);
        snowRequiresPrecipitation = c.getBoolean("world-effects.snow.requires-precipitation", true);
        snowMaxLayers = Math.max(1, Math.min(8, c.getInt("world-effects.snow.max-layers", 3)));
        snowChance = c.getDouble("world-effects.snow.chance", 0.2) * mult.snowAccumulation();
        thawEnabled = c.getBoolean("world-effects.thaw.enabled", true);
        thawAbove = c.getDouble("world-effects.thaw.above", 2);
        thawChance = c.getDouble("world-effects.thaw.chance", 0.1);
        thawIce = c.getBoolean("world-effects.thaw.melt-ice", true);
        thawSnow = c.getBoolean("world-effects.thaw.melt-snow", true);
        coldBiomeThreshold = c.getDouble("world-effects.thaw.cold-biome-threshold", 0.15);

        mobsEnabled = c.getBoolean("mobs.enabled", true);
        mobsMaxExtra = Math.max(0, c.getInt("mobs.max-extra-per-spawn", 2));
        Map<Season, MobSeason> ms = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            String base = "mobs.seasons." + s.name();
            Map<EntityType, Double> ents = new EnumMap<>(EntityType.class);
            ConfigurationSection es = c.getConfigurationSection(base + ".entities");
            if (es != null) {
                for (String k : es.getKeys(false)) {
                    EntityType t = entityType(k);
                    if (t == null) log.warning("mobs: unknown entity type '" + k + "'");
                    else ents.put(t, es.getDouble(k));
                }
            }
            ms.put(s, new MobSeason(c.getDouble(base + ".default", 1.0), Collections.unmodifiableMap(ents)));
        }
        mobSeasons = Collections.unmodifiableMap(ms);
        replacementsEnabled = c.getBoolean("mobs.winter-replacements.enabled", true);
        replacementsBelow = c.getDouble("mobs.winter-replacements.below", 0);
        Map<EntityType, Replacement> rep = new EnumMap<>(EntityType.class);
        ConfigurationSection rs = c.getConfigurationSection("mobs.winter-replacements.rules");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                EntityType from = entityType(k);
                EntityType to = entityType(rs.getString(k + ".to", ""));
                if (from == null || to == null) {
                    log.warning("mobs.winter-replacements: invalid rule '" + k + "'");
                    continue;
                }
                rep.put(from, new Replacement(to, rs.getDouble(k + ".chance", 0)));
            }
        }
        replacements = Collections.unmodifiableMap(rep);

        thermometerEnabled = c.getBoolean("thermometer.enabled", true);
        maxRoomScansPerTick = Math.max(1, c.getInt("performance.max-room-scans-per-tick", 4));
        outsideCacheMillis = Math.max(0, c.getLong("performance.outside-cache-seconds", 5)) * 1000L;

        hypothermiaDeathMessage = c.getString("messages.hypothermia-death", "%player% froze to death");
        heatStrokeDeathMessage = c.getString("messages.heat-stroke-death", "%player% died of heat stroke");
    }

    public boolean isWorldEnabled(World w) {
        if (w == null) return false;
        if (disabledWorlds.contains(w.getName())) return false;
        return enabledWorlds.isEmpty() || enabledWorlds.contains(w.getName());
    }

    public double clothingFor(Material m) {
        if (m == null || m.isAir()) return 0;
        String name = m.name();
        Double exact = clothing.get(name);
        if (exact != null) return exact;
        int us = name.indexOf('_');
        if (us > 0) {
            Double prefix = clothing.get(name.substring(0, us));
            if (prefix != null) return prefix;
        }
        return 0;
    }

    private static EntityType entityType(String name) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Material wildcard matching: "EXACT", "*_SUFFIX", "PREFIX_*", "*CONTAINS*". Blocks only. */
    static final class MaterialPatterns {
        private MaterialPatterns() {
        }

        @SuppressWarnings("deprecation")
        static List<Material> match(String rawPattern) {
            String p = rawPattern.trim().toUpperCase(Locale.ROOT);
            List<Material> out = new ArrayList<>();
            boolean starts = p.startsWith("*"), ends = p.endsWith("*") && p.length() > 1;
            String core = p.replace("*", "");
            for (Material m : Material.values()) {
                if (m.isLegacy() || !m.isBlock()) continue;
                String n = m.name();
                boolean ok;
                if (starts && ends) ok = n.contains(core);
                else if (starts) ok = n.endsWith(core);
                else if (ends) ok = n.startsWith(core);
                else ok = n.equals(core);
                if (ok) out.add(m);
            }
            return out;
        }
    }
}
