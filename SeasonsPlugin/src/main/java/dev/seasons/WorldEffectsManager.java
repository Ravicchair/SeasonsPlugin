package dev.seasons;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything the seasons do to the world itself: weather, snow, ice and thaw,
 * the random tick speed (growth), and ambient particles.
 */
public final class WorldEffectsManager implements Listener {

    private record ChunkRef(UUID world, int x, int z) {}

    private record WeatherProfile(double startChance, double endChance, int minMinutes, int maxMinutes,
                                  double thunderChance) {}

    private record State(Season season, double progress, boolean winter, boolean freeze, boolean melt) {}

    private static final Material[] LEAF_COLORS = {
            Material.ORANGE_CONCRETE_POWDER, Material.RED_CONCRETE_POWDER,
            Material.YELLOW_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER
    };

    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;
    private final Deque<ChunkRef> queue = new ArrayDeque<>();
    private final Map<Season, WeatherProfile> weather = new EnumMap<>(Season.class);
    private final Map<Season, Double> tickMultiplier = new EnumMap<>(Season.class);
    private final List<String> noWinterKeywords = new ArrayList<>();

    private boolean weatherControl = true;
    private boolean tickControl = true;
    private boolean tickFailed = false;
    private boolean chunkReconcile = true;
    private int scanRadius = 48;
    private int columnsPerSecond = 60;
    private int snowMaxLayers = 3;
    private double snowfallChance = 0.5;
    private double snowMeltChance = 0.35;
    private double snowMeltStart = 0.85;
    private double winterCoverOnLoad = 0.8;

    public WorldEffectsManager(SeasonsPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        weatherControl = c.getBoolean("weather.control-enabled", true);
        double[][] defaults = {
                {0.03, 0.10, 5, 12, 0.20},
                {0.008, 0.25, 3, 8, 0.35},
                {0.07, 0.01, 10, 30, 0.15},
                {0.05, 0.03, 8, 20, 0.02}
        };
        for (Season s : Season.values()) {
            double[] d = defaults[s.ordinal()];
            String p = "weather.seasons." + s.key() + ".";
            int min = Math.max(1, c.getInt(p + "min-minutes", (int) d[2]));
            int max = Math.max(min, c.getInt(p + "max-minutes", (int) d[3]));
            weather.put(s, new WeatherProfile(
                    c.getDouble(p + "start-chance", d[0]),
                    c.getDouble(p + "end-chance", d[1]),
                    min, max,
                    c.getDouble(p + "thunder-chance", d[4])));
        }

        scanRadius = Math.max(8, c.getInt("world.scan-radius", 48));
        columnsPerSecond = Math.max(0, c.getInt("world.columns-per-second", 60));
        chunkReconcile = c.getBoolean("world.chunk-reconcile", true);
        snowfallChance = c.getDouble("world.snowfall-chance", 0.5);
        snowMaxLayers = Math.max(1, Math.min(8, c.getInt("world.snow-max-layers", 3)));
        snowMeltChance = c.getDouble("world.snow-melt-chance", 0.35);
        snowMeltStart = c.getDouble("world.snow-melt-start", 0.85);
        winterCoverOnLoad = c.getDouble("world.winter-cover-on-load", 0.8);
        noWinterKeywords.clear();
        for (String k : c.getStringList("world.no-winter-keywords")) {
            noWinterKeywords.add(k.toLowerCase(Locale.ROOT));
        }

        ConfigurationSection mult = c.getConfigurationSection("growth.random-tick-multiplier");
        tickMultiplier.putAll(Util.seasonMap(mult, 1.0));
        boolean wasControlling = tickControl;
        tickControl = c.getBoolean("growth.control-random-tick-speed", true);
        if (!tickControl && wasControlling) restoreRandomTickSpeed();
        tickFailed = false;
    }

    // ---------------------------------------------------------------- weather

