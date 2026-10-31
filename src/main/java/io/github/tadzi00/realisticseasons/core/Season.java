package io.github.tadzi00.realisticseasons.core;

import java.util.Locale;

/** The four seasons, in calendar order. Pure, no Bukkit dependency. */
public enum Season {
    SPRING, SUMMER, AUTUMN, WINTER;

    public Season next() {
        return values()[(ordinal() + 1) % 4];
    }

    public Season previous() {
        return values()[(ordinal() + 3) % 4];
    }

    /** Case-insensitive parse; also accepts "FALL" as an alias for AUTUMN. Returns null if unknown. */
    public static Season parse(String s) {
        if (s == null) return null;
        String u = s.trim().toUpperCase(Locale.ROOT);
        if (u.equals("FALL")) return AUTUMN;
        for (Season season : values()) {
            if (season.name().equals(u)) return season;
        }
        return null;
    }
}
