package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.BodyStage;
import io.github.tadzi00.realisticseasons.core.BodyTemperatureModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player body temperature. Runs every {@code body.update-interval-ticks}; felt temperature (which may
 * need a room scan) is refreshed only every {@code body.felt-refresh-ticks} and cached in between.
 * Body temperature is persisted in the player's PersistentDataContainer (survives relog), reset on respawn.
 */
public final class BodyService implements Listener {

    static final class PlayerState {
        double core;
        double felt = Double.NaN;
        TemperatureService.Felt lastFelt;
        int feltTick = Integer.MIN_VALUE;
        double wetSeconds;
        BodyStage stage = BodyStage.COMFORTABLE;
        int lastBodyDamageTick = Integer.MIN_VALUE;
        BodyStage damageStage;
        boolean overlayLocked;
        int nextRoughTick; // next tick a rough "~5°C" hint may show (0 = right away)
    }

    private final RealisticSeasonsPlugin plugin;
    private final NamespacedKey bodyKey;
    private final Map<UUID, PlayerState> states = new HashMap<>();

    public BodyService(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
        this.bodyKey = new NamespacedKey(plugin, "body_temp");
    }

    private double normal() {
        return plugin.settings().bodyModel.params().normal();
    }

    PlayerState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), id -> {
            PlayerState st = new PlayerState();
            Double saved = p.getPersistentDataContainer().get(bodyKey, PersistentDataType.DOUBLE);
            st.core = saved != null && !saved.isNaN() ? saved : normal();
            return st;
        });
    }

    public double bodyTemperature(Player p) {
        return state(p).core;
    }

    public BodyStage stage(Player p) {
        return state(p).stage;
    }

    /** Last felt temperature computed for this player (refreshing it if never computed). */
    public TemperatureService.Felt lastFelt(Player p) {
        PlayerState st = state(p);
        if (st.lastFelt == null) refreshFelt(p, st, RoomService.Budget.FORCE);
        return st.lastFelt;
    }

    private void refreshFelt(Player p, PlayerState st, RoomService.Budget budget) {
        TemperatureService.Felt f = plugin.temperatures().felt(p.getLocation(), budget);
        if (f.lookup() != null && f.lookup().state() == RoomService.State.UNKNOWN && st.lastFelt != null) {
            return; // scan budget exhausted this tick: keep the previous value, retry next refresh
        }
        st.lastFelt = f;
        st.felt = f.felt();
        st.feltTick = Bukkit.getCurrentTick();
    }

    /** Called by the scheduler every update interval. */
    public void tick() {
        Settings s = plugin.settings();
        int now = Bukkit.getCurrentTick();
        double dt = s.bodyIntervalTicks / 20.0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerState st = state(p);
            boolean enabledHere = s.isWorldEnabled(p.getWorld());
            if (now - st.feltTick >= s.feltRefreshTicks || st.lastFelt == null) {
                refreshFelt(p, st, RoomService.Budget.PLAYER);
            }
            if (plugin.thermometer() != null && !plugin.thermometer().showIfHolding(p, st)) {
                plugin.thermometer().maybeShowRough(p, st, now);
            }

            if (!s.bodyEnabled || !enabledHere || p.getGameMode() == GameMode.CREATIVE
                    || p.getGameMode() == GameMode.SPECTATOR || p.isDead() || Double.isNaN(st.felt)) {
                // recover toward normal, clear effects we caused
                st.core = recoverOnly(st.core, dt);
                applyStage(p, st, s.bodyModel.stage(st.core), false);
                continue;
            }

            boolean wetNow = p.isInWaterOrRain();
            if (wetNow) st.wetSeconds = s.wetLingerSeconds;
            else st.wetSeconds = Math.max(0, st.wetSeconds - dt * (st.felt > s.comfortMax - 5 ? 2 : 1));
            boolean wet = wetNow || st.wetSeconds > 0;

            BodyTemperatureModel model = s.bodyModel;
            st.core = model.step(new BodyTemperatureModel.Input(st.core, st.felt, clothing(p, s), wet, p.isSprinting(), dt));
            p.getPersistentDataContainer().set(bodyKey, PersistentDataType.DOUBLE, st.core);
            applyStage(p, st, model.stage(st.core), true);
            damage(p, st, s, dt);
        }
    }

    private double recoverOnly(double core, double dt) {
        double n = normal();
        double step = plugin.settings().bodyModel.params().recoveryRate() * dt / 60.0;
        if (core < n) return Math.min(n, core + step);
        if (core > n) return Math.max(n, core - step);
        return core;
    }

    double clothing(Player p, Settings s) {
        double sum = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            if (it != null) sum += s.clothingFor(it.getType());
        }
        return sum;
    }

    private void applyStage(Player p, PlayerState st, BodyStage stage, boolean effects) {
        Settings s = plugin.settings();
        BodyStage prev = st.stage;
        st.stage = stage;
        if (prev != stage && s.bodyMessages && effects) message(p, prev, stage);

        if (effects) {
            switch (stage) {
                case SHIVERING -> {
                    effect(p, PotionEffectType.SLOWNESS, 0);
                    effect(p, PotionEffectType.MINING_FATIGUE, 0);
                }
                case HYPOTHERMIA -> {
                    effect(p, PotionEffectType.SLOWNESS, 1);
                    effect(p, PotionEffectType.MINING_FATIGUE, 1);
                    effect(p, PotionEffectType.WEAKNESS, 0);
                }
                case HOT -> effect(p, PotionEffectType.HUNGER, 0);
                case HEAT_STROKE -> {
                    effect(p, PotionEffectType.SLOWNESS, 0);
                    effect(p, PotionEffectType.NAUSEA, 0);
                    effect(p, PotionEffectType.HUNGER, 1);
                }
                default -> {
                }
            }
        }

        // Freeze overlay (vanilla powder-snow vignette). Ticks are locked below the vanilla damage threshold
        // (getMaxFreezeTicks) so vanilla never adds its own freeze damage on top of ours.
        boolean cold = effects && s.freezeOverlay && (stage == BodyStage.SHIVERING || stage == BodyStage.HYPOTHERMIA);
        if (cold) {
            int max = p.getMaxFreezeTicks();
            int ticks = stage == BodyStage.HYPOTHERMIA ? max - 1 : max / 2;
            p.lockFreezeTicks(true);
            p.setFreezeTicks(ticks);
            st.overlayLocked = true;
        } else if (st.overlayLocked) {
            p.lockFreezeTicks(false);
            p.setFreezeTicks(0);
            st.overlayLocked = false;
        }
    }

    private static void effect(Player p, PotionEffectType type, int amp) {
        p.addPotionEffect(new PotionEffect(type, 60, amp, true, false, true));
    }

    private void message(Player p, BodyStage prev, BodyStage now) {
        boolean worse = Math.abs(now.ordinal() - BodyStage.COMFORTABLE.ordinal())
                > Math.abs(prev.ordinal() - BodyStage.COMFORTABLE.ordinal());
        Component msg = switch (now) {
            case COLD -> worse ? Component.text("You feel cold. Find shelter or warmer clothes.", NamedTextColor.AQUA) : null;
            case SHIVERING -> worse ? Component.text("You are shivering! Get warm soon.", NamedTextColor.BLUE) : null;
            case HYPOTHERMIA -> Component.text("Hypothermia! You are freezing to death!", NamedTextColor.DARK_BLUE);
            case HOT -> worse ? Component.text("You are overheating. Find shade or water.", NamedTextColor.GOLD) : null;
            case HEAT_STROKE -> Component.text("Heat stroke! Cool down immediately!", NamedTextColor.RED);
            case COMFORTABLE -> Component.text("Your body temperature is back to normal.", NamedTextColor.GREEN);
        };
        if (msg != null) p.sendMessage(msg);
    }

    private void damage(Player p, PlayerState st, Settings s, double dt) {
        double amount = 0;
        BodyStage kind = null;
        if (st.stage == BodyStage.HYPOTHERMIA) {
            // damage grows the colder the core gets
            double below = s.bodyModel.params().hypothermiaThreshold() - st.core;
            amount = s.freezeDamagePerSecond * dt * (1 + Math.max(0, below) * 0.25);
            kind = BodyStage.HYPOTHERMIA;
        } else if (st.stage == BodyStage.HEAT_STROKE) {
            amount = s.heatDamagePerSecond * dt;
            kind = BodyStage.HEAT_STROKE;
        }
        if (amount <= 0) return;
        st.lastBodyDamageTick = Bukkit.getCurrentTick();
        st.damageStage = kind;
        DamageType type = kind == BodyStage.HYPOTHERMIA ? DamageType.FREEZE : DamageType.HOT_FLOOR;
        p.damage(amount, DamageSource.builder(type).build());
    }

    public void reset(Player p) {
        PlayerState st = state(p);
        st.core = normal();
        st.wetSeconds = 0;
        st.stage = BodyStage.COMFORTABLE;
        st.lastFelt = null;
        st.felt = Double.NaN;
        if (st.overlayLocked) {
            p.lockFreezeTicks(false);
            p.setFreezeTicks(0);
            st.overlayLocked = false;
        }
        p.getPersistentDataContainer().set(bodyKey, PersistentDataType.DOUBLE, st.core);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerState st = states.get(p.getUniqueId());
            if (st == null) continue;
            p.getPersistentDataContainer().set(bodyKey, PersistentDataType.DOUBLE, st.core);
            if (st.overlayLocked) {
                p.lockFreezeTicks(false);
                p.setFreezeTicks(0);
            }
        }
        states.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        state(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PlayerState st = states.remove(e.getPlayer().getUniqueId());
        if (st != null) {
            e.getPlayer().getPersistentDataContainer().set(bodyKey, PersistentDataType.DOUBLE, st.core);
            if (st.overlayLocked) e.getPlayer().lockFreezeTicks(false);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        reset(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        PlayerState st = states.get(p.getUniqueId());
        if (st == null || st.damageStage == null) return;
        if (Bukkit.getCurrentTick() - st.lastBodyDamageTick > 2 * plugin.settings().bodyIntervalTicks) return;
        DamageType expected = st.damageStage == BodyStage.HYPOTHERMIA ? DamageType.FREEZE : DamageType.HOT_FLOOR;
        if (e.getDamageSource().getDamageType() != expected) return;
        String template = st.damageStage == BodyStage.HYPOTHERMIA
                ? plugin.settings().hypothermiaDeathMessage : plugin.settings().heatStrokeDeathMessage;
        e.deathMessage(Component.text(template.replace("%player%", p.getName())));
        st.damageStage = null;
    }
}
