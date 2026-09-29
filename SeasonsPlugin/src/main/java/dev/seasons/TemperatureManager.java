package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class TemperatureManager {

    private final SeasonsPlugin plugin;
    private final Map<UUID, Double> playerTemperatures = new HashMap<>();

    public TemperatureManager(SeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    public double getTemperature(Player player) {
        return playerTemperatures.getOrDefault(player.getUniqueId(), 37.0);
    }

    public void setTemperature(Player player, double temp) {
        playerTemperatures.put(player.getUniqueId(), Math.max(0.0, Math.min(50.0, temp)));
    }

    public Breakdown getBreakdown(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        
        double seasonMod = 0.0;
        if (world != null) {
            Season season = plugin.getSeasonManager().getSeason(world);
            switch (season) {
                case SUMMER: seasonMod = 5.0; break;
                case WINTER: seasonMod = -8.0; break;
                case SPRING: seasonMod = 1.0; break;
                case AUTUMN: seasonMod = -2.0; break;
            }
        }

        double biomeMod = loc.getBlock().getTemperature() * 10.0 - 5.0;
        double armorMod = getArmorTemperatureOffset(player);
        double heatMod = isNearHeatSource(loc) ? 5.0 : 0.0;

        return new Breakdown(seasonMod, biomeMod, armorMod, heatMod);
    }

    public void updateTemperatures() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("seasons.bypass.temperature")) continue;

            double targetTemp = calculateTargetTemperature(player);
            double currentTemp = getTemperature(player);

            double diff = targetTemp - currentTemp;
            double change = Math.signum(diff) * Math.min(Math.abs(diff), 0.2);
            double newTemp = currentTemp + change;

            setTemperature(player, newTemp);
            applyTemperatureEffects(player, newTemp);
        }
    }

    private double calculateTargetTemperature(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return 37.0;

        double baseTemp = 37.0;

        Season currentSeason = plugin.getSeasonManager().getSeason(world);
        switch (currentSeason) {
            case SUMMER: baseTemp += 5.0; break;
            case WINTER: baseTemp -= 8.0; break;
            case SPRING: baseTemp += 1.0; break;
            case AUTUMN: baseTemp -= 2.0; break;
        }

        double biomeTemp = loc.getBlock().getTemperature();
        baseTemp += (biomeTemp - 0.5) * 10.0;

        long time = world.getTime();
        if (time > 13000 && time < 23000) {
            baseTemp -= 3.0;
        }

        int y = loc.getBlockY();
        if (y < 60) {
            baseTemp += (60 - y) * 0.1;
        } else if (y > 100) {
            baseTemp -= (y - 100) * 0.1;
        }

        if (isNearHeatSource(loc)) {
            baseTemp += 5.0;
        }

        baseTemp += getArmorTemperatureOffset(player);

        return baseTemp;
    }

    private boolean isNearHeatSource(Location loc) {
        int radius = 3;
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
            Material type = item.getType();
            String name = type.name();

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

    private void applyTemperatureEffects(Player player, double temp) {
        if (temp < 30.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
            if (temp < 25.0) {
                player.damage(1.0);
                player.sendMessage(ChatColor.RED + "You are freezing!");
            }
        } else if (temp > 42.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
            if (temp > 45.0) {
                player.damage(1.0);
                player.sendMessage(ChatColor.RED + "You are overheating!");
            }
        }

        applyMovementModifier(player, temp);
    }

    private void applyMovementModifier(Player player, double temp) {
        try {
            Attribute speedAttr = getSpeedAttribute();
            if (speedAttr == null || player.getAttribute(speedAttr) == null) return;

            player.getAttribute(speedAttr).getModifiers().stream()
                .filter(m -> m.getName().equals("temp_speed_mod"))
                .forEach(m -> player.getAttribute(speedAttr).removeModifier(m));

            if (temp < 28.0) {
                AttributeModifier mod = new AttributeModifier(
                    UUID.nameUUIDFromBytes("temp_speed_mod".getBytes()),
                    "temp_speed_mod",
                    -0.02,
                    AttributeModifier.Operation.ADD_NUMBER
                );
                player.getAttribute(speedAttr).addModifier(mod);
            }
        } catch (Exception ignored) {
        }
    }

    private Attribute getSpeedAttribute() {
        try {
            return Attribute.valueOf("GENERIC_MOVEMENT_SPEED");
        } catch (IllegalArgumentException e) {
            try {
                return Attribute.valueOf("MOVEMENT_SPEED");
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    public static class Breakdown {
        public final double season;
        public final double biome;
        public final double armor;
        public final double heat;

        public Breakdown(double season, double biome, double armor, double heat) {
            this.season = season;
            this.biome = biome;
            this.armor = armor;
            this.heat = heat;
        }
    }
}
