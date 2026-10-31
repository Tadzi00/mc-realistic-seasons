# RealisticSeasons – Design notes

This document is written for future maintainers (human or AI). It explains **what is intentional** so that
deliberate behaviour is not "fixed" as a bug. If you change a model, update this file and the unit tests.

## 1. Architecture

```
io.github.tadzi00.realisticseasons
├── core/        PURE logic, no Bukkit imports. Unit-tested (JUnit 5). Paper API is NOT on the test classpath,
│                so a Bukkit import here breaks the test build on purpose.
│   ├── Season, SeasonCalculator           calendar + smooth season base temperature
│   ├── OutdoorTemperatureModel            outdoor °C from season/biome/time/altitude/weather/noise
│   ├── HeatModel, HeatSource, PlacedHeatSource   room equilibrium, leaks, local heat, inertia
│   ├── RoomScanner, RoomScan, BlockProbe, CellKind   bounded flood fill over an abstract world
│   ├── BodyTemperatureModel, BodyStage     player core temperature
│   ├── DifficultyPreset, Multipliers       difficulty presets
│   └── RawContract                         RS|v1 line/JSON formatting (STABLE CONTRACT)
├── bukkit/      glue: Settings (config snapshot), SeasonService, RoomService (cache), TemperatureService,
│                BodyService, WorldEffectsTask, BukkitBlockProbe, Thermometer, listeners
├── command/     /temp, /room, /season, /rs
└── api/         RealisticSeasonsAPI (STABLE CONTRACT) + implementation
```

`Settings` is an immutable snapshot of config.yml with the difficulty preset already applied. Services always
read `plugin.settings()` so `/rs reload` swaps everything atomically. Everything runs on the main thread.

## 2. Seasons

- Calendar day = `world.getFullTime() / 24000 + offset`. `offset` (double) and an optional per-world length
  override live in the **world's PersistentDataContainer** (`realisticseasons:season_day_offset`,
  `realisticseasons:season_length_days`), so they are saved in level.dat.
- Year = 4 × length. Season *i* covers `[i·L, (i+1)·L)`; `dayOfSeason` is 1-based.
- `/season set` computes a new offset that keeps the intra-day fraction (time of day is never touched).
- `/season length` keeps the current season and day (clamped to the new length).
- **Smooth transition**: inside a window of `transition-days` centred on each boundary, the base temperature
  is linearly interpolated; exactly at the boundary it is the average of both seasons. Outside the window it
  is the season's base. The *season name* still switches exactly at the boundary.
- Default base temperatures (plains, °C): spring 10, summer 22, autumn 9, winter −6.

## 3. Outdoor temperature

```
T = seasonBase
  + (biomeTemp − 0.8) · 15                      biome offset: plains 0, desert +18, taiga −8.25, snowy −12
  + 5 · cos(2π (timeOfDay − 8000) / 24000)      warmest ~14:00 (tick 8000), coldest ~02:00
  − 0.05 · max(0, y − 64)                        altitude lapse (y=200 → −6.8 °C)
  + rain −3 / thunder −4.5                      only if storming AND biome humidity > 0 (no rain in deserts)
  + noise ±1.5                                  smooth value noise, 32-block cells, reseeded per day & world seed
then underground: depth = surfaceY − y; f = clamp((depth − 4) / (30 − 4), 0, 1)
  T = T·(1−f) + (10 + 0.05·max(0, 0 − y))·f     → caves are ~10 °C year-round, slightly warmer below y=0
```

- `biomeTemp` is vanilla's `Block#getTemperature()`, which already contains vanilla's small height adjustment
  above y≈80. That slight double-counting with our lapse rate is accepted.
- `surfaceY` uses heightmap `MOTION_BLOCKING_NO_LEAVES`. A normal house (3–4 blocks of roof) is therefore
  *not* "underground" – the room model handles houses.
- Nether = fixed 40 °C, End = fixed 5 °C. Worlds where the plugin is disabled report a neutral 20 °C.
- The model never loads chunks; in unloaded chunks it falls back to biome 0.8 / surface = y.

