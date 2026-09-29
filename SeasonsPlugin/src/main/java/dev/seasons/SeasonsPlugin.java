package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class SeasonsPlugin extends JavaPlugin {

    private SeasonManager seasonManager;
    private TemperatureManager temperatureManager;
    private HudManager hudManager;

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDataFile();

        this.seasonManager = new SeasonManager(this);
        this.temperatureManager = new TemperatureManager(this, seasonManager);
        this.hudManager = new HudManager(this, seasonManager, temperatureManager);

        // Register Command Executors
        if (getCommand("temperature") != null) {
            getCommand("temperature").setExecutor(new TemperatureCommand(this, temperatureManager));
        }
        if (getCommand("season") != null) {
            getCommand("season").setExecutor(new SeasonCommand(this, seasonManager));
        }

        // Core tick loop running every second (20 ticks)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            seasonManager.tick();
            temperatureManager.tickAll();
            hudManager.updateAll();
        }, 20L, 20L);

        getLogger().info("SeasonsPlugin loaded successfully!");
    }

    @Override
    public void onDisable() {
        if (temperatureManager != null) {
            temperatureManager.shutdown();
        }
        if (hudManager != null) {
            hudManager.shutdown();
        }
        saveData();
    }

    private void loadDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public FileConfiguration getData() {
        if (dataConfig == null) {
            loadDataFile();
        }
        return dataConfig;
    }

    public void saveData() {
        if (dataConfig == null || dataFile == null) return;
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public void reloadAll() {
        reloadConfig();
        loadDataFile();
        if (temperatureManager != null) temperatureManager.reload();
        if (hudManager != null) hudManager.reload();
    }

    public SeasonManager getSeasonManager() {
        return seasonManager;
    }

    public TemperatureManager getTemperatureManager() {
        return temperatureManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }
}
