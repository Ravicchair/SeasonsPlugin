# Seasons (Paper 26.2, Java 25)

Four seasons with a temperature system, thermometer HUD, seasonal weather,
tree/crop growth and animal spawning.

## Build

Requirements: JDK 25 and Maven 3.9+ (internet needed the first time, to fetch paper-api).

    mvn clean package

The plugin jar is `target/Seasons-1.0.0.jar`. Put it in your server's `plugins/` folder
and start the server. Edit `plugins/Seasons/config.yml`, then run `/season reload`.

## Commands

- `/season` - current season, day, month, days until the next one
- `/season set <season>` / `/season skip <days>` / `/season reload` - admin (`seasons.admin`)
- `/temperature` (`/temp`) - your body temperature and what contributes to it
- `/temperature hud <sidebar|actionbar|off>` - choose the HUD style

## Drinks

- Hot Cocoa: honey bottle + 2 cocoa beans (shapeless) - warms you for 3 minutes
- Chilled Melon Drink: honey bottle + snowball + melon slice (shapeless) - cools you for 3 minutes

## Easiest build (no Maven install)

Install JDK 25 (https://adoptium.net), then double-click `build.bat` (Windows) or run
`./build.sh` (Linux/Mac). It downloads what it needs and creates `Seasons.jar` next to it.
