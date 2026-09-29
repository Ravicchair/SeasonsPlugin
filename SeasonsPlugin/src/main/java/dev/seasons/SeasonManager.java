package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The calendar: which season it is, how far into it we are, and base temperature. */
public final class SeasonManager {
    private static final long DAY = 24000L;
    private static final String[] MONTHS = {
            "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December", "January", "February"
    };
    private static final double[] DEFAULT_BASE = {15.0, 30.0, 13.0, -3.0};

    private final SeasonsPlugin plugin;

    private int seasonLength = 28;
    private Season startSeason = Season.SPRING;
    private String calendarWorld = "world";
    private double freezeStart = 0.25;
    private double freezeEnd = 0.75;
    private long dayOffset = 0L;
    private final Map<Season, Double> baseTemps = new EnumMap<>(Season.class);
    private final Set<String> enabledWorlds = new HashSet<>();

    public SeasonManager(SeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        seasonLength = Math.max(1, c.getInt("calendar.season-length-days", 28));
        startSeason = Season.parse(c.getString("calendar.start-season", "SPRING"), Season.SPRING);
        calendarWorld = c.getString("calendar.world", "world");
        freezeStart = Util.clamp(c.getDouble("calendar.freeze-start", 0.25), 0.0, 1.0);
        freezeEnd = Util.clamp(c.getDouble("calendar.freeze-end", 0.75), 0.0, 1.0);
        for (Season s : Season.values()) {
            baseTemps.put(s, c.getDouble("temperature.base." + s.key(), DEFAULT_BASE[s.ordinal()]));
        }
        enabledWorlds.clear();
        enabledWorlds.addAll(c.getStringList("enabled-worlds"));
        dayOffset = plugin.getData().getLong("day-offset", 0L);
    }

    public boolean isActiveWorld(World w) {
        return w != null && w.getEnvironment() == World.Environment.NORMAL && (enabledWorlds.isEmpty() || enabledWorlds.contains(w.getName()));
    }

    private World clockWorld() {
        World w = Bukkit.getWorld(calendarWorld);
        if (w != null) return w;
        for (World other : Bukkit.getWorlds()) {
            if (other.getEnvironment() == World.Environment.NORMAL) return other;
        }
        return null;
    }

    private long seasonTicks() {
        return seasonLength * DAY;
    }

    private long yearTicks() {
        return seasonTicks() * 4L;
    }

    /** Ticks since the start of the spring of year 0, including start season and admin offsets. */
    private long wrapped() {
        World w = clockWorld();
        long full = w == null ? 0L : w.getFullTime();
        long ticks = full + (dayOffset + (long) startSeason.ordinal() * seasonLength) * DAY;
        return Math.floorMod(ticks, yearTicks());
    }

    public int seasonLength() {
        return seasonLength;
    }

    public Season season() {
        return Season.values()[(int) (wrapped() / seasonTicks())];
    }

    // Direct compatibility bridge method for TemperatureManager
    public Season getSeason(World world) {
        return season();
    }

    public Season getSeason() {
        return season();
    }

    /** 0.0 at the start of the season, approaching 1.0 at its end. */
    public double progress() {
        return (wrapped() % seasonTicks()) / (double) seasonTicks();
    }

    public int dayOfSeason() {
        return Math.min(seasonLength, (int) (progress() * seasonLength) + 1);
    }

    public int daysUntilNextSeason() {
        return Math.max(1, (int) Math.ceil((1.0 - progress()) * seasonLength));
    }

    public String monthName() {
        int idx = (int) Math.min(11, (wrapped() / (double) yearTicks()) * 12.0);
        return MONTHS[idx];
    }

    public boolean isFreezePeriod() {
        if (season() != Season.WINTER) return false;
        double p = progress();
        return p >= freezeStart && p <= freezeEnd;
    }

    /** Outside temperature for the current moment, before biome/time/altitude adjustments. */
    public double baseTemperature() {
        Season s = season();
        double p = progress();
        double cur = baseTemps.getOrDefault(s, 15.0);
        if (p < 0.2) {
            double w = 0.5 + 2.5 * p;
            return baseTemps.getOrDefault(s.previous(), 15.0) * (1.0 - w) + cur * w;
        }
        if (p > 0.8) {
            double w = 0.5 + 2.5 * (1.0 - p);
            return baseTemps.getOrDefault(s.next(), 15.0) * (1.0 - w) + cur * w;
        }
        return cur;
    }

    /** Jump to the beginning of the given season. */
    public void setSeason(Season target) {
        World w = clockWorld();
        long rawDays = w == null ? 0L : w.getFullTime() / DAY;
        long base = rawDays + (long) startSeason.ordinal() * seasonLength;
        long wanted = (long) target.ordinal() * seasonLength;
        dayOffset = Math.floorMod(wanted - base, 4L * seasonLength);
        saveOffset();
    }

    public void skipDays(int days) {
        dayOffset = Math.floorMod(dayOffset + days, 4L * seasonLength);
        saveOffset();
    }

    private void saveOffset() {
        plugin.getData().set("day-offset", dayOffset);
        plugin.saveData();
    }

    /** Snow & Ice melting logic when entering Spring/Summer */
    public void meltSnowAroundBlock(Block b) {
        Season s = season();
        if (s == Season.SPRING || s == Season.SUMMER) {
            if (b.getType() == Material.SNOW) {
                b.setType(Material.AIR);
            } else if (b.getType() == Material.ICE) {
                b.setType(Material.WATER);
            }
        }
    }
}
