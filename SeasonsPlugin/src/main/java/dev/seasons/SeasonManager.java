package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SeasonManager {

    private final SeasonsPlugin plugin;
    private final Map<UUID, SeasonState> worldSeasons = new HashMap<>();

    public SeasonManager(SeasonsPlugin plugin) {
        this.plugin = plugin;
        loadWorldData();
    }

    public static class SeasonState {
        public Season currentSeason;
        public int dayInSeason;
        public int daysPerSeason;

        public SeasonState(Season currentSeason, int dayInSeason, int daysPerSeason) {
            this.currentSeason = currentSeason;
            this.dayInSeason = dayInSeason;
            this.daysPerSeason = daysPerSeason;
        }
    }

    private void loadWorldData() {
        FileConfiguration data = plugin.getData();
        for (World world : Bukkit.getWorlds()) {
            String path = "worlds." + world.getUID();
            String seasonStr = data.getString(path + ".season", "SPRING");
            int day = data.getInt(path + ".day", 1);
            int totalDays = plugin.getConfig().getInt("days-per-season", 7);

            Season season;
            try {
                season = Season.valueOf(seasonStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                season = Season.SPRING;
            }

            worldSeasons.put(world.getUID(), new SeasonState(season, day, totalDays));
        }
    }

    public void saveData() {
        FileConfiguration data = plugin.getData();
        for (Map.Entry<UUID, SeasonState> entry : worldSeasons.entrySet()) {
            String path = "worlds." + entry.getKey();
            SeasonState state = entry.getValue();
            data.set(path + ".season", state.currentSeason.name());
            data.set(path + ".day", state.dayInSeason);
        }
        plugin.saveData();
    }

    public void tick() {
        for (World world : Bukkit.getWorlds()) {
            SeasonState state = worldSeasons.computeIfAbsent(
                world.getUID(),
                k -> new SeasonState(Season.SPRING, 1, plugin.getConfig().getInt("days-per-season", 7))
            );

            long time = world.getTime();
            if (time >= 0 && time < 20) { 
                advanceDay(world, state);
            }
        }
    }

    private void advanceDay(World world, SeasonState state) {
        state.dayInSeason++;
        if (state.dayInSeason > state.daysPerSeason) {
            state.dayInSeason = 1;
            state.currentSeason = state.currentSeason.next();
            Bukkit.broadcastMessage("§eThe season in " + world.getName() + " has changed to §b" + state.currentSeason.name() + "§e!");
        }
        saveData();
    }

    // --- Core API ---

    public Season getSeason(World world) {
        if (world == null) return Season.SPRING;
        SeasonState state = worldSeasons.get(world.getUID());
        return state != null ? state.currentSeason : Season.SPRING;
    }

    public int getDay(World world) {
        if (world == null) return 1;
        SeasonState state = worldSeasons.get(world.getUID());
        return state != null ? state.dayInSeason : 1;
    }

    public int getDaysPerSeason(World world) {
        if (world == null) return 7;
        SeasonState state = worldSeasons.get(world.getUID());
        return state != null ? state.daysPerSeason : 7;
    }

    public void setSeason(World world, Season season, int day) {
        if (world == null) return;
        SeasonState state = worldSeasons.computeIfAbsent(
            world.getUID(), 
            k -> new SeasonState(season, day, plugin.getConfig().getInt("days-per-season", 7))
        );
        state.currentSeason = season;
        state.dayInSeason = day;
        saveData();
    }

    // --- Compatibility Methods (Fixes all 32 compilation errors in screenshots) ---

    public boolean isActiveWorld(World world) {
        return world != null && world.getEnvironment() == World.Environment.NORMAL;
    }

    public Season season(World world) {
        return getSeason(world);
    }

    public Season season() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return getSeason(defaultWorld);
    }

    public int dayOfSeason(World world) {
        return getDay(world);
    }

    public int dayOfSeason() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return getDay(defaultWorld);
    }

    public int seasonLength(World world) {
        return getDaysPerSeason(world);
    }

    public int seasonLength() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return getDaysPerSeason(defaultWorld);
    }

    public double progress(World world) {
        int day = getDay(world);
        int total = getDaysPerSeason(world);
        return (double) day / Math.max(1, total);
    }

    public double progress() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return progress(defaultWorld);
    }

    public void setSeason(Season season) {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (defaultWorld != null) {
            setSeason(defaultWorld, season, 1);
        }
    }

    public void skipDays(int days) {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (defaultWorld != null) {
            SeasonState state = worldSeasons.computeIfAbsent(
                defaultWorld.getUID(), 
                k -> new SeasonState(Season.SPRING, 1, plugin.getConfig().getInt("days-per-season", 7))
            );
            for (int i = 0; i < days; i++) {
                advanceDay(defaultWorld, state);
            }
        }
    }

    public String monthName() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        Season s = getSeason(defaultWorld);
        return s.name().substring(0, 1) + s.name().substring(1).toLowerCase();
    }

    public int daysUntilNextSeason() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return getDaysPerSeason(defaultWorld) - getDay(defaultWorld) + 1;
    }

    public double baseTemperature() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        Season s = getSeason(defaultWorld);
        switch (s) {
            case SUMMER: return 32.0;
            case SPRING: return 23.5; // Spring base around 20°C - 27°C
            case AUTUMN: return 14.0;
            case WINTER: return -2.0;
            default: return 20.0;
        }
    }

    public boolean isFreezePeriod() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return getSeason(defaultWorld) == Season.WINTER;
    }
}
