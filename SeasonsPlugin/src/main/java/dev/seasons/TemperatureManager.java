package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        // Body temperature can safely range between 15°C and 50°C
        playerTemperatures.put(player.getUniqueId(), Math.max(15.0, Math.min(50.0, temp)));
    }

    public double coldThreshold() { return 36.0; }
    public double hotThreshold() { return 39.0; }

    public void addBuff(Player player, double tempChange, int durationSeconds) {
        playerBuffs.put(player.getUniqueId(), tempChange);
        Bukkit.getScheduler().runTaskLater(plugin, () -> playerBuffs.remove(player.getUniqueId()), durationSeconds * 20L);
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
            double targetTemp = bd.target();
            double currentTemp = bodyTemp(player);

            // Shift body temperature toward target by 0.5°C per second
            double diff = targetTemp - currentTemp;
            if (Math.abs(diff) > 0.05) {
                double step = Math.copySign(Math.min(Math.abs(diff), 0.5), diff);
                currentTemp += step;
                setTemperature(player, currentTemp);
            }

            applyEffects(player, currentTemp, bd.outside());
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
            Season season = seasonManager.getSeason(world);
            if (season != null) {
                switch (season) {
                    case SUMMER: seasonMod = 6.0; break;
                    case SPRING: seasonMod = 2.0; break;
                    case AUTUMN: seasonMod = -6.0; break;
                    case WINTER: seasonMod = -18.0; break;
                }
            }
        }

        // Biome base temp calculation:
        // Vanilla biomes: Plains/Forest (~0.7-0.8), Desert/Mesa (2.0)
        double rawBiome = loc.getBlock().getTemperature();
        double biomeMod = (rawBiome - 0.5) * 10.0; // Mesa/Desert becomes ~+15°C

        double heightMod = (loc.getY() > 80) ? -((loc.getY() - 80) / 10.0) : 0.0;
        
        double armorMod = getArmorTemperatureOffset(player);
        double heatMod = isNearHeatSource(loc) ? 15.0 : 0.0;
        double wetMod = player.isInWaterOrRain() ? -6.0 : 0.0;
        double drinkMod = playerBuffs.getOrDefault(player.getUniqueId(), 0.0);

        return new Breakdown(seasonMod + biomeMod + heightMod, armorMod, heatMod, wetMod, drinkMod);
    }

    private boolean isNearHeatSource(Location loc) {
        int radius = 4;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    Material type = b.getType();
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
                offset += 3.0; // +12°C total for full leather set
            } else if (name.contains("CHAINMAIL") || name.contains("IRON")) {
                offset -= 1.5;
            } else if (name.contains("DIAMOND") || name.contains("NETHERITE")) {
                offset += 0.5;
            }
        }
        return offset;
    }

    private void applyEffects(Player player, double bodyTemp, double ambientTemp) {
        // Freezing damage when ambient temperature drops to -15°C or lower
        if (ambientTemp <= -15.0 || bodyTemp <= 20.0) {
            player.damage(1.0);
            player.sendMessage(ChatColor.RED + "You are freezing due to extreme cold!");
        }

        // Cold Threshold (<36°C) -> Slowness
        if (bodyTemp < coldThreshold()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0, false, false));
        } 
        // Hot Threshold (>39°C) -> Hunger / Overheating
        else if (bodyTemp > hotThreshold()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 0, false, false));
            if (bodyTemp > 44.0) {
                player.damage(1.0);
                player.sendMessage(ChatColor.RED + "You are overheating!");
            }
        }
    }

    public static class Breakdown {
        private final double environmentMod;
        private final double armorMod;
        private final double heatMod;
        private final double wetMod;
        private final double drinkMod;

        public Breakdown(double environmentMod, double armorMod, double heatMod, double wetMod, double drinkMod) {
            this.environmentMod = environmentMod;
            this.armorMod = armorMod;
            this.heatMod = heatMod;
            this.wetMod = wetMod;
            this.drinkMod = drinkMod;
        }

        public double outside() { return 25.0 + environmentMod; }
        public double armor() { return armorMod; }
        public double sources() { return heatMod; }
        public double wet() { return wetMod; }
        public double drink() { return drinkMod; }
        
        // Target body temperature based on factors:
        public double target() { return outside() + armorMod + heatMod + wetMod + drinkMod; }
    }
}
