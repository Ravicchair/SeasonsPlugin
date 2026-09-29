package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Simulates a body temperature per player and applies the small cold/heat effects. */
public final class TemperatureManager {

    public record Breakdown(double outside, double armor, double sources, double wet, double drink, double target) {}

    private record Buff(double amount, long expiresAt) {}

    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;
    private final NamespacedKey slowKey;

    private final Map<UUID, Double> body = new HashMap<>();
    private final Map<UUID, Breakdown> breakdowns = new HashMap<>();
    private final Map<UUID, Buff> buffs = new HashMap<>();

    private double responsiveness = 0.08;
    private double nightDrop = 6.0;
    private double altitudeStart = 90.0;
    private double altitudePerBlock = 0.05;
    private double rainCooling = 3.0;
    private double wetModifier = -8.0;
    private double armorPerPoint = 0.6;
    private double armorMax = 14.0;
    private double coldThreshold = 0.0;
    private double coldRange = 15.0;
    private double maxSlow = 0.20;
    private double hotThreshold = 34.0;
    private double hotRange = 12.0;
    private double maxHotExhaustion = 0.02;
    private boolean affectCreative = false;
    private int sourceRadius = 3;
    private double maxHeat = 20.0;
    private double maxCold = 12.0;

    private final Map<Material, Double> sourceValues = new EnumMap<>(Material.class);
    private final List<Map.Entry<String, Double>> biomeKeywords = new ArrayList<>();
    private final Map<String, Double> biomeExact = new HashMap<>();
    private final Map<String, Double> biomeCache = new HashMap<>();

    public TemperatureManager(SeasonsPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.slowKey = new NamespacedKey(plugin, "cold_slowdown");
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        String t = "temperature.";
        responsiveness = Util.clamp(c.getDouble(t + "responsiveness", 0.08), 0.005, 1.0);
        nightDrop = c.getDouble(t + "night-drop", 6.0);
        altitudeStart = c.getDouble(t + "altitude-start", 90.0);
        altitudePerBlock = c.getDouble(t + "altitude-per-block", 0.05);
        rainCooling = c.getDouble(t + "rain-cooling", 3.0);
        wetModifier = c.getDouble(t + "water-modifier", -8.0);
        armorPerPoint = c.getDouble(t + "armor-per-point", 0.6);
        armorMax = c.getDouble(t + "armor-max", 14.0);
        coldThreshold = c.getDouble(t + "effects.cold-threshold", 0.0);
        coldRange = Math.max(1.0, c.getDouble(t + "effects.cold-range", 15.0));
        maxSlow = Util.clamp(c.getDouble(t + "effects.max-slow", 0.20), 0.0, 0.8);
        hotThreshold = c.getDouble(t + "effects.hot-threshold", 34.0);
        hotRange = Math.max(1.0, c.getDouble(t + "effects.hot-range", 12.0));
        maxHotExhaustion = Math.max(0.0, c.getDouble(t + "effects.max-hot-exhaustion", 0.02));
        affectCreative = c.getBoolean(t + "effects.affect-creative", false);
        sourceRadius = Math.max(1, Math.min(6, c.getInt(t + "sources.radius", 3)));
        maxHeat = c.getDouble(t + "sources.max-heat", 20.0);
        maxCold = c.getDouble(t + "sources.max-cold", 12.0);

        sourceValues.clear();
        ConfigurationSection src = c.getConfigurationSection(t + "sources.values");
        if (src != null) {
            for (String key : src.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) {
                    plugin.getLogger().warning("Unknown material in temperature.sources.values: " + key);
                    continue;
                }
                sourceValues.put(m, src.getDouble(key));
            }
        }

