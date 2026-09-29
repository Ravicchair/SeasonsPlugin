package dev.seasons;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TemperatureCommand implements CommandExecutor {

    private final SeasonsPlugin plugin;
    private final TemperatureManager temps;

    public TemperatureCommand(SeasonsPlugin plugin, TemperatureManager temps) {
        this.plugin = plugin;
        this.temps = temps;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            double currentBody = temps.bodyTemp(player);
            TemperatureManager.Breakdown b = temps.breakdown(player);

            player.sendMessage(ChatColor.GOLD + "=== Temperature Info ===");
            player.sendMessage(ChatColor.YELLOW + "Body Temp: " + ChatColor.WHITE + String.format("%.1f°C", currentBody));
            player.sendMessage(ChatColor.YELLOW + "Outside Temp: " + ChatColor.WHITE + String.format("%.1f°C", b.outside()));
            player.sendMessage(ChatColor.YELLOW + "Armor Modifier: " + ChatColor.WHITE + String.format("%+.1f°C", b.armor()));
            player.sendMessage(ChatColor.YELLOW + "Drink Buff: " + ChatColor.WHITE + String.format("%+.1f°C", b.drink()));
            player.sendMessage(ChatColor.YELLOW + "Target Body Temp: " + ChatColor.WHITE + String.format("%.1f°C", b.target()));
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.GOLD + "/temp info" + ChatColor.YELLOW + " to check your temperature breakdown.");
        return true;
    }
}
