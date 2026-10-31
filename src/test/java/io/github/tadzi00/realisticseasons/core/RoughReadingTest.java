package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoughReadingTest {

    @Test
    void noErrorRoundsToStep() {
        // rand 0.5 → zero error
        assertEquals(5, RoughReading.estimate(6.2, 5, 3, 0.5));
        assertEquals(0, RoughReading.estimate(-2.4, 5, 3, 0.5));
        assertEquals(-10, RoughReading.estimate(-8.0, 5, 3, 0.5));
        assertEquals(30, RoughReading.estimate(31.0, 5, 3, 0.5));
    }

    @Test
    void errorIsBoundedAndAlwaysOnStep() {
        for (double t = -30; t <= 40; t += 0.7) {
            for (double r = 0; r < 1; r += 0.05) {
                int e = RoughReading.estimate(t, 5, 3, r);
                assertEquals(0, e % 5);
                // max error 3 + max rounding 2.5
                assertTrue(Math.abs(e - t) <= 5.5 + 1e-9, "t=" + t + " r=" + r + " e=" + e);
            }
        }
    }

    @Test
    void extremesOfRandomShiftTheReading() {
        assertEquals(0, RoughReading.estimate(4.0, 5, 3, 0.0));   // 4 - 3 = 1 → 0
        assertEquals(5, RoughReading.estimate(4.0, 5, 3, 0.999)); // ~7 → 5
    }

    @Test
    void formatsWithTilde() {
        assertEquals("~5°C", RoughReading.format(5));
        assertEquals("~-10°C", RoughReading.format(-10));
    }

    @Test
    void delayWithinRange() {
        assertEquals(20 * 20, RoughReading.nextDelayTicks(20, 60, 0));
        assertEquals(60 * 20, RoughReading.nextDelayTicks(20, 60, 1));
        assertEquals(10 * 20, RoughReading.nextDelayTicks(30, 10, 0.5)); // min > max: uses max
    }
}
