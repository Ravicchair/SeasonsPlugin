package dev.seasons;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SeasonCommand implements CommandExecutor {

    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;

    public SeasonCommand(SeasonsPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            World world = (sender instanceof Player) ? ((Player) sender).getWorld() : plugin.getServer().getWorlds().get(0);
            Season current = seasons.getSeason(world);
            int day = seasons.getDay(world);
            int length = seasons.getDaysPerSeason(world);

            sender.sendMessage(ChatColor.GOLD + "=== Season Status ===");
            sender.sendMessage(ChatColor.YELLOW + "Current Season: " + ChatColor.GREEN + current.name());
            sender.sendMessage(ChatColor.YELLOW + "Day: " + ChatColor.WHITE + day + " / " + length);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("seasons.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /season set <spring|summer|autumn|winter>");
                return true;
            }

            try {
                Season season = Season.valueOf(args[1].toUpperCase());
                World world = (sender instanceof Player) ? ((Player) sender).getWorld() : plugin.getServer().getWorlds().get(0);
                seasons.setSeason(world, season, 1);
                sender.sendMessage(ChatColor.GREEN + "Set season in " + world.getName() + " to " + season.name() + ".");
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid season! Choose spring, summer, autumn, or winter.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("skip")) {
            if (!sender.hasPermission("seasons.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            int days = 1;
            if (args.length > 1) {
                try {
                    days = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }
            seasons.skipDays(days);
            sender.sendMessage(ChatColor.GREEN + "Skipped " + days + " day(s).");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("seasons.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(ChatColor.GREEN + "Seasons configuration reloaded!");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown sub-command. Use /season <info|set|skip|reload>");
        return true;
    }
}
