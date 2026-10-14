# TerraLatitude

Latitude bands that respect the landscape.

**0.2.0-test** integrates Natural Temperature's annual climate with FreeTerraForged
regional climate and elevation, before FTF/RU/Lithostitched choose biomes. It targets
Minecraft 1.21.1, NeoForge 21.1.257, the pinned custom FTF 1.0.0007 build, and NT 1.1.10.

This is an experimental, offline-verified build. Minecraft and worlds have not
been launched, and the addon has not been installed into the active instance.

## What is implemented

| Target | Current implementation |
| --- | --- |
| Shared climate for biome placement | One paired temperature/moisture result enters FTF's owned sampler path before provider selection and downstream selection decorators. |
| Independent moisture control | Separate temperature/moisture strengths retain configurable FTF regional and terrain moisture variation. |
| Consistent rivers and coasts | The same climate reaches climate-filtered RU/Lithostitched placements. Existing disabled/replaced biomes remain disabled/replaced. |
| World settings and diagnostics | A versioned world profile persists NT and TerraLatitude settings; operator commands inspect climate and export surface transects; an offline audit identifies pack restrictions. |
| Coordinated transitions | A configurable five-point filter smooths the NT climate field while retaining NT's waves and FTF's regional noise. It adds no independent noise field. |

This changes **biome-selection climate**. FTF's native cached cell climate and its
preview are not rewritten; other features reading those cells may still see native
FTF climate. Seasonal weather, snow, crops, and player temperature are separate.
See [Climate design and limits](docs/CLIMATE-DESIGN.md).

## How the temperature and moisture work

Temperature uses FTF's regional temperature **before altitude**, evaluates NT's
selected mode with that input, blends the two, and applies FTF's elevation curve
once. Humidity is evaluated from the same paired NT inputs and blended with FTF's
native terrain-shaped moisture. There is no second terrain-moisture correction.
The other four biome climate coordinates are retained.

The native FTF elevation model replaces the previous addon's extra lapse-rate
correction. Climate units are not degrees Celsius. Ocean floors do not acquire
additional cooling based on water depth; FTF's own above-ground elevation gate is
respected. Altitude never depends on player height.

- Temperature band strength defaults to **1.0**.
- Moisture band strength defaults to **0.5**, retaining half of FTF's native moisture
  at full NT influence.
- Both strengths are multiplied by the saved NT influence. At zero NT influence,
  the integration returns native FTF climate exactly.
- A strength of zero preserves that axis's original quantized value.
- Transition radius defaults to **128 blocks**. Zero preserves NT's existing
  transition calculations without the added filter.
- Explicit NT randomized caves retain NT's native shared influence and cave model;
  independent strengths, surface cooling, and smoothing are bypassed there once
  integration is active. Existing FTF cave policies still run afterward.

NT built-in modes **0–10** are supported. Custom extension modes 101–105 are rejected
because their internal configuration reads cannot be guaranteed world-isolated.
NT's native mode algorithms execute from the dependency; they are not bundled.

## Test installation

1. Use a separate test instance and a fresh FTF world. Keep a copy of the intended
   preset/configs for comparisons. Previously generated chunks retain their biomes.
2. Remove any prior TerraLatitude JAR and the original `fnf_altitude` prototype.
   Add `dist/terralatitude-0.2.0-test-1.21.1.jar`. Do not load both altitude addons.
3. Start normally. The startup registration message alone does not prove runtime
   sampling succeeded. A new world profile is written at its first owned FTF sample.
4. Run `/terralatitude status`, then `/terralatitude sample` in the Overworld.
5. Compare low/high terrain at similar latitudes, wet/dry regions, rivers/coasts,
   caves, and generation speed. Biomes remain discrete even with smooth climate.
6. For controlled A/B comparisons, use separate fresh worlds with the same seed,
   preset, and pack. Keep the original 0.1.x artifact as a separate baseline if needed.

