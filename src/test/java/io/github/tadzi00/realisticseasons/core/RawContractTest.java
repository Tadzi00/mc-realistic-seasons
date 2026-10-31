package io.github.tadzi00.realisticseasons.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the STABLE RS|v1 contract. If this test needs changing, you are breaking another project. */
class RawContractTest {

    @Test
    void exactRoomLine() {
        RawContract.Report r = new RawContract.Report(true, true, 75, 0, 0, 18.25, -5.2, "winter", true);
        assertEquals("RS|v1|room|{\"inside\":true,\"sealed\":true,\"volume\":75,\"leaks\":{\"small\":0,\"large\":0},"
                + "\"tempC\":18.3,\"outsideC\":-5.2,\"season\":\"WINTER\",\"comfortable\":true}", RawContract.line("room", r));
    }

    @Test
    void outsideHasZeroVolume() {
        RawContract.Report r = new RawContract.Report(false, false, 123, 0, 0, 3.04, 3.04, "SPRING", false);
        assertEquals("RS|v1|temp|{\"inside\":false,\"sealed\":false,\"volume\":0,\"leaks\":{\"small\":0,\"large\":0},"
                + "\"tempC\":3.0,\"outsideC\":3.0,\"season\":\"SPRING\",\"comfortable\":false}", RawContract.line("temp", r));
    }

    @Test
    void leakyRoom() {
        String json = RawContract.json(new RawContract.Report(true, false, 40, 2, 1, 9.0, -1.0, "AUTUMN", false));
        assertTrue(json.contains("\"leaks\":{\"small\":2,\"large\":1}"));
        assertTrue(json.contains("\"sealed\":false"));
    }

    @Test
    void numberFormatting() {
        assertEquals("0.0", RawContract.oneDecimal(-0.04));
        assertEquals("0.0", RawContract.oneDecimal(-0.0));
        assertEquals("-0.1", RawContract.oneDecimal(-0.06));
        assertEquals("12.0", RawContract.oneDecimal(12));
        assertEquals("1234.6", RawContract.oneDecimal(1234.56));
        assertEquals("0.0", RawContract.oneDecimal(Double.NaN));
    }

    @Test
    void singleLine() {
        String line = RawContract.line("temp", new RawContract.Report(true, true, 1, 0, 0, 1, 1, "SUMMER", false));
        assertFalse(line.contains("\n"));
        assertTrue(line.startsWith("RS|v1|temp|{"));
        assertTrue(line.endsWith("}"));
    }

    @Test
    void errorLineIsEscaped() {
        assertEquals("RS|v1|error|{\"error\":\"bad \\\"x\\\"\"}", RawContract.errorLine("bad \"x\""));
    }
}
