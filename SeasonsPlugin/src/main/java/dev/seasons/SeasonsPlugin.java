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

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            temperatures.tickAll();
            hud.updateAll();
            worldEffects.tickSecond();
        }, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, worldEffects::tickWeather, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, worldEffects::tickParticles, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, worldEffects::processQueue, 40L, 1L);

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
        seasons.reload();
        temperatures.reload();
        hud.reload();
        worldEffects.reload();
        ecology.reload();
        drinks.reload();
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
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public YamlConfiguration getData() {
        return data;
    }

    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
