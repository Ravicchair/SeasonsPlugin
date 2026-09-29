package dev.seasons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Util {
    private Util() {}

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Biome name without namespace, e.g. "snowy_plains". */
    static String biomeKey(World w, int x, int y, int z) {
        return w.getBiome(x, y, z).getKey().getKey();
    }

    static boolean containsAny(String s, Collection<String> keywords) {
        for (String k : keywords) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** Biomes that are snowy/frozen all year round in vanilla. */
    static boolean isNativelyCold(String biome) {
        return biome.contains("snowy") || biome.contains("frozen") || biome.contains("ice_spikes")
                || biome.equals("grove") || biome.equals("jagged_peaks");
    }

    /** Biomes without rain. */
    static boolean isDry(String biome) {
        return biome.contains("desert") || biome.contains("badlands") || biome.contains("savanna");
    }

    static Map<Season, Double> seasonMap(ConfigurationSection sec, double def) {
        Map<Season, Double> m = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            m.put(s, sec == null ? def : sec.getDouble(s.key(), def));
        }
        return m;
    }

    static Component prefixed(String text, NamedTextColor color) {
        return Component.text()
                .append(Component.text("[Seasons] ", NamedTextColor.GOLD))
                .append(Component.text(text, color))
                .build();
    }

    static List<String> filter(List<String> options, String typed) {
        List<String> out = new ArrayList<>();
        String t = typed.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
        return out;
    }
}
