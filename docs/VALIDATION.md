# Validation

Current release: [0.3.0 native integration](M1-NATIVE-INTEGRATION.md), built and verified
offline. The record below describes the earlier 0.2.1 release and its server tests.

## 0.2.1-test reference validation

Status: 0.2.1 compiles and its recorded offline checks pass. Owner-run server
transects on 2026-10-14 match intended climate at all 129 unique sampled positions.
Generated-chunk, preview, cave, and performance validation remain separate.
Automated work did not launch a game/server/world.

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

## Server observations on 2026-10-14

The server log identified TerraLatitude 0.2.1-test and loaded saved profile
`8ee4dbe9a58c693e482f1c19c8367218eaf2fc598b5855c4099bbf9edadb5819`.
Two owner-run transects exported 65 and 129 rows. All 65 shorter-transect rows
exactly match rows in the longer export: 129 unique positions, not 194 independent
samples. The line is at X=1224, Z=-6724 through 9660, sampled every 128 blocks.

- Every exported temperature and humidity delta is zero.
- All 112 land samples receive negative altitude corrections.
- All 17 ocean samples use Y=64. Their tiny positive altitude deltas, below 0.0001,
  are consistent with final quantization rather than meaningful elevation warming.
- At X=1224, Z=828, ground Y=335, pre-altitude temperature 0.2675026 becomes -0.1213.
- The longer export predicts 24 biome IDs, including four Still Life biomes.
  These are observations of one line, not a complete reachable-biome inventory.

The exports compare the active decorated sampler with the intended composition.
Biome IDs are predictions from the generator, not stored chunk reads. No preview,
cave, global distribution, or generation-performance conclusion follows from this
sample set. Cold Hybrid Aquatic deep-reef predictions are consistent with that
mod's authored cold-ocean placements, not evidence of tropical leakage.

[Evidence and hashes](evidence/server-transects-2026-10-14.json),
[short export](evidence/server-transect-short-2026-10-14.csv),
[long export](evidence/server-transect-long-2026-10-14.csv).

## Recorded release checks

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
  static checks. The initial local installation retained a 0.2.0 backup; the later
  server observations above establish the active 0.2.1 diagnostic sampler result.

The ordering regression uses the installed Mixin comparator, HEAD locator,
injection-node tracking, and insertion implementation with compiled synthetic
cancelling callbacks. It does **not** bootstrap the full transformer, execute
FTF's wrapper, or generate a world. The NT parity harness likewise uses isolated
transformed dependency fixtures. Passing these checks is not full-pack validation.

Reports under `reports/` record artifact/dependency/source hashes and detailed
results, including `sampler-order.json` and `runtime-020-transect-findings.json`.

## Remaining owner-run validation

Use a separate fresh FTF comparison world or save copy to compare predicted and
stored biomes, river/coast outcomes, caves, save/reopen persistence, and generation
time. Existing chunks retain their earlier biomes. FTF preview and climate maps
still need the integration described in the [next specification](INTEGRATION-SPEC.md).

The [altitude arithmetic characterization](evidence/altitude-arithmetic-2026-10-14.json)
compares the current helper with a reconstruction of the pinned FTF float sequence.
It found a maximum quantized difference of 0.0001 in synthetic cases. This is a
compatibility consideration for future refactoring, not a full FTF runtime test or
an observed new gameplay defect.
