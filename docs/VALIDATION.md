# Validation — TerraLatitude 0.2.1-test

Status: corrected build compiles and offline checks pass. The owner ran Minecraft
with 0.2.0 and supplied two transects; automated work did not launch a game/server/world.
The corrected 0.2.1 still requires an owner-run test.

Artifact: `dist/terralatitude-0.2.1-test-1.21.1.jar` — 55,472 bytes,
33 compiled addon classes.
SHA-256: `a8caea0ab0e4f656331934d5fdcdd5c6b18f8da4e41dba106130f603fdd71cd8`.

## Runtime finding in 0.2.0

The October 14 test produced 65-row and 128-row transects. All 171 land rows
showed substantially less altitude correction than the intended model. At
X=-7708, Z=-5216, ground Y=256, actual temperature was -0.2252; the saved preset
and smoothed latitude temperature imply approximately -0.5261 after the native
FTF altitude curve. These are climate coordinates, not Celsius.

The debug log confirms both callback mixins loaded. Installed Mixin ordering and
HEAD insertion put Natural Temperature's cancelling callback before TerraLatitude
when both used default injector order 1000. Mixin priority 2000 alone was not a
precedence guarantee. The earlier status command directly evaluated composition,
so its profile message did not establish that worldgen received that composition.

0.2.1 explicitly uses injector order 900. Status/sample compare intended and actual
quantized temperature and humidity; transects expose both and their deltas.
Ocean surface diagnostics no longer sample the seabed. Original saves, settings,
profiles, and CSVs were retained.

## Checks performed

- Java 21 compilation against exact pinned Minecraft/NeoForge/FTF/NT dependencies.
- Five ordinary test programs passed, including 15 targeted surface-coordinate
  cases for sea-level rounding, negative heights, and overflow handling.
- 50/50 static package/bytecode checks passed, now checking injector order rather
  than relying on class mixin priority for precedence.
- The sampler-order regression passed 41 assertions: old ordering returns NT for
  an owned sample, explicit order 900 returns TerraLatitude, and unowned samples
  still return NT. Reversed registration and equal class priorities were checked.
- NT calculator parity passed 191,274 assertions across built-in modes 0–10,
  settings, seeds, cave modes, and snapshot/thread isolation.
- All 10 pack-audit parsing/inventory tests passed.
- The final project rebuild produced the identical JAR hash and passed all 50
  static checks. Installed locally with 0.2.0 backed up; activation needs a full
  Minecraft restart. No save/configuration files or remote server were changed.

The ordering regression uses the installed Mixin comparator, HEAD locator,
injection-node tracking, and insertion implementation with compiled synthetic
cancelling callbacks. It does **not** bootstrap the full transformer, execute
FTF's wrapper, or generate a world. The NT parity harness likewise uses isolated
transformed dependency fixtures. Passing these checks is not full-pack validation.

Reports under `reports/` record artifact/dependency/source hashes and detailed
results, including `sampler-order.json` and `runtime-020-transect-findings.json`.

## Owner-run confirmation

Restart Minecraft with only 0.2.1 installed. Use a fresh FTF comparison world
because existing chunks retain the climate/biomes generated under 0.2.0.
Run `/terralatitude status`, `/terralatitude sample`, and the same transect again.
Expect `MATCH` and zero temperature/humidity deltas. Check nonzero negative
altitude corrections on uplands, biome distribution, river/coast outcomes,
Streams generation, caves, save/reopen persistence, and generation time.
FTF's preview still uses native cached climate rather than this composition.
