package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeasonsPlugin extends JavaPlugin {

    private SeasonManager seasonManager;
    private TemperatureManager temperatureManager;
    private HudManager hudManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.seasonManager = new SeasonManager(this);
        this.temperatureManager = new TemperatureManager(this, seasonManager);
        this.hudManager = new HudManager(this, seasonManager, temperatureManager);

        // Register Command Executors (Pass 'this' first as SeasonsPlugin)
        if (getCommand("temperature") != null) {
            getCommand("temperature").setExecutor(new TemperatureCommand(this, temperatureManager));
        }

        // Main game tick loop running every second (20 ticks)
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
