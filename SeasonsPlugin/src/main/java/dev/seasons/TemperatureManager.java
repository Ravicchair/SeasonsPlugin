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
        double clamped = Math.max(15.0, Math.min(50.0, temp));
        playerTemperatures.put(player.getUniqueId(), clamped);
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
            
            // Target body temp is directly driven by outside temp + armor offset + drink buffs
            double targetBodyTemp = bd.target();
            double currentBodyTemp = bodyTemp(player);

            // Gradual body temperature drift (shifts by 0.5°C per second)
            double diff = targetBodyTemp - currentBodyTemp;
            if (Math.abs(diff) > 0.05) {
                double step = Math.copySign(Math.min(Math.abs(diff), 0.5), diff);
                currentBodyTemp += step;
            } else {
                currentBodyTemp = targetBodyTemp;
            }

            setTemperature(player, currentBodyTemp);
            applyEffects(player, currentBodyTemp, bd.outside());
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

        // Base Seasonal Outside Temperature Shift
        double seasonMod = 0.0;
        if (world != null && seasonManager != null) {
            Season season = seasonManager.getSeason(world);
            if (season != null) {
                switch (season) {
                    case SUMMER: seasonMod = 8.0; break;   // Mesa/Desert ~40°C - 48°C
                    case SPRING: seasonMod = -1.0; break;  // Spring ~20°C - 27°C
                    case AUTUMN: seasonMod = -6.0; break;  // Cool
                    case WINTER: seasonMod = -22.0; break; // Sub-zero winter
                }
            }
        }

        // Vanilla Biome base temperature offset
        double rawBiome = loc.getBlock().getTemperature();
        double biomeMod = (rawBiome - 0.5) * 20.0;

        // Altitude factor
        double heightMod = (loc.getY() > 80) ? -((loc.getY() - 80) / 6.0) : 0.0;
        
        // Heat sources & Wetness directly alter OUTSIDE temperature
        double heatMod = isNearHeatSource(loc) ? 25.0 : 0.0;
        double wetMod = player.isInWaterOrRain() ? -8.0 : 0.0;

        // Modifiers that directly affect BODY temperature
        double armorMod = getArmorTemperatureOffset(player);
        double drinkMod = playerBuffs.getOrDefault(player.getUniqueId(), 0.0);

        return new Breakdown(seasonMod + biomeMod + heightMod + heatMod + wetMod, armorMod, drinkMod);
    }

    private boolean isNearHeatSource(Location loc) {
        int radius = 5;
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
                offset += 3.5; // Full Leather = +14°C insulation
            } else if (name.contains("CHAINMAIL") || name.contains("IRON")) {
                offset -= 1.5; // Iron pulls body temp down
            } else if (name.contains("DIAMOND") || name.contains("NETHERITE")) {
                offset += 0.5;
            }
        }
        return offset;
    }

    private void applyEffects(Player player, double bodyTemp, double ambientTemp) {
        // Sub-zero ambient temperature or extreme body hypothermia causes freezing damage
        if (ambientTemp < 0.0 || bodyTemp <= 22.0) {
            player.damage(1.0);
            player.sendMessage(ChatColor.RED + "You are taking freezing damage!");
        }

        // Body temp effects
        if (bodyTemp < coldThreshold()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0, false, false));
        } else if (bodyTemp > hotThreshold()) {
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
        private final double drinkMod;

        public Breakdown(double environmentMod, double armorMod, double drinkMod) {
            this.environmentMod = environmentMod;
            this.armorMod = armorMod;
            this.drinkMod = drinkMod;
        }

        public double outside() { return 20.0 + environmentMod; }
        public double armor() { return armorMod; }
        public double drink() { return drinkMod; }
        
        public double target() { return outside() + armorMod + drinkMod; }
    }
}
