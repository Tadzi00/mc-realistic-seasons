package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyTemperatureModelTest {

    private final BodyTemperatureModel m = new BodyTemperatureModel(BodyTemperatureModel.Params.defaults());

    private double simulate(double felt, double clothing, boolean wet, double seconds) {
        double core = 37;
        for (int i = 0; i < seconds; i++) {
            core = m.step(new BodyTemperatureModel.Input(core, felt, clothing, wet, false, 1));
        }
        return core;
    }

    @Test
    void comfortableStaysNormal() {
        assertEquals(37.0, simulate(20, 0, false, 3600), 1e-9);
    }

    @Test
    void nakedInFreezingColdReachesHypothermiaInMinutes() {
        double after5 = simulate(-10, 0, false, 300);
        double after10 = simulate(-10, 0, false, 600);
        assertTrue(after5 < 35.5, "after 5 min: " + after5);
        assertEquals(BodyStage.HYPOTHERMIA, m.stage(after10), "after 10 min: " + after10);
    }

    @Test
    void leatherClothingSlowsCoolingALot() {
        double naked = simulate(-10, 0, false, 600);
        double leather = simulate(-10, 3.0, false, 600);
        assertTrue(leather > naked + 2, "leather " + leather + " vs naked " + naked);
        assertEquals(BodyStage.COMFORTABLE, m.stage(leather));
    }

    @Test
    void wetIsColder() {
        assertTrue(simulate(0, 0, true, 300) < simulate(0, 0, false, 300));
        // wet in mild weather is still uncomfortable: 14°C - 4 penalty = 10 < comfortMin
        assertTrue(simulate(14, 0, true, 600) < 37.0);
    }

    @Test
    void sprintingWarms() {
        double still = 36;
        double ran = m.step(new BodyTemperatureModel.Input(36, -5, 0, false, true, 60));
        double walked = m.step(new BodyTemperatureModel.Input(still, -5, 0, false, false, 60));
        assertTrue(ran > walked);
    }

    @Test
    void recoveryNeverOvershoots() {
        double core = 34;
        for (int i = 0; i < 3600; i++) core = m.step(new BodyTemperatureModel.Input(core, 22, 0, false, true, 1));
        assertEquals(37.0, core, 1e-9);
        double hot = 38.5;
        for (int i = 0; i < 3600; i++) hot = m.step(new BodyTemperatureModel.Input(hot, 22, 0, false, false, 1));
        assertEquals(37.0, hot, 1e-9);
    }

    @Test
    void stages() {
        assertEquals(BodyStage.COMFORTABLE, m.stage(37));
        assertEquals(BodyStage.COLD, m.stage(35.9));
        assertEquals(BodyStage.SHIVERING, m.stage(34.5));
        assertEquals(BodyStage.HYPOTHERMIA, m.stage(32.9));
        assertEquals(BodyStage.COMFORTABLE, m.stage(40), "heat stroke disabled by default");
    }

    @Test
    void heatStrokeWhenEnabled() {
        var d = BodyTemperatureModel.Params.defaults();
        BodyTemperatureModel hot = new BodyTemperatureModel(new BodyTemperatureModel.Params(d.normal(), d.comfortMin(),
                d.comfortMax(), d.coolingRate(), d.heatingRate(), d.recoveryRate(), d.metabolicHeat(), d.sweating(),
                d.sprintWarm(), d.wetFeltPenalty(), d.wetMultiplier(), d.clothingHeatFactor(), d.coolingMultiplier(),
                true, d.coldThreshold(), d.shiveringThreshold(), d.hypothermiaThreshold(), d.hotThreshold(),
                d.heatStrokeThreshold(), d.minCore(), d.maxCore()));
        assertEquals(0, m.drive(45, 0, false), 1e-12, "disabled: no heat drive");
        assertTrue(hot.drive(45, 0, false) > 0);
        double core = 37;
        for (int i = 0; i < 1800; i++) core = hot.step(new BodyTemperatureModel.Input(core, 45, 0, false, false, 1));
        assertEquals(BodyStage.HEAT_STROKE, hot.stage(core));
    }

    @Test
    void coolingMultiplierFromDifficulty() {
        var d = BodyTemperatureModel.Params.defaults();
        BodyTemperatureModel brutal = new BodyTemperatureModel(new BodyTemperatureModel.Params(d.normal(), d.comfortMin(),
                d.comfortMax(), d.coolingRate(), d.heatingRate(), d.recoveryRate(), d.metabolicHeat(), d.sweating(),
                d.sprintWarm(), d.wetFeltPenalty(), d.wetMultiplier(), d.clothingHeatFactor(), 2.2,
                false, d.coldThreshold(), d.shiveringThreshold(), d.hypothermiaThreshold(), d.hotThreshold(),
                d.heatStrokeThreshold(), d.minCore(), d.maxCore()));
        assertEquals(2.2 * m.drive(-10, 0, false), brutal.drive(-10, 0, false), 1e-9);
    }

    @Test
    void coreIsClamped() {
        double core = 37;
        for (int i = 0; i < 100_000; i++) core = m.step(new BodyTemperatureModel.Input(core, -60, 0, true, false, 1));
        assertEquals(BodyTemperatureModel.Params.defaults().minCore(), core, 1e-9);
    }

    @Test
    void comfortRange() {
        assertTrue(m.isComfortable(12));
        assertTrue(m.isComfortable(28));
        assertFalse(m.isComfortable(11.9));
        assertFalse(m.isComfortable(28.1));
    }
}