## 4. Room detection (RoomScanner)

Bounded BFS over cells classified by `BlockProbe`:

| CellKind | Blocks | Treatment |
|---|---|---|
| OPEN | air, torches, flowers, carpets, water, lava… (`!Material.isSolid()`) | part of the room volume, traversed |
| SOLID | any solid block | wall face; contributes its insulation value |
| DOOR_CLOSED | closed doors and closed trapdoors | wall face with `door-insulation` (sealed) |
| SMALL_LEAK | open doors/trapdoors, **all** fence gates, glass panes, iron bars, fences (configurable) | counted (unique positions), **not traversed** |
| UNKNOWN | unloaded chunk | scan aborts → outside |

Algorithm:
1. Start at the queried block; if it is not OPEN (slab, carpet-on-solid…) try the block above.
2. Start cell has sky access → **outside** (`SKY_AT_START`).
3. BFS. For each neighbour: SOLID/DOOR → boundary face; SMALL_LEAK → small leak; OPEN with **sky access** →
   large leak (the opening to the outside), not traversed; other OPEN → enqueue.
4. More than `max-volume` cells (default 1000) → **outside** (`TOO_LARGE`). More than `max-large-leaks`
   (default 8) sky openings → **outside** (`TOO_MANY_OPENINGS`, e.g. a missing wall or a porch).
5. Result: volume, unique small/large leak counts, average insulation over boundary faces, heat sources found
   in air cells *and* in boundary blocks (furnaces built into walls), bounding box, and a BitSet of cells.

"Sky access" = `y > highestBlockY(MOTION_BLOCKING_NO_LEAVES)`. Glass and panes block the sky (greenhouses
work); leaves do not (a leaf roof is not a roof).

A hole in a wall: the hole cell itself is usually covered (wall/roof above) so it is part of the room, and the
cell outside it has sky → one large leak per opening cell. An opening that leads into a big cave or another
large space instead makes the volume explode → outside. **That is intended**: a room connected to a big
volume cannot be heated.

### Why an open door is a *small* leak
An open door is an opening of controlled size that people pass through constantly; in reality a briefly
open door costs some heat but does not make a heated room as cold as outside. Modelling it as "small leak"
(retention ≈ 0.79 for a 2-block door) gives the expected gameplay: *a little colder*, but the room is still a
room. Thermal inertia (τ = 120 s) makes walking through a door nearly free. A real hole is different: hot
air escapes continuously, so a large leak costs far more (retention 0.55 per block).

Fence gates are always small leaks, even closed, because they are fences (air passes through).

## 5. Heat model (HeatModel)

```
sealedRise  = Σpower · insulation / (k · V^(2/3))           k = loss-coefficient = 0.1, capped at 45 °C
retention   = exp(−(small · 0.12 + large · 0.6))
T_room_eq   = T_out + retention · sealedRise
local(d)    = power · gain / (1 + d) · (1 − d/radius)        0 beyond radius; Σ capped at 40 °C
localWeight = inside: 0.3 + 0.7 · (1 − retention);   outside: 1
felt        = T_room (or T_out) + localWeight · Σ local
inertia     : T(t+dt) = T_eq + (T(t) − T_eq) · e^(−dt/τ),  τ = 120 s
```

Rationale:
- Heat loss through walls scales with **surface area**, not volume; area ∝ V^(2/3). The spec's "Σpower /
  (volume·k)" is implemented with a configurable exponent (`heat.volume-exponent`, default 2/3). Set it to 1.0
  for the literal volume form. Insulation multiplies the rise (wool 2.5× better than stone).
- Constants are tuned so: a 5×3×5 stone room (V=75) with one lit campfire (40) at −10 °C sits at ≈ +12 °C;
  with a lit furnace + 4 torches ≈ +20 °C; the same campfire in a 1000-block hall raises it by only 4 °C. One torch adds
  ≈ 3.4 °C to a small room. Big rooms need several heat sources – intended.
