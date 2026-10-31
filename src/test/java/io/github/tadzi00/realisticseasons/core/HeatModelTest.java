package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeatModelTest {

    private final HeatModel m = new HeatModel(HeatModel.Params.defaults());

    @Test
    void noSourcesNoRise() {
        assertEquals(-10, m.roomEquilibrium(-10, 0, 75, 1.0, 0, 0), 1e-9);
    }

    @Test
    void campfireWarmsSmallSealedStoneRoomComfortably() {
        double t = m.roomEquilibrium(-10, 40, 75, 1.0, 0, 0);
        assertTrue(t > 10 && t < 20, "small stone room with a campfire in -10°C should be ~12°C, was " + t);
    }

    @Test
    void biggerRoomIsColder() {
        assertTrue(m.sealedRise(40, 75, 1.0) > m.sealedRise(40, 500, 1.0));
    }

    @Test
    void insulationScalesRise() {
        assertEquals(2.5 * m.sealedRise(10, 75, 1.0), m.sealedRise(10, 75, 2.5), 1e-9);
    }

    @Test
    void riseIsCapped() {
        assertEquals(HeatModel.Params.defaults().maxRoomRise(), m.sealedRise(10_000, 10, 3.0), 1e-9);
    }

    @Test
    void openDoorIsALittleColderHoleMuchColder() {
        double sealed = m.roomEquilibrium(-10, 40, 75, 1.0, 0, 0);
        double openDoor = m.roomEquilibrium(-10, 40, 75, 1.0, 2, 0);
        double hole = m.roomEquilibrium(-10, 40, 75, 1.0, 0, 2);
        assertTrue(openDoor < sealed && openDoor > sealed - 6, "open door: small drop");
        assertTrue(hole < openDoor - 5, "two-block hole: large drop");
        assertTrue(m.retention(0, 0) == 1.0);
        assertTrue(m.retention(0, 8) < 0.01);
    }

    @Test
    void localHeatFallsOffAndStopsAtRadius() {
        HeatSource torch = new HeatSource(6, 4);
        double near = m.localContribution(torch, 0.5);
        double mid = m.localContribution(torch, 2);
        assertTrue(near > mid && mid > 0);
        assertEquals(0, m.localContribution(torch, 4), 1e-12);
        assertEquals(0, m.localContribution(torch, 10), 1e-12);
    }

    @Test
    void localSumIsCapped() {
        List<PlacedHeatSource> lava = List.of(
                new PlacedHeatSource(0, 0, 0, new HeatSource(60, 6)),
                new PlacedHeatSource(1, 0, 0, new HeatSource(60, 6)),
                new PlacedHeatSource(0, 0, 1, new HeatSource(60, 6)));
        assertEquals(HeatModel.Params.defaults().maxLocalContribution(), m.localSum(lava, 0.5, 0.5, 0.5), 1e-9);
    }

    @Test
    void localWeightHigherInLeakyRooms() {
        assertEquals(1.0, m.localWeight(false, 0, 0));
        assertEquals(HeatModel.Params.defaults().minLocalWeight(), m.localWeight(true, 0, 0), 1e-9);
        assertTrue(m.localWeight(true, 0, 4) > m.localWeight(true, 2, 0));
    }

    @Test
    void thermalInertiaIsExponential() {
        double tau = HeatModel.Params.defaults().thermalTimeConstantSeconds();
        assertEquals(20, m.approach(20, -10, 0), 1e-9);
        double afterTau = m.approach(20, -10, tau);
        assertEquals(-10 + 30 * Math.exp(-1), afterTau, 1e-9);
        // stepping in two halves equals one full step (exact integration, frame-rate independent)
        double half = m.approach(m.approach(20, -10, tau / 2), -10, tau / 2);
        assertEquals(afterTau, half, 1e-9);
        assertEquals(-10, m.approach(20, -10, tau * 50), 1e-6);
    }

    @Test
    void heatPowerMultiplierApplies() {
        var p = HeatModel.Params.defaults();
        HeatModel weak = new HeatModel(new HeatModel.Params(p.lossCoefficient(), p.volumeExponent(), p.smallLeakWeight(),
                p.largeLeakWeight(), p.localGain(), p.minLocalWeight(), p.maxLocalContribution(), p.maxRoomRise(),
                p.thermalTimeConstantSeconds(), 0.5));
        assertEquals(m.sealedRise(10, 75, 1) / 2, weak.sealedRise(10, 75, 1), 1e-9);
    }
}
