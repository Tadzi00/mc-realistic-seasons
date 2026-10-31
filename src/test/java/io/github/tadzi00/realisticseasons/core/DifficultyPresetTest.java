package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyPresetTest {

    @Test
    void parse() {
        assertEquals(DifficultyPreset.BRUTAL, DifficultyPreset.parse("Brutal", DifficultyPreset.NORMAL));
        assertEquals(DifficultyPreset.NORMAL, DifficultyPreset.parse("nightmare", DifficultyPreset.NORMAL));
        assertEquals(DifficultyPreset.NORMAL, DifficultyPreset.parse(null, DifficultyPreset.NORMAL));
    }

    @Test
    void presetsAreOrderedByHarshness() {
        DifficultyPreset[] order = {DifficultyPreset.EASY, DifficultyPreset.NORMAL, DifficultyPreset.HARD, DifficultyPreset.BRUTAL};
        for (int i = 1; i < order.length; i++) {
            Multipliers a = order[i - 1].multipliers(), b = order[i].multipliers();
            assertTrue(b.coolingRate() > a.coolingRate());
            assertTrue(b.freezeDamage() > a.freezeDamage());
            assertTrue(b.winterTemperatureOffset() < a.winterTemperatureOffset());
            assertTrue(b.cropCutoffOffset() > a.cropCutoffOffset());
            assertTrue(b.heatSourcePower() < a.heatSourcePower());
        }
    }

    @Test
    void customIsIdentityAndCombineMixesMultiplyAndAdd() {
        assertEquals(Multipliers.IDENTITY, DifficultyPreset.CUSTOM.multipliers());
        Multipliers user = new Multipliers(2, 1, 1, 1, 1.5, -2, 1, 1, 1, 1);
        Multipliers c = DifficultyPreset.HARD.multipliers().combine(user);
        assertEquals(3.0, c.coolingRate(), 1e-9);
        assertEquals(3.5, c.cropCutoffOffset(), 1e-9);
        assertEquals(-5.0, c.winterTemperatureOffset(), 1e-9);
    }
}