- Leaks are exponential so many leaks converge to "outside" smoothly. 2-block open door ≈ −21 % of the rise,
  1 hole ≈ −45 %, 2 holes ≈ −70 %, 4 holes ≈ −91 %.
- Local heat is what you feel near a source (radiant warmth). In a sealed room heat is already spread, so local
  weight is only 0.3; in a leaky room it rises toward 1 – "only spots near torches are warm".
- Inertia is integrated **exactly** (closed form) lazily at each query, so it is frame-rate independent.
- Heat source table (power / radius): torch 6/4, soul torch 3/3, lantern 8/4, soul lantern 4/3, campfire
  40/6 (lit), soul campfire 25/5 (lit), furnace & smoker 30/5 (lit), blast furnace 35/5 (lit), fire 35/5,
  soul fire 25/5, lava 60/6, magma 15/3, glowstone 3/2, shroomlight 3/2, jack-o'-lantern 5/3, candles 2/2 (lit).
- Plants use **outdoor temperature outdoors** (no local heat): a campfire in a field does not make crops grow
  in winter. Inside a room plants use room temperature + local heat.

## 6. Body temperature (BodyTemperatureModel)

Rates in °C of core per minute:
```
feltEff = felt − (wet ? 4 : 0)
cold:  drive = −0.03 · (comfortMin − feltEff) · (wet ? 1.5 : 1) · coolingMultiplier / (1 + clothing)
       core += (drive + metabolicHeat 0.1 [only if core < 37] + sprint 0.3) · dt
hot:   drive = +0.02 · (feltEff − comfortMax) · (1 + 0.3·clothing)   (only if heat stroke enabled)
       core += (drive − sweating 0.1 + sprint) · dt
neutral: core → 37 at 0.6 °C/min, never overshooting
stages: <36 cold, <35 shivering (slowness I, mining fatigue I), <33 hypothermia (slowness II, mining
        fatigue II, weakness, freeze damage 0.5/s growing with depth), clamp 25..43
```
- Clothing: per armor piece, by material prefix (`LEATHER_*` 0.75 → full leather = 3.0, iron 0.2, …).
  Leather divides cooling by 4. Naked at −10 °C felt: shivering after ~4 min, hypothermia after ~7 min; in full leather ~30 min to shivering.
- Wet lingers 30 s after leaving water/rain (dries twice as fast when warm).
- Felt temperature is refreshed every 2 s per player (room lookup is cached); the body integrates every 1 s.
- Freeze overlay: freeze ticks are **locked** (Paper `lockFreezeTicks`) at `maxFreezeTicks − 1` during
  hypothermia so vanilla never applies its own powder-snow damage on top of ours.
- Damage uses `DamageType.FREEZE` (heat stroke: `HOT_FLOOR`). The custom death message is applied only if
  the killing blow was that damage type and we dealt body damage within the last 2 updates.
- Creative/spectator, dead players and disabled worlds: no cooling, core recovers to normal.
- Persisted in player PDC `realisticseasons:body_temp` every update and on quit; reset on respawn.

## 7. World effects (WorldEffectsTask)

- Every `interval-ticks` (10) sample at most `columns-per-run` (48) random columns in total, split across
  players in NORMAL-environment enabled worlds, within `radius` (64) blocks, only in loaded chunks.
  → at most ~96 heightmap lookups per second regardless of player count.
- Only the topmost block (heightmap `MOTION_BLOCKING`, i.e. sky-exposed) is touched:
  - source WATER below 0 °C → ICE (chance scales with how far below 0).
  - snow: below 0 °C, during a storm (configurable), on an occluding top → SNOW layer, up to `max-layers` (3).
  - thaw: above 2 °C, ICE → WATER and snow layers shrink — **except in cold biomes** (vanilla temperature
    < 0.15) where vanilla's own snow/ice is kept.