    public void tickWeather() {
        if (!weatherControl) return;
        WeatherProfile wp = weather.get(seasons.season());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World w : Bukkit.getWorlds()) {
            if (!seasons.isActiveWorld(w)) continue;
            if (w.hasStorm()) {
                if (r.nextDouble() < wp.endChance()) {
                    w.setStorm(false);
                    w.setThundering(false);
                }
            } else if (r.nextDouble() < wp.startChance()) {
                int minutes = r.nextInt(wp.minMinutes(), wp.maxMinutes() + 1);
                w.setClearWeatherDuration(0);
                w.setStorm(true);
                w.setWeatherDuration(minutes * 1200);
                boolean thunder = r.nextDouble() < wp.thunderChance();
                w.setThundering(thunder);
                if (thunder) w.setThunderDuration(minutes * 1200);
            }
        }
    }

    // --------------------------------------------------- surface (snow / ice)

    private State currentState() {
        Season s = seasons.season();
        double p = seasons.progress();
        return new State(s, p, s == Season.WINTER, seasons.isFreezePeriod(),
                s != Season.WINTER || p >= snowMeltStart);
    }

    public void tickSecond() {
        State st = currentState();
        applyRandomTickSpeed(st.season());
        if (columnsPerSecond <= 0) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (!seasons.isActiveWorld(w)) continue;
            Location l = p.getLocation();
            for (int i = 0; i < columnsPerSecond; i++) {
                int x = l.getBlockX() + r.nextInt(-scanRadius, scanRadius + 1);
                int z = l.getBlockZ() + r.nextInt(-scanRadius, scanRadius + 1);
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                applyColumn(w, x, z, st, false);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!chunkReconcile) return;
        World w = e.getWorld();
        if (!seasons.isActiveWorld(w) || queue.size() > 4096) return;
        queue.add(new ChunkRef(w.getUID(), e.getChunk().getX(), e.getChunk().getZ()));
    }

    /** Processes one queued freshly loaded chunk per tick. */
    public void processQueue() {
        ChunkRef ref = queue.poll();
        if (ref == null) return;
        World w = Bukkit.getWorld(ref.world());
        if (w == null || !seasons.isActiveWorld(w) || !w.isChunkLoaded(ref.x(), ref.z())) return;
        State st = currentState();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                applyColumn(w, (ref.x() << 4) + i, (ref.z() << 4) + j, st, true);
            }
        }
    }

    private void applyColumn(World w, int x, int z, State st, boolean onLoad) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        Material topType = top.getType();
        String biome = Util.biomeKey(w, x, top.getY(), z);
        boolean nativelyCold = Util.isNativelyCold(biome);
        boolean excluded = Util.containsAny(biome, noWinterKeywords);

        // --- water freezes in mid-winter and thaws afterwards
        if (topType == Material.WATER) {
            if (st.freeze() && !excluded
                    && top.getBlockData() instanceof Levelled level && level.getLevel() == 0
                    && !top.getRelative(BlockFace.UP).isLiquid()) {
                top.setType(Material.ICE, false);
            }
            return;
        }
        if (topType == Material.ICE && !st.freeze() && !nativelyCold
                && top.getRelative(BlockFace.DOWN).getType() == Material.WATER
                && (onLoad || r.nextDouble() < 0.5)) {
            Block above = top.getRelative(BlockFace.UP);
            if (above.getType() == Material.SNOW) above.setType(Material.AIR, false);
            top.setType(Material.WATER, false);
            return;
        }

        if (excluded) return;

        // --- snow
        Block above = top.getRelative(BlockFace.UP);
        Block snow = null;
        Block ground = top;
        if (topType == Material.SNOW) {
            snow = top;
            ground = top.getRelative(BlockFace.DOWN);
        } else if (above.getType() == Material.SNOW) {
            snow = above;
        }

        if (snow != null) {
            if (!nativelyCold && st.melt() && (onLoad || r.nextDouble() < snowMeltChance)) {
                Snow data = (Snow) snow.getBlockData();
                if (onLoad || data.getLayers() <= 1) {
                    snow.setType(Material.AIR, false);
                } else {
                    data.setLayers(data.getLayers() - 1);
                    snow.setBlockData(data, false);
                }
                return;
            }
            if (st.winter() && w.hasStorm() && r.nextDouble() < 0.25) {
                Snow data = (Snow) snow.getBlockData();
                if (data.getLayers() < snowMaxLayers) {
                    data.setLayers(data.getLayers() + 1);
                    snow.setBlockData(data, false);
                }
            }
            return;
        }

        if (st.melt() || !st.winter()) return;
        boolean place = false;
        if (w.hasStorm() && r.nextDouble() < snowfallChance) {
            place = true;
        } else if (onLoad && st.progress() >= 0.02 && r.nextDouble() < winterCoverOnLoad) {
            place = true;
        }
        if (place && canHoldSnow(ground) && isSnowReplaceable(above)) {
            above.setType(Material.SNOW, false);
        }
    }

    private static boolean canHoldSnow(Block ground) {
        Material m = ground.getType();
        if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE) return true;
        if (Tag.LEAVES.isTagged(m)) return true;
        return m.isOccluding() && m != Material.MAGMA_BLOCK;
    }

    private static boolean isSnowReplaceable(Block b) {
        if (b.isEmpty()) return true;
        Material m = b.getType();
        return m == Material.SHORT_GRASS || m == Material.FERN;
    }

    // -------------------------------------------------------- growth (ticks)

    private void applyRandomTickSpeed(Season s) {
        if (!tickControl || tickFailed) return;
        try {
            for (World w : Bukkit.getWorlds()) {
                if (!seasons.isActiveWorld(w)) continue;
                Integer current = w.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
                String key = "random-tick-original." + w.getName();
                if (!plugin.getData().contains(key) && current != null) {
                    plugin.getData().set(key, current);
                    plugin.saveData();
                }
                int original = plugin.getData().getInt(key, current == null ? 3 : current);
                double mult = tickMultiplier.getOrDefault(s, 1.0);
                int target = mult <= 0.0 ? 0 : Math.max(1, (int) Math.round(original * mult));
                if (current == null || current != target) {
                    w.setGameRule(GameRule.RANDOM_TICK_SPEED, target);
                }
            }
        } catch (Throwable t) {
            tickFailed = true;
            plugin.getLogger().warning("Could not adjust randomTickSpeed, seasonal growth speed is disabled: " + t);
        }
    }

    private void restoreRandomTickSpeed() {
        for (World w : Bukkit.getWorlds()) {
            String key = "random-tick-original." + w.getName();
            if (!plugin.getData().contains(key)) continue;
            try {
                w.setGameRule(GameRule.RANDOM_TICK_SPEED, plugin.getData().getInt(key));
            } catch (Throwable ignored) {
                // nothing sensible to do
            }
            plugin.getData().set(key, null);
        }
        plugin.saveData();
    }

    public void shutdown() {
        restoreRandomTickSpeed();
    }

    // -------------------------------------------------------------- particles

    public void tickParticles() {
        Season s = seasons.season();
        if (s != Season.WINTER && s != Season.AUTUMN) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (!seasons.isActiveWorld(w)) continue;
            Location l = p.getLocation();
            if (l.getBlock().getLightFromSky() < 14) continue;

            if (s == Season.WINTER) {
                if (!w.hasStorm()) continue;
                String biome = Util.biomeKey(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
                // vanilla already shows snow in cold biomes
                if (Util.isNativelyCold(biome) || Util.containsAny(biome, noWinterKeywords)) continue;
                p.spawnParticle(Particle.SNOWFLAKE, l.getX(), l.getY() + 6.0, l.getZ(), 25, 9.0, 3.0, 9.0, 0.02);
            } else {
                fallingLeaves(p, w, l);
            }
        }
    }

    private void fallingLeaves(Player p, World w, Location l) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            int x = l.getBlockX() + r.nextInt(-10, 11);
            int y = l.getBlockY() + r.nextInt(-2, 9);
            int z = l.getBlockZ() + r.nextInt(-10, 11);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = w.getBlockAt(x, y, z);
            if (!Tag.LEAVES.isTagged(b.getType())) continue;
            if (!b.getRelative(BlockFace.DOWN).isEmpty()) continue;
            Material color = LEAF_COLORS[r.nextInt(LEAF_COLORS.length)];
            p.spawnParticle(Particle.FALLING_DUST, x + r.nextDouble(), y - 0.1, z + r.nextDouble(),
                    1, 0.0, 0.0, 0.0, 0.0, color.createBlockData());
        }
    }
}