The exact reference FTF and NT binary hashes are pinned by the offline builder.
A matching version label alone does not guarantee compatibility with another FTF
build. Runtime Mixin application, full-pack startup, appearance, and performance
still need testing.

## Saved world settings

Common config `config/terralatitude-common.toml` supplies defaults only for worlds
without an existing profile; see [example](examples/terralatitude-common.toml).
The 0.1.x altitude configuration keys are replaced by the new `[climate]` defaults.

On first use, the addon saves `<world>/terralatitude-climate.json`, containing:

- NT mode, seed, bands, offsets, waves, climate zones, global modifiers, and caves;
- separate climate strengths and transition radius;
- the selected FTF altitude strength and a fingerprint of its full preset.

Later sessions load that immutable profile. Editing the global NT/common config
does not change the integrated climate of a profiled world. Server generation uses
its own saved settings. No client-side packet synchronization or FTF preview
integration is provided by this release; use the server commands for diagnostics.

To tune a **new comparison world**, edit the common defaults and NT settings before
creating it. An existing profile can be edited while the world is stopped, but
changes affect only newly generated chunks. There is no live climate-reload command.
A mismatched world seed, changed FTF preset, invalid profile, or failed profile write
raises a clear error rather than silently generating with different settings.
Keep the preset alongside the save. These checks do not freeze other mods' rules.

With no existing profile, `enabled = false` leaves normal FTF+NT behavior untouched
and creates no profile. For an already profiled world, its saved `enabled` field is
authoritative. This escape hatch does not make unsupported custom modes available
inside enabled TerraLatitude integration.

## Diagnostic commands

Commands require operator permission level 2 and run on the server:

- `/terralatitude status` — show active profile fingerprint and controls.
- `/terralatitude sample` — inspect predicted surface climate and biome at your X/Z.
- `/terralatitude transect 4096 128` — export a north–south line across your X/Z,
  with ground height, raw/band/final temperature and moisture, and predicted biome.

Transects allow at most 129 samples. CSV files go under
`<world>/terralatitude-diagnostics/`. Commands evaluate terrain/noise and biome
selection without explicitly requesting/loading chunks. They can still take time;
biome predictions may be affected by later pack hooks. Exported units are climate
units, not Celsius, and predictions do not rewrite existing chunks.

## Offline build and checks

Requires Python 3.11+ and Java 21. This checkout has an ignored `build.local.toml`
pointing to the prepared F&F Terra Prism instance. For another checkout, copy
`build-settings.example.toml` to `build.local.toml` and set its dependency paths.
No game or dependency downloads are performed; no upstream JARs are distributed.
On this Mac, use `/opt/homebrew/bin/python3` if the default Python is too old.

```sh
python3 build.py
python3 verify.py
python3 tools/test_nt_adapter.py
python3 -m unittest discover -s tests -p 'test_audit_pack.py' -v
python3 tools/audit_pack.py
```

Build, verify, and NT parity accept `--instance`, `--prism-dir`, `--java-home`.
CLI overrides `TERRALATITUDE_*` environment variables, then `build.local.toml`.
The pack audit accepts `--instance`. See [validation](docs/VALIDATION.md) and the
[pack audit guide](docs/PACK-AUDIT.md).

The NT parity fixture applies compiled redirect handlers to test-only dependency
bytecode and supplies raw inputs without starting Minecraft. It verifies numbers
and settings isolation; it does **not** exercise the actual Mixin launch process.

## Pack restrictions and future work

The reference pack still replaces jungles with redwoods and disables some RU
rainforest/tropical-river placements. The offline audit reports these restrictions;
TerraLatitude does not enable a tropical palette or override Biome Replacer.
See [development handoff](docs/DEVELOPMENT.md) for outstanding runtime checks.

Optional later work: a direct upstream FTF climate API, integrated FTF preview and
cell consumers, biome ecology profiles, and season/weather adapters. Directional
rain shadows and physiological treelines are not implemented.

Source and addon: MIT. FTF, NT, Minecraft, and other dependencies retain their own
licenses and are not included in the distributed JAR.
