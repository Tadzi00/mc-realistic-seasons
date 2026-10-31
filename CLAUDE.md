# CLAUDE.md – RealisticSeasons

Paper 1.21.x plugin (Java 21). Package `io.github.tadzi00.realisticseasons`.

## Build & test
- `./gradlew build` – compile + JUnit 5 tests; jar in `build/libs/RealisticSeasons-<version>.jar`
- `./gradlew test` – tests only
- JDK 21 is auto-provisioned (foojay toolchain resolver); Gradle itself runs on Java 11+.
- Paper/MC version: `gradle.properties` (`paperApiVersion`, `minecraftVersion`).

## Read first
**DESIGN.md** – models, constants, algorithms, performance budgets, and the "Not bugs" list.
Check it before "fixing" behaviour; much of what looks odd is intentional.

## Rules
- **Do not break the stable contracts** (DESIGN.md §11): the `RS|v1|temp|…` / `RS|v1|room|…` raw output
  (`core/RawContract.java`, guarded by `RawContractTest`) and the `api/RealisticSeasonsAPI` method
  names/signatures (called reflectively by another project). Incompatible change ⇒ new version (`RS|v2`).
- **Keep pure logic in `core/` free of Bukkit imports** and covered by unit tests. Paper API is not on the test
  classpath on purpose.
- Never load chunks from the plugin (check `isChunkLoaded`), never scan rooms unbounded or every tick.
- Main thread only; no async world access.
- New behaviour ⇒ config toggle + DESIGN.md update + tests for the math.