- **Design choice (thaw)**: the plugin does not remember which ice it created (would need persistent per-block
  storage). It melts *any* exposed ice/snow layer in non-cold biomes when warm. Since vanilla never creates
  exposed ice in non-cold biomes, this almost only affects our ice – plus player-placed exposed ice.
- Freezing is temperature-driven, not season-driven: cold autumn nights in a taiga can freeze ponds.

## 8. Crops

`BlockGrowEvent`, `BlockSpreadEvent` (except fire), `StructureGrowEvent` (saplings, mushrooms; bone meal
optional) and `BlockFertilizeEvent` are cancelled when the plant temperature is below `crops.min-temperature`
(5 °C + difficulty offset). Cheap path first: if the outdoor temperature is warm enough nothing is scanned.
Otherwise the room cache is consulted with its own budget (2 scans/tick). If the budget is exhausted the
growth is cancelled – the plant retries on its next random tick, when the room is cached.

## 9. Mobs

`CreatureSpawnEvent` with reason NATURAL only. Multiplier < 1 cancels that fraction; > 1 spawns
`floor(m−1)` + probabilistic extra copies next tick (reason CUSTOM → never re-enters the listener), capped at
`max-extra-per-spawn`. In winter, monsters also get `winter-hostile-spawn` (difficulty). Winter replacements
(e.g. SKELETON → STRAY 50 %) apply only when the outdoor temperature at the spawn is below 0 °C.

## 10. Caching, invalidation and performance budgets

- Rooms are cached per world as (scan, BitSet of cells, stored temperature). Lookup hit = queried block or the
  block above is a room cell.
- Invalidation: any change inside the room's bounding box **+1** (place, break, burn, ignite, fade, form,
  explosions, pistons, entity block change, redstone-toggled openables, right-clicking an openable or a heat
  source block). Applied immediately and again one tick later (state changes after the event).
- Invalidated rooms leave a *ghost* (bbox + temperature, 10 min); the next scan overlapping it inherits the
  temperature → inertia survives rescans.
- Cached rooms are rescanned after `rescan-seconds` (30) on access (catches furnaces lighting up, which fires
  no block event) and dropped after `cache-expire-seconds` (300) without access.
- New rooms start at equilibrium (`seed-at-equilibrium: true`) so a restart does not make every house cold.
- Outside results are cached per block for `outside-cache-seconds` (5); cleared within 16 blocks of any
  invalidation.
- Scan budget: `performance.max-room-scans-per-tick` (4) for player updates, `crops.max-scans-per-tick` (2) for
  crops. Commands and the API (`Budget.FORCE`) are never refused. A scan is at most 1000 cells × 6 neighbours.
- Outdoors, local heat sources are found by scanning a cube of radius 5 (11³ = 1331 blocks) – per player only
  every 2 s.

## 11. STABLE CONTRACTS (do not change without a version bump)

### 11.1 Raw command output – `RS|v1`
`/temp raw [x y z] [world]` and `/room raw [x y z] [world]` print **exactly one line**:
```
RS|v1|temp|{"inside":true,"sealed":true,"volume":75,"leaks":{"small":0,"large":0},"tempC":18.3,"outsideC":-5.2,"season":"WINTER","comfortable":true}
RS|v1|room|{...same fields...}
RS|v1|error|{"error":"<message>"}        (bad args, no permission, chunk not loaded)
```
- Field order and names are fixed: `inside` bool, `sealed` bool, `volume` int (0 if outside), `leaks`
  {`small` int, `large` int}, `tempC` number (1 decimal), `outsideC` number (1 decimal), `season` uppercase
  string, `comfortable` bool (`tempC` within `comfort-min..comfort-max`, default 12..28).
- `temp`: `tempC` = felt temperature (room/outdoor + local heat). `room`: `tempC` = room air temperature
  when inside, felt outdoor temperature when outside.
- Numbers use `.` as decimal separator, never `-0.0`, never NaN.
- Console/RCON must pass coordinates; world defaults to the main (first) world. Players may omit coordinates
  and may use `~` relative coordinates. Permission `realisticseasons.raw` (default op).
