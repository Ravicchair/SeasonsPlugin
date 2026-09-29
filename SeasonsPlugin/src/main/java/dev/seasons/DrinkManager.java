package dev.seasons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Craftable drinks that temporarily warm or cool you (honey bottles with a tag). */
public final class DrinkManager implements Listener {
    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;
    private final TemperatureManager temps;

    private final NamespacedKey drinkKey;
    private final NamespacedKey hotKey;
    private final NamespacedKey coolKey;

    private boolean enabled = true;
    private double hotAmount = 14.0;
    private double coolAmount = -14.0;
    private int hotSeconds = 180;
    private int coolSeconds = 180;

    public DrinkManager(SeasonsPlugin plugin, SeasonManager seasons, TemperatureManager temps) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.temps = temps;
        this.drinkKey = new NamespacedKey(plugin, "drink");
        this.hotKey = new NamespacedKey(plugin, "hot_cocoa");
        this.coolKey = new NamespacedKey(plugin, "chilled_drink");
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("consumables.enabled", true);
        hotAmount = c.getDouble("consumables.hot-cocoa.amount", 14.0);
        hotSeconds = Math.max(1, c.getInt("consumables.hot-cocoa.seconds", 180));
        coolAmount = c.getDouble("consumables.chilled-drink.amount", -14.0);
        coolSeconds = Math.max(1, c.getInt("consumables.chilled-drink.seconds", 180));

        unregister();
        if (enabled) {
            ShapelessRecipe hot = new ShapelessRecipe(hotKey,
                    drinkItem("hot", "Hot Cocoa", NamedTextColor.GOLD, "Warms you up for a while."));
            hot.addIngredient(Material.HONEY_BOTTLE);
            hot.addIngredient(2, Material.COCOA_BEANS);
            Bukkit.addRecipe(hot);

            ShapelessRecipe cool = new ShapelessRecipe(coolKey,
                    drinkItem("cool", "Chilled Melon Drink", NamedTextColor.AQUA, "Cools you down for a while."));
            cool.addIngredient(Material.HONEY_BOTTLE);
            cool.addIngredient(Material.SNOWBALL);
            cool.addIngredient(Material.MELON_SLICE);
            Bukkit.addRecipe(cool);
        }
    }

    public void unregister() {
        Bukkit.removeRecipe(hotKey);
        Bukkit.removeRecipe(coolKey);
    }

    public void discover(Player p) {
        if (enabled) p.discoverRecipes(List.of(hotKey, coolKey));
    }

    private ItemStack drinkItem(String kind, String name, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(drinkKey, PersistentDataType.STRING, kind);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (!item.hasItemMeta()) return;
        String kind = item.getItemMeta().getPersistentDataContainer().get(drinkKey, PersistentDataType.STRING);
        if (kind == null) return;

        Player p = e.getPlayer();
        if (!seasons.isActiveWorld(p.getWorld())) return;
        if ("hot".equals(kind)) {
            temps.addBuff(p, hotAmount, hotSeconds);
            p.sendActionBar(Component.text("You feel warm inside.", NamedTextColor.GOLD));
        } else if ("cool".equals(kind)) {
            temps.addBuff(p, coolAmount, coolSeconds);
            p.sendActionBar(Component.text("You feel refreshed.", NamedTextColor.AQUA));
        }
    }
}
