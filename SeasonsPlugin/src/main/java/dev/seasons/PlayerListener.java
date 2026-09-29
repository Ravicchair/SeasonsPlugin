package dev.seasons;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final TemperatureManager temps;
    private final HudManager hud;
    private final DrinkManager drinks;

    public PlayerListener(TemperatureManager temps, HudManager hud, DrinkManager drinks) {
        this.temps = temps;
        this.hud = hud;
        this.drinks = drinks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        drinks.discover(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        temps.forget(e.getPlayer());
        hud.forget(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        temps.reset(e.getEntity());
    }
}
