# TerraLatitude

Latitude bands that respect the landscape.

A standalone NeoForge compatibility project for **Natural Temperature +
FreeTerraForged**, with downstream biome selection by Regions Unexplored and
Lithostitched.

**Current release: 0.1.1-test.** This is the renamed, independently buildable
altitude prototype; the climate algorithm is unchanged from the first test JAR.
The proposed deeper integration is documented in [Climate design](docs/CLIMATE-DESIGN.md),
and its staged work is in [Development](docs/DEVELOPMENT.md). Planned features are
not implemented in this release.

## Current behavior

Natural Temperature supplies latitude bands. This addon makes high terrain colder
after those bands are calculated, before FreeTerraForged, Regions Unexplored, and
Lithostitched choose biomes. FTF continues to generate the landscape.

**Scope:** a first test build for this instance's custom FTF 1.0.0007 and Natural
Temperature 1.1.10 on NeoForge 21.1.257 / Minecraft 1.21.1. It is built and checked
offline; no game, server, or world was launched. The addon has not been installed
into the active instance. See [Validation](docs/VALIDATION.md) for the checked release and `reports/` for
regenerated local build evidence.

## What it does

- Uses FTF's predicted local ground height and the preset's actual sea level.
- Adds progressively more cooling above sea level while keeping the latitude
  pattern. At equal elevation, a warmer input remains warmer until values clamp.
- Preserves humidity, continentalness, erosion, depth, and weirdness exactly.
- Applies within FTF-owned Overworld generation; other generators and dimensions
  return their original samples.
- Leaves terrain at/below sea level unchanged. Deep ocean floors do not become
  artificially warm.
- Scales the extra correction by Natural Temperature's influence. At zero
  influence it leaves FTF's existing climate behavior alone.
- Preserves explicitly randomized underground samples when NT's underground
  randomization is enabled, including the exact threshold height.

Ground elevation determines cooling, not the player's height or the Y used to
query a biome. If underground randomization is off, the temperature correction
applies through the column; this can also influence cave biomes that use
temperature, while preserving their other selection parameters.

## The starting climate model

