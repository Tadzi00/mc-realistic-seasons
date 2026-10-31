package io.github.tadzi00.realisticseasons.bukkit;

import io.github.tadzi00.realisticseasons.RealisticSeasonsPlugin;
import io.github.tadzi00.realisticseasons.core.RawContract;
import io.github.tadzi00.realisticseasons.core.RoughReading;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Craftable thermometer: a CLOCK with a custom name and a PDC marker. While held (either hand) the action
 * bar shows felt temperature, body temperature and room status. Recipe (vertical):
 * GLASS / REDSTONE / COPPER_INGOT.
 */
public final class Thermometer {

    private final RealisticSeasonsPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;

    public Thermometer(RealisticSeasonsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "thermometer");
        this.recipeKey = new NamespacedKey(plugin, "thermometer");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Thermometer", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Hold to read the temperature.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isThermometer(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public void registerRecipe() {
        unregisterRecipe();
        ShapedRecipe r = new ShapedRecipe(recipeKey, create());
        r.shape("G", "R", "C");
        r.setIngredient('G', Material.GLASS);
        r.setIngredient('R', Material.REDSTONE);
        r.setIngredient('C', Material.COPPER_INGOT);
        Bukkit.addRecipe(r);
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    /** Shows the precise reading if the player holds a thermometer. Returns true if something was shown. */
    boolean showIfHolding(Player p, BodyService.PlayerState st) {
        if (!plugin.settings().thermometerEnabled) return false;
        if (!isThermometer(p.getInventory().getItemInMainHand()) && !isThermometer(p.getInventory().getItemInOffHand())) return false;
        TemperatureService.Felt f = st.lastFelt;
        if (f == null) return false;
        NamedTextColor color = f.felt() < 0 ? NamedTextColor.AQUA
                : f.felt() < plugin.settings().comfortMin ? NamedTextColor.BLUE
                : f.felt() > plugin.settings().comfortMax ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        String where = !f.inside() ? "outside" : f.scan().sealed() ? "sealed room" : "leaky room";
        p.sendActionBar(Component.text(RawContract.oneDecimal(f.felt()) + "°C", color)
                .append(Component.text("  |  body " + RawContract.oneDecimal(st.core) + "°C  |  " + where
                        + "  |  outside " + RawContract.oneDecimal(f.outside()) + "°C", NamedTextColor.GRAY)));
        return true;
    }

    /**
     * Without a thermometer: now and then (random interval) show an inaccurate guess like "~5°C", but only
     * while the felt temperature is outside the comfort range.
     */
    void maybeShowRough(Player p, BodyService.PlayerState st, int now) {
        Settings s = plugin.settings();
        TemperatureService.Felt f = st.lastFelt;
        if (!s.roughEnabled || f == null) return;
        boolean cold = f.felt() < s.comfortMin;
        boolean hot = f.felt() > s.comfortMax;
        if (!cold && !hot) {
            // comfortable: allow a quick first hint after stepping out into the cold/heat
            st.nextRoughTick = Math.min(st.nextRoughTick, now + 100);
            return;
        }
        if (now < st.nextRoughTick) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int guess = RoughReading.estimate(f.felt(), s.roughStep, s.roughMaxError, rnd.nextDouble());
        p.sendActionBar(Component.text(RoughReading.format(guess), cold ? NamedTextColor.AQUA : NamedTextColor.GOLD));
        st.nextRoughTick = now + RoughReading.nextDelayTicks(s.roughMinSeconds, s.roughMaxSeconds, rnd.nextDouble());
    }
}
