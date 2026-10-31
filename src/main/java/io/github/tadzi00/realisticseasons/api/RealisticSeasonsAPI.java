package io.github.tadzi00.realisticseasons.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * STABLE PUBLIC API (v1). Registered in Bukkit's ServicesManager. Other plugins may call these methods
 * reflectively by name, so names and signatures must never change without a major version bump.
 *
 * <pre>
 * RegisteredServiceProvider&lt;RealisticSeasonsAPI&gt; rsp =
 *     Bukkit.getServicesManager().getRegistration(RealisticSeasonsAPI.class);
 * </pre>
 *
 * All methods must be called from the server main thread.
 */
public interface RealisticSeasonsAPI {

    /** Current season of the world as an uppercase name: SPRING, SUMMER, AUTUMN or WINTER. */
    String getSeason(World world);

    /** Felt temperature in °C at the location (room temperature or outdoor + nearby heat sources). */
    double getTemperature(Location loc);

    /** True if the location is inside a detected room that has no leaks at all. */
    boolean isInsideSealedRoom(Location loc);

    /** True if {@link #getTemperature(Location)} is within the configured comfort range (default 12..28 °C). */
    boolean isComfortable(Location loc);

    /** Same JSON object as printed by {@code /room raw} (without the {@code RS|v1|room|} prefix). */
    String getRoomJson(Location loc);

    /** Player's core body temperature in °C (37.0 = normal). */
    double getBodyTemperature(Player p);
}
