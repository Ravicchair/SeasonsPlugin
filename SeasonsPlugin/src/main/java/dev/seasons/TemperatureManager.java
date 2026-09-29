package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class TemperatureManager {

    private final SeasonsPlugin plugin;
    private final SeasonManager seasonManager;
    private final Map<UUID, Double> playerTemperatures = new HashMap<>();
    private final Map<UUID, Double> playerBuffs = new HashMap<>();

    public TemperatureManager(SeasonsPlugin plugin, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
    }

    public double bodyTemp(Player player) {
        return playerTemperatures.getOrDefault(player.getUniqueId(), 37.0);
    }

    public double getTemperature(Player player) {
        return bodyTemp(player);
    }

    public void setTemperature(Player player, double temp) {
        playerTemperatures.put(player.getUniqueId(), Math.max(0.0, Math.min(50.0, temp)));
    }

    public double coldThreshold() {
        return 30.0;
    }

    public double hotThreshold() {
        return 42.0;
    }

    public void addBuff(Player player, double tempChange, int durationSeconds) {
        playerBuffs.put(player.getUniqueId(), tempChange);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerBuffs.remove(player.getUniqueId());
        }, durationSeconds * 20L);
    }

    public void forget(Player player) {
        playerTemperatures.remove(player.getUniqueId());
        playerBuffs.remove(player.getUniqueId());
    }

    public void reset(Player player) {
        playerTemperatures.put(player.getUniqueId(), 37.0);
        playerBuffs.remove(player.getUniqueId());
    }

    public void tickAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("seasons.bypass.temperature")) continue;

            Breakdown bd = breakdown(player);
            double targetTemp = 37.0 + bd.target();
            double currentTemp = bodyTemp(player);

            double diff = targetTemp - currentTemp;
            double change = Math.signum(diff) * Math.min(Math.abs(diff), 0.2);
            double newTemp = currentTemp + change;

            setTemperature(player, newTemp);
            applyEffects(player, newTemp);
        }
    }

    public void shutdown() {
        playerTemperatures.clear();
        playerBuffs.clear();
    }

    public void reload() {
        playerBuffs.clear();
    }

    public Breakdown breakdown(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        double seasonMod = 0.0;
        if (world != null && seasonManager != null) {
            Season season = getSeasonSafely(world);
            if (season != null) {
                switch (season) {
                    case SUMMER: seasonMod = 5.0; break;
                    case WINTER: seasonMod = -8.0; break;
                    case SPRING: seasonMod = 1.0; break;
                    case AUTUMN: seasonMod = -2.0; break;
                }
            }
        }

        double biomeMod = loc.getBlock().getTemperature() * 10.0 - 5.0;
        double armorMod = getArmorTemperatureOffset(player);
        double heatMod = isNearHeatSource(loc) ? 5.0 : 0.0;
        double wetMod = player.isInWaterOrRain() ? -3.0 : 0.0;
        double drinkMod = playerBuffs.getOrDefault(player.getUniqueId(), 0.0);

        return new Breakdown(seasonMod, biomeMod, armorMod, heatMod, wetMod, drinkMod);
    }

    private Season getSeasonSafely(World world) {
        try {
            java.lang.reflect.Method m = seasonManager.getClass().getMethod("getSeason", World.class);
            return (Season) m.invoke(seasonManager, world);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = seasonManager.getClass().getMethod("getCurrentSeason", World.class);
                return (Season) m.invoke(seasonManager, world);
            } catch (Exception e2) {
                try {
                    java.lang.reflect.Method m = seasonManager.getClass().getMethod("getSeason");
                    return (Season) m.invoke(seasonManager);
                } catch (Exception e3) {
                    return Season.SPRING;
                }
            }
        }
    }

    private boolean isNearHeatSource(Location loc) {
        int radius = 3;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material type = loc.getBlock().getRelative(x, y, z).getType();
                    if (type == Material.FIRE || type == Material.SOUL_FIRE ||
                        type == Material.LAVA || type == Material.CAMPFIRE ||
                        type == Material.SOUL_CAMPFIRE || type == Material.MAGMA_BLOCK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private double getArmorTemperatureOffset(Player player) {
        double offset = 0.0;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack item : armor) {
            if (item == null || item.getType() == Material.AIR) continue;
            String name = item.getType().name();

            if (name.contains("LEATHER")) {
                offset += 0.5;
            } else if (name.contains("NETHERITE") || name.contains("DIAMOND")) {
                offset += 0.3;
            } else if (name.contains("CHAINMAIL") || name.contains("IRON")) {
                offset -= 0.2;
            }
        }
        return offset;
    }

    private void applyEffects(Player player, double temp) {
        PotionEffectType slowEffect = PotionEffectType.getByName("SLOWNESS");
        PotionEffectType weakEffect = PotionEffectType.getByName("WEAKNESS");

        if (temp < coldThreshold()) {
            if (slowEffect != null) {
                player.addPotionEffect(new PotionEffect(slowEffect, 40, 0, false, false));
            }
            if (temp < 25.0) {
                player.damage(1.0);
                player.sendMessage(ChatColor.RED + "You are freezing!");
            }
        } else if (temp > hotThreshold()) {
            if (weakEffect != null) {
                player.addPotionEffect(new PotionEffect(weakEffect, 40, 0, false, false));
            }
            if (temp > 45.0) {
                player.damage(1.0);
                player.sendMessage(ChatColor.RED + "You are overheating!");
            }
        }
    }

    public static class Breakdown {
        private final double seasonMod;
        private final double biomeMod;
        private final double armorMod;
        private final double heatMod;
        private final double wetMod;
        private final double drinkMod;

        public Breakdown(double seasonMod, double biomeMod, double armorMod, double heatMod, double wetMod, double drinkMod) {
            this.seasonMod = seasonMod;
            this.biomeMod = biomeMod;
            this.armorMod = armorMod;
            this.heatMod = heatMod;
            this.wetMod = wetMod;
            this.drinkMod = drinkMod;
        }

        public double outside() { return seasonMod + biomeMod; }
        public double armor() { return armorMod; }
        public double sources() { return heatMod; }
        public double wet() { return wetMod; }
        public double drink() { return drinkMod; }
        public double target() { return outside() + armor() + sources() + wet() + drink(); }
    }
}