The default is inspired by an average environmental lapse rate of **6.5°C per km**,
as described by [NOAA](https://www.noaa.gov/jetstream/appendix/weather-glossary-l).
Actual atmospheric lapse rates vary. This is not a physical weather simulation.

Minecraft's climate axis is not Celsius, and a 500-block landscape compresses
real mountain relief. Two explicit tuning assumptions bridge those differences:

- 10 effective landscape meters per vertical block.
- 20 reference Celsius degrees per normalized climate unit.

At full NT influence, those defaults subtract **0.325 climate units per 100 blocks
above sea level**, clamped at the cold end of the normal climate range.

| Ground above sea level | Added cooling | Input +0.8 becomes | Input +0.25 becomes | Input 0 becomes |
| --- | --- | --- | --- | --- |
| 0 blocks | 0 | +0.800 | +0.250 | 0.000 |
| 100 blocks | 0.325 | +0.475 | −0.075 | −0.325 |
| 200 blocks | 0.650 | +0.150 | −0.400 | −0.650 |
| 400 blocks | 1.300 | −0.500 | −1.000 | −1.000 |

These are illustrative input climates, not guaranteed jungle/desert labels or
snowlines. Natural Temperature considers humidity too; its rainforest band is
not necessarily its hottest temperature band. Specific cold/alpine biomes still
depend on the generator's terrain context and available biome definitions.

The influence weighting avoids adding a full second correction to the part of
FTF climate retained by NT. It is an approximation: NT's default tropical formula
also retains a small original-temperature contribution, so exact reconstruction
of FTF's earlier altitude calculation is not claimed.

## Testing

1. Remove the old `ftf-naturaltemperature-altitude` prototype JAR if present;
   its mod ID is `fnf_altitude`. Do not load both: they would apply cooling twice,
   so TerraLatitude declares that old mod incompatible. Add
   `dist/terralatitude-0.1.1-test-1.21.1.jar` to a test
   instance containing the exact supported FTF and NT builds. RU and Still Life
   West can remain installed; the addon has no hard dependency on either.
2. Create a fresh FTF world with the desired preset. For an A/B comparison, use
   the same seed and configs in separate fresh worlds, one with the addon and
   one without.
3. Confirm the startup message `FTF / Natural Temperature altitude compatibility
   registered` and, during FTF world generation, `Altitude correction reached FTF
   Overworld biome sampling`. Registration alone does not prove the hook executed.
4. Compare lowland and highland biome IDs at similar latitude, in both temperate
   and tropical regions. Lowland candidates should remain comparable, while high
   terrain should shift toward cooler choices. Biome boundaries are discrete,
   so a gradual climate correction can still cause a distinct boundary.
5. Inspect snowy/alpine coverage, forest transitions, caves, and generation speed.
   Snow rendering, seasonal timing, and survival temperature remain controlled
   by the existing mods and are not independently validated by this addon.

No tropical biome exclusions or NT band distances are changed by this mod.
Restoring the proposed tropical selection is a separate configuration change.
The reference local NT config used equatorial distance 50,000 on 2026-10-13;
a previously saved server deployment used 10,000 (not live-verified). Align the
intended test configurations before comparing their results.

For multiplayer testing, use matching addon/config versions on the server and
test client. The common configuration is not automatically synchronized. Existing
generated chunks retain their stored biomes; changes affect later generation.

## Configuration

On first normal game load, NeoForge creates `config/terralatitude-common.toml`.
An example is provided at `examples/terralatitude-common.toml`. The old prototype
used `fnf-altitude-common.toml`; any manually tuned values must be copied into
the new configuration. No automatic migration changes an instance. Restart the world
after tuning values; for clean comparisons, create another fresh test world.

| Setting | Default | Effect |
| --- | --- | --- |
| `enabled` | `true` | Turns the correction on/off. |
| `lapse_rate_c_per_km` | `6.5` | Environmental lapse-rate reference; zero disables cooling. |
| `effective_meters_per_block` | `10.0` | Higher gives stronger cooling for the same height. |
| `degrees_per_climate_unit` | `20.0` | Higher gives weaker cooling. |
| `preserve_randomized_underground` | `true` | Respects NT's explicitly randomized cave samples. |

If mountain biomes are too cold, first try reducing `effective_meters_per_block`
to 5 rather than changing the latitude bands. There is no promise that the initial
defaults suit every FTF height/terrain preset.

## Implementation and verification

The mixin targets `WorldgenPlans$SamplerDecoration.sample` at its return. NT has
already adjusted the underlying Minecraft climate sample; base biome selection
and Lithostitched climate filtering are still ahead. This avoids competing with
NT's cancellable injection on the vanilla sampler.

FTF's `UndergroundBiomeSurfaceProtection.sampleSurfaceY` supplies ground height
using a pooled cell and its existing cached-or-calculated terrain lookup. The
addon does not query chunk heightmaps, load chunks, call biome selection
recursively, or retain generator contexts in its own cache. Normal FTF lookup
accuracy and lookup overhead remain limitations to evaluate in game.

Configuration events are registered on the **mod** event bus. A single immutable
configuration snapshot is published to generation threads. Terrain-lookup runtime
failures return the original climate and log one error instead of flooding the log.

Build and verify from this directory with **Python 3.11+ and Java 21**. The local
checkout is already configured for the existing F&F Terra instance using ignored
`build.local.toml`. On another checkout, copy `build-settings.example.toml` to
`build.local.toml` and set `instance` to a prepared Prism instance directory
(containing `mmc-pack.json`). Set optional `prism` or `java_home` paths if needed.
No dependency JARs are copied into the repository.

Both commands accept `--instance`, `--prism-dir`, and `--java-home`. Command-line
values override `TERRALATITUDE_INSTANCE`, `TERRALATITUDE_PRISM_DIR`, and
`TERRALATITUDE_JAVA_HOME`, which override the local config. Prism otherwise uses
its platform default; JDK discovery falls back to `JAVA_HOME` and then the OS.
Relative paths resolve against the project root.

On this Mac, `/opt/homebrew/bin/python3` meets the Python requirement; Apple's
`/usr/bin/python3` may be too old. Use a Python 3.11+ interpreter for:

```sh
python3 build.py
python3 verify.py
```

The offline build uses named classes and libraries already installed by Prism.
No upstream code or dependency jars are bundled. Verification includes pure-model
tests, compilation against the installed APIs, bytecode hook checks, metadata,
and artifact integrity. It does **not** start the Mixin runtime or prove the
complete modpack loads, actual biome frequency, visual snowlines, or performance.

The FTF runtime API is specific to the custom installed build. Exact reference
hashes are recorded in the build reports; matching a filename/version alone does
not establish compatibility with a different FTF build.

## Limits and future work

This addon changes biome-placement temperature. It does not directly implement
altitude-dependent player temperature, Ecliptic weather, snow accumulation,
treeline physiology, rain shadows, windward precipitation, or tropical cloud
forests. Choosing a colder biome can indirectly affect existing weather systems,
but those integrations need separate validation.

Source and addon: MIT. FreeTerraForged, Natural Temperature, Minecraft, and other
dependencies retain their own licenses and are not included in the artifact.
