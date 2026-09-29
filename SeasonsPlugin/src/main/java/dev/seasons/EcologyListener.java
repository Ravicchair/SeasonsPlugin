package dev.seasons;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Seasonal animal spawning, tree growth and crop growth. */
public final class EcologyListener implements Listener {

    private static final Set<Material> CROP_EXCLUDED = EnumSet.of(
            Material.KELP, Material.NETHER_WART, Material.CHORUS_FLOWER,
            Material.WEEPING_VINES, Material.TWISTING_VINES);

    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;

    private Map<Season, Double> animals = new EnumMap<>(Season.class);
    private Map<Season, Double> trees = new EnumMap<>(Season.class);
    private Map<Season, Double> crops = new EnumMap<>(Season.class);
    private final Map<Material, Map<Season, Double>> cropOverrides = new EnumMap<>(Material.class);
    private boolean bonemealAffected = false;

    public EcologyListener(SeasonsPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        animals = Util.seasonMap(c.getConfigurationSection("spawns.animal-spawn-chance"), 1.0);
        trees = Util.seasonMap(c.getConfigurationSection("growth.tree-growth-chance"), 1.0);
        crops = Util.seasonMap(c.getConfigurationSection("growth.crop-growth-chance"), 1.0);
        bonemealAffected = c.getBoolean("growth.bonemeal-affected", false);

        cropOverrides.clear();
        ConfigurationSection over = c.getConfigurationSection("growth.crop-overrides");
        if (over != null) {
            for (String key : over.getKeys(false)) {
                Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                ConfigurationSection sec = over.getConfigurationSection(key);
                if (m == null || sec == null) {
                    plugin.getLogger().warning("Bad entry in growth.crop-overrides: " + key);
                    continue;
                }
                Map<Season, Double> map = new EnumMap<>(Season.class);
                for (Season s : Season.values()) {
                    if (sec.contains(s.key())) map.put(s, sec.getDouble(s.key()));
                }
                cropOverrides.put(m, map);
            }
        }
    }

    private static boolean roll(double chance) {
        return chance >= 1.0 || ThreadLocalRandom.current().nextDouble() < chance;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAnimalSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(e.getEntity() instanceof Animals)) return;
        if (!seasons.isActiveWorld(e.getLocation().getWorld())) return;
        if (!roll(animals.getOrDefault(seasons.season(), 1.0))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTreeGrow(StructureGrowEvent e) {
        if (e.isFromBonemeal() && !bonemealAffected) return;
        if (!seasons.isActiveWorld(e.getWorld())) return;
        String species = e.getSpecies().name();
        if (species.contains("MUSHROOM") || species.contains("CHORUS") || species.contains("FUNGUS")) return;
        if (!roll(trees.getOrDefault(seasons.season(), 1.0))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCropGrow(BlockGrowEvent e) {
        Block block = e.getBlock();
        if (!(block.getBlockData() instanceof Ageable)) return;
        Material type = block.getType();
        if (CROP_EXCLUDED.contains(type) || !seasons.isActiveWorld(block.getWorld())) return;

        Season season = seasons.season();
        Map<Season, Double> override = cropOverrides.get(type);
        double chance = (override != null && override.containsKey(season))
                ? override.get(season)
                : crops.getOrDefault(season, 1.0);
        if (!roll(chance)) e.setCancelled(true);
    }
}
