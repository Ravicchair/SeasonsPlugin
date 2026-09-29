package dev.seasons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SeasonCommand implements TabExecutor {
    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;
    private final HudManager hud;

    public SeasonCommand(SeasonsPlugin plugin, SeasonManager seasons, HudManager hud) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.hud = hud;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            info(sender);
            return true;
        }
        if (!sender.hasPermission("seasons.admin")) {
            sender.sendMessage(Util.prefixed("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                Season s = args.length > 1 ? Season.parse(args[1], null) : null;
                if (s == null) {
                    sender.sendMessage(Util.prefixed("Usage: /season set <spring|summer|autumn|winter>", NamedTextColor.GRAY));
                } else {
                    seasons.setSeason(s);
                    sender.sendMessage(Util.prefixed("It is now the start of " + s.displayName() + ".", NamedTextColor.GREEN));
                }
            }
            case "skip" -> {
                int days;
                try {
                    days = args.length > 1 ? Integer.parseInt(args[1]) : 0;
                } catch (NumberFormatException ex) {
                    days = 0;
                }
                if (days == 0) {
                    sender.sendMessage(Util.prefixed("Usage: /season skip <days>", NamedTextColor.GRAY));
                } else {
                    seasons.skipDays(days);
                    sender.sendMessage(Util.prefixed("Skipped " + days + " day(s). It is now "
                            + seasons.season().displayName() + ", day " + seasons.dayOfSeason() + ".", NamedTextColor.GREEN));
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(Util.prefixed("Configuration reloaded.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Util.prefixed("Usage: /season [info|set <season>|skip <days>|reload]", NamedTextColor.GRAY));
        }
        return true;
    }

    private void info(CommandSender sender) {
        Season s = seasons.season();
        TextComponent.Builder title = Component.text();
        title.append(Component.text(s.symbol() + " " + s.displayName(), s.color(), TextDecoration.BOLD));
        title.append(Component.text("  Day " + seasons.dayOfSeason() + "/" + seasons.seasonLength()
                + " \u2022 " + seasons.monthName(), NamedTextColor.GRAY));
        sender.sendMessage(title.build());

        int left = seasons.daysUntilNextSeason();
        sender.sendMessage(Component.text(s.next().displayName() + " arrives in " + left + (left == 1 ? " day." : " days."),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Seasonal base temperature: " + hud.fmt(seasons.baseTemperature()),
                NamedTextColor.GRAY));
        if (seasons.isFreezePeriod()) {
            sender.sendMessage(Component.text("It's mid-winter: lakes and rivers are freezing over.", NamedTextColor.AQUA));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("seasons.admin");
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("info"));
            if (admin) base.addAll(List.of("set", "skip", "reload"));
            return Util.filter(base, args[0]);
        }
        if (args.length == 2 && admin && args[0].equalsIgnoreCase("set")) {
            return Util.filter(List.of("spring", "summer", "autumn", "winter"), args[1]);
        }
        return List.of();
    }
}
