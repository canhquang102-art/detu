package vn.detu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Util {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Util() {
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> colorAll(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(color(l));
        return out;
    }

    public static String fmtTime(long ms) {
        long s = Math.max(0L, (ms + 999L) / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static String fmt(long v) {
        double d = v;
        if (v >= 1_000_000_000_000L) return trim(d / 1e12) + "T";
        if (v >= 1_000_000_000L) return trim(d / 1e9) + "B";
        if (v >= 1_000_000L) return trim(d / 1e6) + "M";
        if (v >= 1_000L) return trim(d / 1e3) + "K";
        return String.valueOf(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }
}