- Formatting lives in `core/RawContract.java`, guarded by `RawContractTest`. Any incompatible change → `RS|v2`.

### 11.2 Java API – `io.github.tadzi00.realisticseasons.api.RealisticSeasonsAPI`
Registered in Bukkit's `ServicesManager`. Another plugin calls it **via reflection by method name**, so names,
parameter types and return types must not change:
```
String  getSeason(org.bukkit.World)
double  getTemperature(org.bukkit.Location)       // felt °C
boolean isInsideSealedRoom(org.bukkit.Location)
boolean isComfortable(org.bukkit.Location)
String  getRoomJson(org.bukkit.Location)          // JSON object of /room raw, no prefix
double  getBodyTemperature(org.bukkit.entity.Player)
```
Adding methods is fine; renaming/removing is not.

### 11.1 Additive v1 fields: body temperature

`/temp raw player <name>` (and `/room raw player <name>`) report at that player's position and append
`"bodyC":<one decimal>,"stage":"<COMFORTABLE|COLD|SHIVERING|HYPOTHERMIA|HOT|HEAT_STROKE>"` after `comfortable`.
The coordinate forms are unchanged. Adding fields at the end is allowed within v1 (readers ignore unknown
fields); removing/renaming still requires v2. Guarded by `RawContractTest.lineWithBodyAppendsBodyFields`.

## 12. Known simplifications / limitations

- One temperature per room (well-mixed air) + local radiant heat; no vertical stratification.
- Rooms adjacent to each other through an open door are separate rooms (the door is a small leak, not a
  passage) and do not exchange heat.
- Walls are not thermal masses; only room air has inertia.
- Sky access uses the heightmap, so a room under a tall overhang that is open on the sides may count as a
  (very leaky) room or as outside depending on how many openings it has.
- Water/lava in a room count as air for volume.
- Seasons follow world `dayTime` (`getFullTime`). With `doDaylightCycle false` the calendar stops.
- Biome temperature is sampled at the queried block (3D biomes) and includes vanilla's height adjustment.
- Mobs spawning via spawners, breeding, chunk generation etc. are not affected (NATURAL only).
- Body model is for players only (bots built on player entities are covered automatically).
- Player-placed exposed ice in non-cold biomes melts when it is warm (see §7).

## 13. "Not bugs" list

- **The `~5°C` action-bar hint without a thermometer is wrong on purpose.** It is the felt temperature
  plus a random error (±`max-error`, default 3 °C), rounded to `step` (5 °C), shown at random 20–60 s
  intervals and only while uncomfortable. It gives a feel for danger; the thermometer is the precise
  instrument. Holding a thermometer replaces the hint. Math: `core/RoughReading` (tested).

- Standing in a doorway (open door) is *a little* colder than inside, not freezing. Intended (§4).
- A huge hall (> 1000 air blocks) counts as outside even with a roof. Raise `rooms.max-volume`.
- A room with a missing wall / > 8 sky openings counts as outside.
- Crops next to a campfire outdoors still do not grow in winter; only rooms count (§5, §8).
- Bone meal does nothing on cold plants (message "Too cold for this plant to grow").
- A freshly built room is at equilibrium immediately (seed-at-equilibrium); opening/closing doors afterwards
  changes temperature only gradually (τ = 120 s).
- Lighting a furnace may take up to 30 s (rescan) to warm the room if no block event fired.
- Room temperature shown by `/room` lags behind the "heading to" equilibrium – that is thermal inertia.
- Ice forms in cold autumn nights/at high altitude outside winter – freezing is temperature-driven.
- Natural ice in frozen biomes never melts (cold-biome threshold) – intended.
- Body temperature recovers in creative/spectator instead of being frozen in place.
- `/temp raw` from console without coordinates prints an `RS|v1|error|…` line – coordinates are required.
- Glass roofs make rooms (greenhouses); leaf roofs do not.
- Thunderstorms in deserts do not cool (humidity 0 biomes have no rain).
