# RealisticSeasons

A standalone Paper plugin (1.21.x) that adds **realistic seasons, temperature, room insulation, body
temperature and freezing**. It works for ordinary human players — no bots or other plugins required.

## Features

- **Seasons** – SPRING / SUMMER / AUTUMN / WINTER with a configurable length (in in-game days), stored
  per world (world PersistentDataContainer). Optional smooth temperature transition around season boundaries.
- **Outdoor temperature** (°C) = season base + biome offset + day/night swing + altitude lapse (colder up
  high, deep underground tends to a stable ~10 °C with a slight geothermal gradient) + rain/thunder + small
  deterministic noise. Fixed temperatures in the Nether and the End.
- **Rooms** – a bounded flood fill detects enclosed rooms (default max 1000 air blocks). Closed doors seal;
  open doors / trapdoors / fence gates / glass panes / fences / iron bars are *small leaks*; holes to the
  outside are *large leaks*. Wall materials insulate differently (wool best, then logs/planks, stone,
  glass worst).
- **Heat** – torches, lanterns, lit campfires and furnaces, fire, lava, magma… (all configurable). A sealed
  room is heated as a whole; a leaky room loses most of its heat so only spots near the sources stay warm.
  Room temperature has thermal inertia: opening a door does not instantly freeze the room.
- **Body temperature** – core temperature drifts toward what you feel. Clothing (leather is best) slows
  cooling, being wet (water/rain) speeds it up, sprinting warms you. Stages: comfortable → cold → shivering
  (slowness, mining fatigue) → hypothermia (freeze damage, frost overlay) → death ("froze to death").
  Optional heat stroke (off by default). Persists across relogs; reset on respawn.
- **Winter world effects** – exposed water slowly freezes, snow layers build up during storms, ice and snow
  thaw again when it warms up (throttled, near players only).
- **Crops** – plants, saplings, grass/vine spread and bone meal stop working below a cutoff (default 5 °C).
  Heated rooms count, so **in winter you can only farm indoors**.
- **Mobs** – seasonal spawn multipliers (more strays and wolves in winter, fewer farm animals…) and winter
  replacements (skeletons → strays below freezing).
- **Thermometer** item and machine-readable output for bots/tools (`RS|v1|…`), plus a Java API.
- **Difficulty** presets `easy | normal | hard | brutal | custom` plus per-feature multipliers; every
  feature has an enable toggle.

## Installation

1. Build (see below) or download the jar from the GitHub Actions artifacts.
2. Drop `RealisticSeasons-<version>.jar` into `plugins/` of a Paper 1.21.x server (Java 21).
3. Start the server, edit `plugins/RealisticSeasons/config.yml`, then `/rs reload`.

## Commands

| Command | Permission (default) | Description |
|---|---|---|
| `/temp` | `realisticseasons.temp` (all) | Felt temperature, outside temperature, body temperature, season, inside/outside. |
| `/room` | `realisticseasons.room` (all) | Room volume, sealed?, leaks by type, heat sources, room/outside temperature. |
| `/season` | `realisticseasons.season` (all) | Current season and day X/Y. |
| `/season set <season> [day] [world]` | `realisticseasons.season.admin` (op) | Jump to a season/day. |
| `/season length <days> [world]` | `realisticseasons.season.admin` (op) | Change season length for a world (keeps current season). |
| `/temp raw [x y z] [world]` | `realisticseasons.raw` (op) | One machine-readable line `RS\|v1\|temp\|{json}`. |
| `/room raw [x y z] [world]` | `realisticseasons.raw` (op) | One machine-readable line `RS\|v1\|room\|{json}`. |
| `/rs reload` | `realisticseasons.admin` (op) | Reload config.yml. |
| `/rs thermometer [player]` | `realisticseasons.admin` (op) | Give a thermometer. |
| `/rs info` | `realisticseasons.admin` (op) | Version, difficulty, cached rooms. |

Raw commands work from console/RCON (coordinates required; world defaults to the main world). Example:

```
> temp raw 100 64 -20 world
RS|v1|temp|{"inside":true,"sealed":true,"volume":75,"leaks":{"small":0,"large":0},"tempC":18.3,"outsideC":-5.2,"season":"WINTER","comfortable":true}
```

Errors are reported as one line too: `RS|v1|error|{"error":"chunk not loaded"}`.

### Thermometer

Shaped recipe (one column): **Glass** over **Redstone** over **Copper Ingot**. Hold it in either hand to see
felt temperature, body temperature, room status and outside temperature on the action bar.

## Configuration overview

`config.yml` is fully commented. Main sections:

- `difficulty` + `multipliers` – preset and fine-tuning (cooling rate, freeze damage, water freeze speed,
  snow, crop cutoff offset, winter temperature offset, season length, room max volume, heat source power,
  winter hostile spawns).
- `worlds` – enable/disable per world.
- `seasons` – length, smooth transition, base temperatures.
- `temperature` – biome/diurnal/altitude/weather/underground parameters, comfort range (default 12–28 °C).
- `rooms` – max volume, leak limits, wall insulation table, leaky materials.
- `heat` – heat model constants and the heat source table (`power`, `radius`, `requires-lit`).
- `body` – rates, thresholds, clothing table, damage, heat stroke toggle.
- `crops`, `world-effects`, `mobs`, `thermometer`, `performance`, `messages`.

## API (for other plugins)

```java
RegisteredServiceProvider<RealisticSeasonsAPI> rsp =
        Bukkit.getServicesManager().getRegistration(RealisticSeasonsAPI.class);
RealisticSeasonsAPI api = rsp.getProvider();
api.getSeason(world);            // "WINTER"
api.getTemperature(location);    // felt °C
api.isInsideSealedRoom(location);
api.isComfortable(location);
api.getRoomJson(location);       // same JSON as /room raw
api.getBodyTemperature(player);  // core °C
```

Interface: `io.github.tadzi00.realisticseasons.api.RealisticSeasonsAPI`. Call from the main thread.

## Building

Requires nothing but a JDK able to run Gradle (Java 11+); JDK 21 is downloaded automatically via the
Gradle toolchain resolver.

```bash
./gradlew build          # compile + unit tests
# jar: build/libs/RealisticSeasons-<version>.jar
```

To target a newer Paper release, bump `paperApiVersion` / `minecraftVersion` in `gradle.properties`.

See [DESIGN.md](DESIGN.md) for the models, algorithms and intentional behaviour.

## License

MIT © 2026 Tadzi00
