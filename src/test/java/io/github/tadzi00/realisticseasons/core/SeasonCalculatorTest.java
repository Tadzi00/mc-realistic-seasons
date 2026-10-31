package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SeasonCalculatorTest {

    private static final Map<Season, Double> BASE = SeasonCalculator.defaultBaseTemps();

    @Test
    void positionsAcrossTheYear() {
        assertEquals(new SeasonCalculator.SeasonPosition(Season.SPRING, 1, 10, 0.0), SeasonCalculator.position(0, 10));
        assertEquals(Season.SPRING, SeasonCalculator.position(9.99, 10).season());
        assertEquals(10, SeasonCalculator.position(9.99, 10).dayOfSeason());
        assertEquals(Season.SUMMER, SeasonCalculator.position(10, 10).season());
        assertEquals(1, SeasonCalculator.position(10, 10).dayOfSeason());
        assertEquals(Season.AUTUMN, SeasonCalculator.position(25.5, 10).season());
        assertEquals(6, SeasonCalculator.position(25.5, 10).dayOfSeason());
        assertEquals(Season.WINTER, SeasonCalculator.position(39, 10).season());
        assertEquals(Season.SPRING, SeasonCalculator.position(40, 10).season(), "wraps to next year");
        assertEquals(Season.SPRING, SeasonCalculator.position(400 + 3, 10).season());
    }

    @Test
    void negativeDaysWrapBackwards() {
        SeasonCalculator.SeasonPosition p = SeasonCalculator.position(-0.5, 10);
        assertEquals(Season.WINTER, p.season());
        assertEquals(10, p.dayOfSeason());
    }

    @Test
    void lengthIsClampedToAtLeastOne() {
        assertEquals(Season.SUMMER, SeasonCalculator.position(1, 0).season());
    }

    @Test
    void offsetRoundTrip() {
        double raw = 1234.37;
        for (Season s : Season.values()) {
            for (int day : new int[]{1, 5, 14}) {
                double off = SeasonCalculator.offsetFor(raw, s, day, 14);
                SeasonCalculator.SeasonPosition p = SeasonCalculator.position(raw + off, 14);
                assertEquals(s, p.season());
                assertEquals(day, p.dayOfSeason());
                // intra-day fraction preserved -> time of day unaffected
                assertEquals(0.37, (raw + off) - Math.floor(raw + off), 1e-9);
            }
        }
    }

    @Test
    void offsetClampsDay() {
        double off = SeasonCalculator.offsetFor(0, Season.WINTER, 99, 7);
        assertEquals(7, SeasonCalculator.position(off, 7).dayOfSeason());
    }

    @Test
    void stepFunctionWithoutTransition() {
        assertEquals(22.0, SeasonCalculator.seasonalBaseTemperature(10.0, 10, BASE, 0), 1e-9);
        assertEquals(10.0, SeasonCalculator.seasonalBaseTemperature(9.999, 10, BASE, 0), 1e-9);
    }

    @Test
    void transitionIsMidpointAtBoundaryAndPlainInMiddle() {
        double mid = (BASE.get(Season.SPRING) + BASE.get(Season.SUMMER)) / 2;
        assertEquals(mid, SeasonCalculator.seasonalBaseTemperature(10.0, 10, BASE, 4), 1e-9);
        assertEquals(mid, SeasonCalculator.seasonalBaseTemperature(10.0 - 1e-9, 10, BASE, 4), 1e-6);
        assertEquals(22.0, SeasonCalculator.seasonalBaseTemperature(15.0, 10, BASE, 4), 1e-9);
        assertEquals(22.0, SeasonCalculator.seasonalBaseTemperature(12.0, 10, BASE, 4), 1e-9, "edge of window");
    }

    @Test
    void transitionIsContinuousAndMonotonicIntoWinter() {
        double prev = Double.NaN;
        for (double d = 27; d <= 33; d += 0.01) { // autumn -> winter boundary at 30
            double t = SeasonCalculator.seasonalBaseTemperature(d, 10, BASE, 4);
            if (!Double.isNaN(prev)) {
                assertTrue(Math.abs(t - prev) < 0.1, "continuous at day " + d);
                assertTrue(t <= prev + 1e-9, "cooling into winter at day " + d);
            }
            prev = t;
        }
    }

    @Test
    void winterToSpringWrapsAroundYearBoundary() {
        double mid = (BASE.get(Season.WINTER) + BASE.get(Season.SPRING)) / 2;
        assertEquals(mid, SeasonCalculator.seasonalBaseTemperature(40.0, 10, BASE, 4), 1e-9);
        assertEquals(mid, SeasonCalculator.seasonalBaseTemperature(0.0, 10, BASE, 4), 1e-9);
    }

    @Test
    void parseSeason() {
        assertEquals(Season.AUTUMN, Season.parse("fall"));
        assertEquals(Season.WINTER, Season.parse(" Winter "));
        assertNull(Season.parse("monsoon"));
        assertEquals(Season.SPRING, Season.WINTER.next());
        assertEquals(Season.WINTER, Season.SPRING.previous());
    }
}
