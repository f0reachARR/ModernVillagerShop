package me.f0reach.vshop.command.sub;

import me.f0reach.vshop.model.TradeSide;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Parsed form of the {@code /vshop history} positional + flag arguments. The
 * input is the raw greedy-string supplied by Brigadier; everything after the
 * first whitespace-delimited token is interpreted as either a positional
 * shopId / page or a {@code --flag value} pair.
 *
 * Supported flags (per spec §4 / §5):
 *   --shop <id|prefix>
 *   --side <sell|buy>
 *   --from <YYYY-MM-DD[THH:mm[:ss]]>
 *   --to   <YYYY-MM-DD[THH:mm[:ss]]>
 *   --player <name>
 */
public record HistoryArgs(
        String shopIdPrefix,   // null if not specified
        Integer page,          // null if not specified
        TradeSide side,        // null if not specified
        Instant from,
        Instant to,
        String playerName      // null if not specified
) {

    public static Result parse(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return Result.ok(new HistoryArgs(null, null, null, null, null, null));

        String[] tokens = s.split("\\s+");
        String shopIdPrefix = null;
        Integer page = null;
        TradeSide side = null;
        Instant from = null;
        Instant to = null;
        String playerName = null;

        int i = 0;
        // Per spec §4: `[shopId] [page]`. The first positional is always the
        // shopId prefix (even if it happens to be all digits — UUID prefixes
        // can be), the second positional is the page. Page-only callers should
        // use `--page <n>`.
        int positionalIdx = 0;
        while (i < tokens.length && !tokens[i].startsWith("--")) {
            String tok = tokens[i];
            if (positionalIdx == 0) {
                shopIdPrefix = tok;
            } else if (positionalIdx == 1) {
                if (!isInteger(tok)) return Result.error("page must be an integer: " + tok);
                page = Integer.parseInt(tok);
            } else {
                return Result.error("Unexpected positional argument: " + tok);
            }
            positionalIdx++;
            i++;
        }

        while (i < tokens.length) {
            String flag = tokens[i++];
            if (i >= tokens.length) {
                return Result.error("Missing value for " + flag);
            }
            String value = tokens[i++];
            switch (flag) {
                case "--shop" -> shopIdPrefix = value;
                case "--page" -> {
                    if (!isInteger(value)) return Result.error("--page must be an integer");
                    page = Integer.parseInt(value);
                }
                case "--side" -> {
                    try { side = TradeSide.valueOf(value.toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ex) { return Result.error("--side must be sell or buy"); }
                }
                case "--from" -> {
                    Instant parsed = parseDate(value, false);
                    if (parsed == null) return Result.error("--from has invalid date: " + value);
                    from = parsed;
                }
                case "--to" -> {
                    Instant parsed = parseDate(value, true);
                    if (parsed == null) return Result.error("--to has invalid date: " + value);
                    to = parsed;
                }
                case "--player" -> playerName = value;
                default -> { return Result.error("Unknown flag: " + flag); }
            }
        }
        return Result.ok(new HistoryArgs(shopIdPrefix, page, side, from, to, playerName));
    }

    public int pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = s.charAt(0) == '-' ? 1 : 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return s.length() > (s.charAt(0) == '-' ? 1 : 0);
    }

    /**
     * Accepts YYYY-MM-DD (treated as start-of-day; end-of-day if endExclusive)
     * or YYYY-MM-DDTHH:mm[:ss] in the server's default zone.
     */
    private static Instant parseDate(String s, boolean endExclusive) {
        ZoneId zone = ZoneId.systemDefault();
        try {
            if (s.length() == 10) {
                LocalDate d = LocalDate.parse(s);
                LocalDateTime ldt = endExclusive ? d.plusDays(1).atStartOfDay() : d.atStartOfDay();
                return ldt.atZone(zone).toInstant();
            }
            LocalDateTime ldt = LocalDateTime.parse(s);
            return ldt.atZone(zone).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record Result(HistoryArgs value, String error) {
        public static Result ok(HistoryArgs v) { return new Result(v, null); }
        public static Result error(String message) { return new Result(null, message); }
        public boolean isError() { return error != null; }
    }

    /** Convenience: convert the shopId prefix to a UUID using the registry-resolved Shop. */
    public static UUID toUuidOrNull(String prefix) {
        if (prefix == null) return null;
        try { return UUID.fromString(prefix); } catch (IllegalArgumentException ex) { return null; }
    }
}
