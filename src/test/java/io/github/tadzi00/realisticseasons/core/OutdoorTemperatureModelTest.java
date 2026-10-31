package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutdoorTemperatureModelTest {

    private static final OutdoorTemperatureModel.Params D = OutdoorTemperatureModel.Params.defaults();

    private static OutdoorTemperatureModel noNoise() {
        return new OutdoorTemperatureModel(new OutdoorTemperatureModel.Params(D.referenceBiomeTemp(), D.biomeScale(),
                D.diurnalAmplitude(), D.peakTick(), D.lapseRatePerBlock(), D.lapseStartY(), D.rainOffset(),
                D.thunderOffset(), D.caveTemperature(), D.caveBlendStartDepth(), D.caveBlendFullDepth(),
                D.geothermalStartY(), D.geothermalPerBlock(), 0, D.noiseCellSize()));
    }

    private static OutdoorTemperatureModel.Input in(double base, double biome, long time, int y, int surface, boolean rain) {
        return new OutdoorTemperatureModel.Input(base, biome, time, 0, y, 0, surface, rain, false, 42L, 0);
    }

    private final OutdoorTemperatureModel m = noNoise();

    @Test
    void plainsAtPeakIsBasePlusAmplitude() {
        assertEquals(10 + D.diurnalAmplitude(), m.temperature(in(10, 0.8, 8000, 64, 63, false)), 1e-9);
        assertEquals(10 - D.diurnalAmplitude(), m.temperature(in(10, 0.8, 20000, 64, 63, false)), 1e-9);
    }

    @Test
    void biomeOffset() {
        assertEquals(18.0, m.biomeOffset(2.0), 1e-9);   // desert
        assertEquals(-12.0, m.biomeOffset(0.0), 1e-9);  // snowy plains
        assertEquals(0.0, m.biomeOffset(0.8), 1e-9);    // plains
    }

    @Test
    void colderHighUp() {
        double low = m.temperature(in(10, 0.8, 8000, 64, 63, false));
        double high = m.temperature(in(10, 0.8, 8000, 200, 199, false));
        assertEquals(low - (200 - 64) * D.lapseRatePerBlock(), high, 1e-9);
    }

    @Test
    void rainAndThunderAreColder() {
        double dry = m.temperature(in(10, 0.8, 8000, 64, 63, false));
        double rain = m.temperature(in(10, 0.8, 8000, 64, 63, true));
        double thunder = m.temperature(new OutdoorTemperatureModel.Input(10, 0.8, 8000, 0, 64, 0, 63, true, true, 42L, 0));
        assertEquals(dry + D.rainOffset(), rain, 1e-9);
        assertTrue(thunder < rain);
    }

    @Test
    void deepUndergroundTendsTowardCaveTemperatureInAnySeason() {
        double winterSurface = m.temperature(in(-6, 0.25, 20000, 70, 69, false));
        double winterCave = m.temperature(in(-6, 0.25, 20000, 20, 70, false));
        double summerCave = m.temperature(in(22, 2.0, 8000, 20, 70, false));
        assertTrue(winterSurface < -5);
        assertEquals(D.caveTemperature(), winterCave, 1e-9);
        assertEquals(D.caveTemperature(), summerCave, 1e-9);
    }

    @Test
    void shallowCoverBlendsPartially() {
        double surface = m.temperature(in(-6, 0.8, 8000, 60, 59, false));
        double shallow = m.temperature(in(-6, 0.8, 8000, 50, 65, false)); // 15 blocks deep
        assertTrue(shallow > surface && shallow < D.caveTemperature());
    }

    @Test
    void geothermalBelowStartY() {
        double t = m.temperature(in(-6, 0.8, 8000, -40, 70, false));
        assertEquals(D.caveTemperature() + 40 * D.geothermalPerBlock(), t, 1e-9);
    }

    @Test
    void noiseIsDeterministicBoundedAndSmooth() {
        OutdoorTemperatureModel n = new OutdoorTemperatureModel(D);
        double amp = D.noiseAmplitude();
        double prev = n.noise(0, 0, 3, 99);
        assertEquals(prev, n.noise(0, 0, 3, 99));
        for (int x = 1; x < 500; x++) {
            double v = n.noise(x, 17, 3, 99);
            assertTrue(Math.abs(v) <= amp + 1e-9);
            assertTrue(Math.abs(v - prev) < amp * 0.2, "noise must be smooth between neighbouring blocks");
            prev = v;
        }
        boolean differs = false;
        for (int x = 0; x < 200; x += 16) differs |= n.noise(x, 0, 3, 99) != n.noise(x, 0, 4, 99);
        assertTrue(differs, "noise changes from day to day");
    }
}
