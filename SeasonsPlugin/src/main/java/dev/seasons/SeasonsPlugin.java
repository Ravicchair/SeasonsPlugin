package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class SeasonsPlugin extends JavaPlugin {
    private File dataFile;
    private YamlConfiguration data;

    private SeasonManager seasons;
    private TemperatureManager temperatures;
    private HudManager hud;
    private WorldEffectsManager worldEffects;
    private EcologyListener ecology;
    private DrinkManager drinks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        seasons = new SeasonManager(this);
        temperatures = new TemperatureManager(this, seasons);
        hud = new HudManager(this, seasons, temperatures);
        worldEffects = new WorldEffectsManager(this, seasons);
        ecology = new EcologyListener(this, seasons);
        drinks = new DrinkManager(this, seasons, temperatures);
        reloadAll();

        getServer().getPluginManager().registerEvents(worldEffects, this);
        getServer().getPluginManager().registerEvents(ecology, this);
        getServer().getPluginManager().registerEvents(drinks, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(temperatures, hud, drinks), this);

        bind("season", new SeasonCommand(this, seasons, hud));
        bind("temperature", new TemperatureCommand(temperatures, hud));

        // Core tick loop running every 20 ticks (1 second)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (temperatures != null) temperatures.tickAll();
            if (hud != null) hud.updateAll();
            if (worldEffects != null) worldEffects.tickSecond();
        }, 20L, 20L);

        // Weather check every 60 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (worldEffects != null) worldEffects.tickWeather();
        }, 1200L, 1200L);

        // Ambient particles every half second
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (worldEffects != null) worldEffects.tickParticles();
        }, 40L, 10L);

        // World/block queue processing every tick
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (worldEffects != null) worldEffects.processQueue();
        }, 20L, 1L);

        getLogger().info("Seasons enabled. Current season: " + seasons.season().displayName()
                + " (day " + seasons.dayOfSeason() + "/" + seasons.seasonLength() + ")");
    }

    @Override
    public void onDisable() {
        if (hud != null) hud.shutdown();
        if (temperatures != null) temperatures.shutdown();
        if (worldEffects != null) worldEffects.shutdown();
        if (drinks != null) drinks.unregister();
        saveData();
    }

    public void reloadAll() {
        reloadConfig();
        if (seasons != null) seasons.reload();
        if (temperatures != null) temperatures.reload();
        if (hud != null) hud.reload();
        if (worldEffects != null) worldEffects.reload();
        if (ecology != null) ecology.reload();
        if (drinks != null) drinks.reload();
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public YamlConfiguration getData() {
        return data;
    }

    public void saveData() {
        try {
            if (data != null && dataFile != null) {
                data.save(dataFile);
            }
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
