# TerraLatitude 0.2.2: integration foundation

Historical release record. The subsequent [0.3.0 native integration](M1-NATIVE-INTEGRATION.md)
completed the climate source audit and replaced the private FTF adapter.
The source-access limitation below applied to this earlier development pass.

Status: built and verified offline on 2026-10-14; ready for owner testing.
This is partial M0/M1 progress plus bounded diagnostic improvements from M2.
It does not complete the FTF source audit or preview integration.

## Delivered

`ClimateComposition` takes the saved profile, coordinates, native climate target,
and raw regional snapshot as explicit inputs. It does not acquire terrain, bind
worlds, read FTF's global preset, or request chunks. The existing sampler hook and
its order remain in place; `IntegratedClimate` handles ownership and acquisition.

`RawClimateAccess.fromCell` reads an already populated FTF cell into an immutable
snapshot. It uses FTF's `Levels.scale`, `getNormalizedInlandElevation`, and ground
threshold. The snapshot does not retain or modify the cell. The ordinary path still
uses FTF's terrain lookup; no second terrain engine, noise graph, or cache was added.
The six private-field accessors and legacy altitude arithmetic remain compatibility
code pending a proper FTF source review.

Surface diagnostics previously acquired raw terrain inputs once to choose a sample
height and again to calculate expected climate. They now reuse the first snapshot
for both. The actual decorated sampler and biome prediction are still evaluated
independently. This removes one explicit raw-input acquisition in the normal
surface diagnostic path; it is not a claim about overall world-generation speed.

The optional command `/terralatitude transect 4096 128 east_west` adds east–west
sampling. Existing command syntax retains north–south sampling and the 129-sample
cap. CSVs keep the original 17 columns and append normalized elevation, altitude
strength, climate model, and profile fingerprint. Status also shows the model.

## Compatibility and verification

The artifact is `terralatitude-0.2.2-test-1.21.1.jar`,
61,232 bytes, SHA-256:

```text
35845275b0a9fa9300d2fdbb8301d3e6b53a6a948e129f534e791f9969d8db5a
```

It uses the same pinned FTF and NT dependencies. Saved profile schema 1 and climate
arithmetic are unchanged. `ftf-nt-legacy-0.2.1` is the calculation identifier, not
the installed addon version. No profile migration is required.

- The unmodified project baseline rebuilt to the recorded 0.2.1 JAR hash.
- 12,672 supplied-input cases matched that compiled legacy calculation exactly:
  final T/H, intermediate doubles, raw-input branches, cave behavior, and the four
  untouched target coordinates. The fixture also checks independent zero-strength
  axes, missing inputs, saved-profile serialization, and concurrent profiles.
- 252 populated-cell cases passed against native FTF `Levels` and `Cell` code,
  including multiple sea levels/heights, edge/warp inputs, gates, and snapshot
  independence. Test-only setup supplies noise inputs and accessor getters; it
  does not exercise a real terrain lookup or prove Mixin application.
- Five ordinary Java test programs passed, including both transect axes, limits,
  coordinate overflow, existing climate math, and saved-profile checks.
- All 54 package/bytecode checks passed. NT parity passed 191,274 assertions;
  the sampler-order fixture passed 41; all 10 pack-audit tests passed.

The seven measured composition-only batches followed warmup. Median time changed
by about -1.3%, but the observed range exceeded the proposed 5% tolerance, so the
result is **inconclusive**. This does not establish full sampler cost, terrain
cache behavior, or chunk generation performance. No speedup is claimed.

[Machine-readable evidence](evidence/m1-foundation-0.2.2.json) records hashes,
counts, limitations, and timings. Detailed generated reports are under `reports/`.
The new fixture is reproducible with:

```sh
python3 build.py
python3 verify.py
python3 tools/test_composition.py --baseline-jar /path/to/terralatitude-0.2.1-test-1.21.1.jar
python3 tools/test_nt_adapter.py
python3 tools/test_sampler_order.py
python3 -m unittest discover -s tests -p 'test_audit_pack.py' -v
```

The exact baseline JAR is required; it can be rebuilt from commit `762a41c` with
its pinned dependencies. The legacy fixture replaces ownership/native/raw input
acquisition in the old compiled class and retains its composition bytecode. It is
an independent version comparison, not proof of a live server's transformed path.

## FTF reuse inventory and remaining work

This table is an installed-binary inventory, **not the required FTF source audit**.
Gortex rejected the explicit FTF worktree path from the Prism-scoped task. FTF source
was not read through a shell fallback. Git metadata showed a clean checkout at
`583a375def5132fba43930367185a2b35fef901b`; its relationship to the installed JAR has
not been established.

| Area | Existing FTF capability / current use | Remaining decision |
| --- | --- | --- |
| Terrain acquisition | `UndergroundBiomeSurfaceProtection.sampleSurfaceY` uses FTF's lookup; the new `fromCell` boundary also accepts populated cells. | Find a supported call site that already has equivalent inputs. |
| Height and altitude gate | FTF `Levels.scale`, `getNormalizedInlandElevation`, and `ground` are used directly. | Keep these authoritative across any new hook. |
| Raw regional climate and warping | Existing FTF noise graphs are read through six accessors; coordinate preparation remains the compatibility adapter. | Source review must identify reusable access or a minimal FTF extension. |
| Altitude shaping | The pinned private `ClimateModule.modifyTemp` reads a global preset; schema-1 arithmetic is retained locally. | Prefer an explicit-settings FTF helper if practical, with legacy rounding compatibility. |
| Owner lifecycle and sampler order | Existing FTF plan/semantics and TerraLatitude ownership checks remain. | Validate actual runtime owners and ordering before changing interception. |
| Preview | Existing FTF preview capabilities were identified in prior binary research. | Establish a contained reuse path; broad lifecycle/display rewrites are deferred. |

Next: finish the FTF source audit from its tracked project, then select the smallest
supported integration change. Full cold/warm sampler measurement, runtime ownership
checks, preview parity, and in-world appearance remain acceptance work. M0/M1 are
not marked complete by this release. No FTF source modification was made.

## Owner test procedure

Use a separate test instance/save copy with the same FTF preset and saved climate
profile. Keep only the new TerraLatitude JAR alongside the unchanged pinned FTF/NT
builds. Installation is separate; this development pass did not modify the local
instance or server.

1. Run `/terralatitude status` and `/terralatitude sample`. Check the saved profile
   identity and `MATCH` at representative surface locations.
2. Run `/terralatitude transect 4096 128` and the same command with `east_west`.
   Expect 65 rows each, with the appropriate coordinate varying and new metadata
   present. Compare the north–south output with 0.2.1 at the same origin/profile.
3. Check land, ocean surfaces, uplands, and caves; reopen the save and confirm the
   profile identity remains stable. Check ordinary generation responsiveness.

CSV biome IDs remain predictions, not reads from persisted chunk data. A numerical
mismatch needs investigation; it does not by itself identify a failing mod.

Rain shadows, geographic moisture/ocean modelling, hydrology coupling, and other
features of comparable complexity remain deferred. Biome and bank placement rules
were audited but not changed in this release.
