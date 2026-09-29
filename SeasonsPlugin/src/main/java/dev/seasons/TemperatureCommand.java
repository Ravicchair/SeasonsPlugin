package dev.seasons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class TemperatureCommand implements TabExecutor {
    private final TemperatureManager temps;
    private final HudManager hud;

    public TemperatureCommand(TemperatureManager temps, HudManager hud) {
        this.temps = temps;
        this.hud = hud;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("hud")) {
            HudManager.Mode mode = args.length > 1 ? HudManager.Mode.parse(args[1], null) : null;
            if (mode == null) {
                p.sendMessage(Util.prefixed("HUD is " + hud.mode(p).name().toLowerCase(Locale.ROOT)
                        + ". Usage: /temperature hud <sidebar|actionbar|off>", NamedTextColor.GRAY));
            } else {
                hud.setMode(p, mode);
                p.sendMessage(Util.prefixed("HUD set to " + mode.name().toLowerCase(Locale.ROOT) + ".", NamedTextColor.GREEN));
            }
            return true;
        }

        Double body = temps.bodyTemp(p);
        TemperatureManager.Breakdown b = temps.breakdown(p);
        if (body == null || b == null) {
            p.sendMessage(Util.prefixed("Seasonal temperature isn't active here.", NamedTextColor.GRAY));
            return true;
        }
        p.sendMessage(Util.prefixed("Body temperature: " + hud.fmt(body) + " (heading to " + hud.fmt(b.target()) + ")",
                NamedTextColor.WHITE));
        p.sendMessage(line("Outside", hud.fmt(b.outside())));
        p.sendMessage(line("Armor", signed(b.armor())));
        p.sendMessage(line("Nearby heat/cold", signed(b.sources())));
        p.sendMessage(line("Water", signed(b.wet())));
        p.sendMessage(line("Drink", signed(b.drink())));
        return true;
    }

    private Component line(String label, String value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String signed(double v) {
        return String.format(Locale.ROOT, "%+.1f\u00b0", v);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Util.filter(List.of("hud"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            return Util.filter(List.of("sidebar", "actionbar", "off"), args[1]);
        }
        return List.of();
    }
}
