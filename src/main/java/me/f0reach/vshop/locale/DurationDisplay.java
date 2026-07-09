package me.f0reach.vshop.locale;

import java.time.Duration;
import java.util.function.Function;

/**
 * Formats a {@link Duration} into a short human-readable string using
 * locale-provided unit suffixes (e.g. {@code "2時間30分"} / {@code "2h 30m"}).
 * Picks the two most significant non-zero units, or falls back to
 * {@code duration.now} when the duration is zero / negative.
 *
 * <p>Unit suffix keys read from {@link MessageManager} (via {@link MessageManager#getRaw}):
 * {@code duration.d}, {@code duration.h}, {@code duration.m}, {@code duration.s},
 * {@code duration.sep}, {@code duration.now}.</p>
 */
public final class DurationDisplay {

    private final Function<String, String> resolver;

    public DurationDisplay(MessageManager messages) {
        this(messages::getRaw);
    }

    /** For tests: pass a plain key-to-raw resolver instead of a live MessageManager. */
    public DurationDisplay(Function<String, String> resolver) {
        this.resolver = resolver;
    }

    public String format(Duration duration) {
        if (duration == null) return "";
        long totalSeconds = duration.getSeconds();
        if (totalSeconds <= 0) return raw("duration.now", "now");

        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        String sep = raw("duration.sep", " ");

        StringBuilder out = new StringBuilder();
        int emitted = 0;
        if (days > 0) {
            append(out, sep, emitted++, days, raw("duration.d", "d"));
        }
        if (emitted < 2 && hours > 0) {
            append(out, sep, emitted++, hours, raw("duration.h", "h"));
        }
        if (emitted < 2 && minutes > 0) {
            append(out, sep, emitted++, minutes, raw("duration.m", "m"));
        }
        if (emitted < 2 && seconds > 0 && days == 0 && hours == 0) {
            append(out, sep, emitted++, seconds, raw("duration.s", "s"));
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String sep, int emitted, long value, String suffix) {
        if (emitted > 0) out.append(sep);
        out.append(value).append(suffix);
    }

    private String raw(String key, String fallback) {
        String v = resolver.apply(key);
        return v == null || v.equals(key) ? fallback : v;
    }
}
