package me.f0reach.vshop.locale;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationDisplayTest {

    private static final Map<String, String> JA = Map.of(
            "duration.d", "日",
            "duration.h", "時間",
            "duration.m", "分",
            "duration.s", "秒",
            "duration.sep", "",
            "duration.now", "まもなく");

    private static final Map<String, String> EN = Map.of(
            "duration.d", "d",
            "duration.h", "h",
            "duration.m", "m",
            "duration.s", "s",
            "duration.sep", " ",
            "duration.now", "now");

    private static DurationDisplay display(Map<String, String> table) {
        Function<String, String> resolver = k -> table.getOrDefault(k, k);
        return new DurationDisplay(resolver);
    }

    @Test
    void ja_hoursAndMinutes() {
        assertEquals("2時間30分",
                display(JA).format(Duration.ofHours(2).plusMinutes(30)));
    }

    @Test
    void en_hoursAndMinutes() {
        assertEquals("2h 30m",
                display(EN).format(Duration.ofHours(2).plusMinutes(30)));
    }

    @Test
    void picksTwoMostSignificantUnits() {
        // 1d 2h 30m — seconds and minutes get dropped after two units.
        Duration d = Duration.ofDays(1).plusHours(2).plusMinutes(30).plusSeconds(15);
        assertEquals("1d 2h", display(EN).format(d));
    }

    @Test
    void skipsZeroLeadingUnits() {
        // Under an hour: shows minutes only (seconds still allowed as fill).
        assertEquals("45m 12s", display(EN).format(Duration.ofSeconds(45 * 60 + 12)));
    }

    @Test
    void secondsOnly_whenUnderMinute() {
        assertEquals("15s", display(EN).format(Duration.ofSeconds(15)));
        assertEquals("15秒", display(JA).format(Duration.ofSeconds(15)));
    }

    @Test
    void secondsSuppressed_whenDaysOrHoursPresent() {
        // 1h 0m 15s → seconds ignored; we already emitted the "h" unit and
        // the second slot skipped past minutes (zero).
        assertEquals("1h", display(EN).format(Duration.ofSeconds(3600 + 15)));
    }

    @Test
    void zeroOrNegative_returnsNowLabel() {
        assertEquals("now", display(EN).format(Duration.ZERO));
        assertEquals("now", display(EN).format(Duration.ofSeconds(-5)));
        assertEquals("まもなく", display(JA).format(Duration.ZERO));
    }

    @Test
    void nullDuration_returnsEmpty() {
        assertEquals("", display(EN).format(null));
    }

    @Test
    void missingKey_fallsBackToBuiltInSuffix() {
        // Empty resolver → everything falls back.
        DurationDisplay bare = new DurationDisplay(k -> null);
        assertEquals("2h 30m", bare.format(Duration.ofHours(2).plusMinutes(30)));
        assertEquals("now", bare.format(Duration.ZERO));
    }
}