        biomeKeywords.clear();
        ConfigurationSection kw = c.getConfigurationSection(t + "biome-keywords");
        if (kw != null) {
            for (String key : kw.getKeys(false)) {
                biomeKeywords.add(Map.entry(key.toLowerCase(Locale.ROOT), kw.getDouble(key)));
            }
        }
        biomeExact.clear();
        ConfigurationSection ex = c.getConfigurationSection(t + "biome-exact");
        if (ex != null) {
            for (String key : ex.getKeys(false)) {
                biomeExact.put(key.toLowerCase(Locale.ROOT), ex.getDouble(key));
            }
        }
        biomeCache.clear();
    }

    public double coldThreshold() { return coldThreshold; }
    public double hotThreshold() { return hotThreshold; }

    /** Body temperature in degrees C, or null if seasons are not active for this player. */
    public Double bodyTemp(Player p) {
        return body.get(p.getUniqueId());
    }

    public Breakdown breakdown(Player p) {
        return breakdowns.get(p.getUniqueId());
    }

    /** Temporary warm (positive) or cool (negative) effect, e.g. from a drink. */
    public void addBuff(Player p, double amount, int seconds) {
        UUID id = p.getUniqueId();
        buffs.put(id, new Buff(amount, System.currentTimeMillis() + seconds * 1000L));
        body.computeIfPresent(id, (k, v) -> v + amount * 0.3);
    }

    // ------------------------------------------------------------------ tick

    public void tickAll() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.isDead() || !seasons.isActiveWorld(p.getWorld())) {
                body.remove(id);
                breakdowns.remove(id);
                setSlow(p, 0.0);
                continue;
            }

            double outside = outside(p);
            double armor = armorWarmth(p);
            double src = sources(p.getLocation());
            double wet = p.isInWater() ? wetModifier : 0.0;
            double drink = 0.0;
            Buff b = buffs.get(id);
            if (b != null) {
                if (b.expiresAt() < now) buffs.remove(id);
                else drink = b.amount();
            }

            double target = outside + armor + src + wet + drink;
            Double previous = body.get(id);
            double cur = previous == null ? target : previous;
            double diff = target - cur;
            cur = Math.abs(diff) < 0.05 ? target : cur + diff * responsiveness;

            body.put(id, cur);
            breakdowns.put(id, new Breakdown(outside, armor, src, wet, drink, target));
            applyEffects(p, cur);
        }
    }

    private double outside(Player p) {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        String biome = Util.biomeKey(w, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        double t = seasons.baseTemperature() + biomeModifier(biome);
        t -= Math.max(0.0, loc.getY() - altitudeStart) * altitudePerBlock;

        // Smooth day/night curve: 0 at noon (tick 6000), 1 at midnight (tick 18000).
        double x = (w.getTime() - 6000L) / 24000.0 * 2.0 * Math.PI;
        double night = (1.0 - Math.cos(x)) / 2.0;
        double drop = nightDrop * ((biome.contains("desert") || biome.contains("badlands")) ? 2.0 : 1.0);
        t -= drop * night;

        if (w.hasStorm() && !Util.isDry(biome) && loc.getBlock().getLightFromSky() >= 15) {
            t -= rainCooling;
        }
        return t;
    }

    private double biomeModifier(String biome) {
        return biomeCache.computeIfAbsent(biome, k -> {
            double v = biomeExact.getOrDefault(k, 0.0);
            for (Map.Entry<String, Double> e : biomeKeywords) {
                if (k.contains(e.getKey())) v += e.getValue();
            }
            return v;
        });
    }

    private double armorWarmth(Player p) {
        AttributeInstance armor = p.getAttribute(Attribute.ARMOR);
        double points = armor == null ? 0.0 : armor.getValue();
        return Math.min(armorMax, points * armorPerPoint);
    }

    private double sources(Location loc) {
        if (sourceValues.isEmpty()) return 0.0;
        World w = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        int r = sourceRadius;
        double heat = 0.0;
        double cold = 0.0;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!w.isChunkLoaded((bx + dx) >> 4, (bz + dz) >> 4)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = w.getBlockAt(bx + dx, by + dy, bz + dz);
                    Double value = sourceValues.get(block.getType());
                    if (value == null) continue;
                    if (value > 0 && block.getBlockData() instanceof Lightable lightable && !lightable.isLit()) continue;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r + 0.5) continue;
                    double contribution = value * (1.0 - dist / (r + 1.0));
                    if (contribution > 0) heat += contribution;
                    else cold += contribution;
                }
            }
        }
        return Math.min(maxHeat, heat) + Math.max(-maxCold, cold);
    }

    // --------------------------------------------------------------- effects

    private void applyEffects(Player p, double temp) {
        GameMode gm = p.getGameMode();
        boolean immune = !affectCreative && (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR);
        if (immune) {
            setSlow(p, 0.0);
            return;
        }

        if (temp < coldThreshold) {
            double i = Util.clamp((coldThreshold - temp) / coldRange, 0.0, 1.0);
            setSlow(p, maxSlow * (0.2 + 0.8 * i));
        } else {
            setSlow(p, 0.0);
        }

        if (temp > hotThreshold) {
            double i = Util.clamp((temp - hotThreshold) / hotRange, 0.0, 1.0);
            double extra = maxHotExhaustion * (0.25 + 0.75 * i);
            p.setExhaustion((float) (p.getExhaustion() + extra));
        }
    }

    private void setSlow(Player p, double slow) {
        AttributeInstance inst = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;

        AttributeModifier existing = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (slowKey.equals(m.getKey())) {
                existing = m;
                break;
            }
        }

        if (slow <= 0.0005) {
            if (existing != null) inst.removeModifier(existing);
            return;
        }
        double value = -slow;
        if (existing != null) {
            if (Math.abs(existing.getAmount() - value) < 0.002) return;
            inst.removeModifier(existing);
        }
        inst.addModifier(new AttributeModifier(slowKey, value, AttributeModifier.Operation.ADD_SCALAR));
    }

    // ------------------------------------------------------------- lifecycle

    public void reset(Player p) {
        UUID id = p.getUniqueId();
        body.remove(id);
        breakdowns.remove(id);
        buffs.remove(id);
    }

    public void forget(Player p) {
        reset(p);
        setSlow(p, 0.0);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setSlow(p, 0.0);
        }
        body.clear();
        breakdowns.clear();
        buffs.clear();
    }
}
