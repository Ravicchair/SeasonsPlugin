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

            // Check Minecraft time advancement (24000 ticks = 1 Minecraft day)
            long time = world.getTime();
            if (time >= 0 && time < 20) { 
                // Increments at dawn
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
}
