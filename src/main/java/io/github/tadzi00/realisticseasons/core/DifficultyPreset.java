package io.github.tadzi00.realisticseasons.core;

import java.util.Locale;

/** Built-in difficulty presets. CUSTOM = identity, so only the user's own multipliers apply. */
public enum DifficultyPreset {
    EASY(new Multipliers(0.5, 0.5, 0.5, 0.6, -3, 4, 1, 1.5, 1.3, 0.8)),
    NORMAL(Multipliers.IDENTITY),
    HARD(new Multipliers(1.5, 1.5, 1.5, 1.3, 2, -3, 1, 1.0, 0.85, 1.3)),
    BRUTAL(new Multipliers(2.2, 2.5, 2.0, 1.6, 4, -6, 1, 0.8, 0.7, 1.6)),
    CUSTOM(Multipliers.IDENTITY);

    private final Multipliers multipliers;

    DifficultyPreset(Multipliers m) {
        this.multipliers = m;
    }

    public Multipliers multipliers() {
        return multipliers;
    }

    public static DifficultyPreset parse(String s, DifficultyPreset fallback) {
        if (s == null) return fallback;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
