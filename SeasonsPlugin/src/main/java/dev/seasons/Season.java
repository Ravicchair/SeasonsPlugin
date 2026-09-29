package dev.seasons;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

public enum Season {
    SPRING("Spring", "\u273F", NamedTextColor.GREEN),
    SUMMER("Summer", "\u2600", NamedTextColor.YELLOW),
    AUTUMN("Autumn", "\u2740", NamedTextColor.GOLD),
    WINTER("Winter", "\u2744", NamedTextColor.AQUA);

    private final String displayName;
    private final String symbol;
    private final NamedTextColor color;

    Season(String displayName, String symbol, NamedTextColor color) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public String symbol() { return symbol; }
    public NamedTextColor color() { return color; }
    public String key() { return name().toLowerCase(Locale.ROOT); }

    public Season next() { return values()[(ordinal() + 1) % values().length]; }
    public Season previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    public static Season parse(String text, Season fallback) {
        if (text == null) return fallback;
        for (Season s : values()) {
            if (s.name().equalsIgnoreCase(text.trim())) return s;
        }
        if (text.trim().equalsIgnoreCase("fall")) return AUTUMN;
        return fallback;
    }
}
