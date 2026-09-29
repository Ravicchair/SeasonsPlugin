package dev.seasons;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Thermometer HUD: a sidebar on the right of the screen, or an action bar. */
public final class HudManager {

    public enum Mode {
        SIDEBAR, ACTIONBAR, OFF;

        public static Mode parse(String text, Mode fallback) {
            if (text == null) return fallback;
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(text.trim())) return m;
            }
            return fallback;
        }
    }

    private static final String OBJECTIVE = "seasons_hud";
    private static final String[] ENTRIES = {"\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74", "\u00a75"};
    private static final int SEGMENTS = 10;
    private static final double BAR_MIN = -20.0;
    private static final double BAR_MAX = 50.0;

    // temperature, r, g, b
    private static final double[][] STOPS = {
            {-20, 90, 110, 255},
            {0, 90, 220, 255},
            {18, 90, 255, 110},
            {30, 255, 235, 70},
            {40, 255, 150, 50},
            {50, 255, 60, 60}
    };

    private final SeasonsPlugin plugin;
    private final SeasonManager seasons;
    private final TemperatureManager temps;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Mode> modes = new HashMap<>();
    private Mode defaultMode = Mode.SIDEBAR;
    private boolean fahrenheit = false;

    public HudManager(SeasonsPlugin plugin, SeasonManager seasons, TemperatureManager temps) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.temps = temps;
    }

    public void reload() {
        defaultMode = Mode.parse(plugin.getConfig().getString("hud.default-mode", "SIDEBAR"), Mode.SIDEBAR);
        fahrenheit = "FAHRENHEIT".equalsIgnoreCase(plugin.getConfig().getString("temperature.unit", "CELSIUS"));
        modes.clear();
    }

    public Mode mode(Player p) {
        return modes.computeIfAbsent(p.getUniqueId(),
                id -> Mode.parse(plugin.getData().getString("hud." + id), defaultMode));
    }

    public void setMode(Player p, Mode mode) {
        modes.put(p.getUniqueId(), mode);
        plugin.getData().set("hud." + p.getUniqueId(), mode.name());
        plugin.saveData();
        if (mode != Mode.SIDEBAR) clearSidebar(p);
    }

    public String fmt(double celsius) {
        double v = fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius;
        return String.format(Locale.ROOT, "%.1f\u00b0%s", v, fahrenheit ? "F" : "C");
    }

    // ---------------------------------------------------------------- update

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
        }
    }

    private void update(Player p) {
        double bodyTemp = temps.bodyTemp(p);
        TemperatureManager.Breakdown bd = temps.breakdown(p);
        Mode mode = mode(p);

        if (bd == null || mode == Mode.OFF) {
            clearSidebar(p);
            return;
        }

        Season season = seasons.season();
        if (mode == Mode.SIDEBAR && showSidebar(p, bodyTemp, bd, season)) return;
        p.sendActionBar(actionBar(bodyTemp, bd.outside(), season));
    }

    private boolean showSidebar(Player p, double temp, TemperatureManager.Breakdown bd, Season season) {
        UUID id = p.getUniqueId();
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        Scoreboard main = sm.getMainScoreboard();
        Scoreboard board = boards.get(id);

        if (board == null) {
            if (p.getScoreboard() != main) return false;
            board = sm.getNewScoreboard();
            Objective created = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Component.text("Climate", NamedTextColor.GOLD, TextDecoration.BOLD));
            created.setDisplaySlot(DisplaySlot.SIDEBAR);
            created.numberFormat(NumberFormat.blank());
            boards.put(id, board);
            p.setScoreboard(board);
        } else if (p.getScoreboard() != board) {
            boards.remove(id);
            return false;
        }

        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            boards.remove(id);
            return false;
        }

        double outside = bd.outside();

        Component[] lines = {
                join(Component.text(season.symbol() + " " + season.displayName(), season.color(), TextDecoration.BOLD),
                        Component.text("  Day " + seasons.dayOfSeason() + "/" + seasons.seasonLength(), NamedTextColor.GRAY)),
                join(Component.text("Body  ", NamedTextColor.GRAY),
                        Component.text(fmt(temp), colorAt(temp), TextDecoration.BOLD)),
                bar(temp),
                join(Component.text(status(temp, outside), colorAt(temp))),
                join(Component.text("Outside  ", NamedTextColor.GRAY),
                        Component.text(fmt(outside), colorAt(outside)))
        };

        for (int i = 0; i < lines.length; i++) {
            Score score = obj.getScore(ENTRIES[i]);
            score.setScore(lines.length - i);
            score.customName(lines[i]);
        }
        return true;
    }

    private Component actionBar(double temp, double outside, Season season) {
        return join(
                Component.text(season.symbol() + " " + season.displayName() + "  ", season.color()),
                bar(temp),
                Component.text("  " + fmt(temp), colorAt(temp), TextDecoration.BOLD),
                Component.text("  " + status(temp, outside), colorAt(temp)));
    }

    // ------------------------------------------------------------- rendering

    private static Component join(Component... parts) {
        TextComponent.Builder b = Component.text();
        for (Component part : parts) b.append(part);
        return b.build();
    }

    private Component bar(double temp) {
        double f = Util.clamp((temp - BAR_MIN) / (BAR_MAX - BAR_MIN), 0.0, 1.0);
        int filled = (int) Math.round(f * SEGMENTS);
        TextComponent.Builder b = Component.text();
        b.append(Component.text("[", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < SEGMENTS; i++) {
            if (i < filled) {
                double segTemp = BAR_MIN + (i + 0.5) * (BAR_MAX - BAR_MIN) / SEGMENTS;
                b.append(Component.text("\u2588", colorAt(segTemp)));
            } else {
                b.append(Component.text("\u2591", NamedTextColor.DARK_GRAY));
            }
        }
        b.append(Component.text("]", NamedTextColor.DARK_GRAY));
        return b.build();
    }

    private String status(double bodyTemp, double outsideTemp) {
        double cold = temps.coldThreshold();
        double hot = temps.hotThreshold();

        if (outsideTemp <= -15.0 || bodyTemp <= 20.0) return "Freezing (Damage)";
        if (bodyTemp < cold - 10.0) return "Freezing";
        if (bodyTemp < cold) return "Cold";
        if (bodyTemp < cold + 1.5) return "Chilly";
        if (bodyTemp < hot - 1.5) return "Comfortable";
        if (bodyTemp < hot) return "Warm";
        if (bodyTemp < hot + 6.0) return "Hot";
        return "Scorching";
    }

    private static TextColor colorAt(double t) {
        if (t <= STOPS[0][0]) return rgb(STOPS[0][1], STOPS[0][2], STOPS[0][3]);
        for (int i = 1; i < STOPS.length; i++) {
            if (t <= STOPS[i][0]) {
                double f = (t - STOPS[i - 1][0]) / (STOPS[i][0] - STOPS[i - 1][0]);
                return rgb(STOPS[i - 1][1] + f * (STOPS[i][1] - STOPS[i - 1][1]),
                        STOPS[i - 1][2] + f * (STOPS[i][2] - STOPS[i - 1][2]),
                        STOPS[i - 1][3] + f * (STOPS[i][3] - STOPS[i - 1][3]));
            }
        }
        double[] last = STOPS[STOPS.length - 1];
        return rgb(last[1], last[2], last[3]);
    }

    private static TextColor rgb(double r, double g, double b) {
        return TextColor.color((int) Math.round(r), (int) Math.round(g), (int) Math.round(b));
    }

    // ------------------------------------------------------------- lifecycle

    private void clearSidebar(Player p) {
        Scoreboard board = boards.remove(p.getUniqueId());
        if (board != null && p.getScoreboard() == board) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void forget(Player p) {
        boards.remove(p.getUniqueId());
        modes.remove(p.getUniqueId());
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearSidebar(p);
        }
    }
}
