# TerraLatitude 0.3.0: native FTF integration

Historical record. [Version 0.3.1](PR-API-COMPATIBILITY.md) removes the dependency
on the local-only double helper and uses the smaller API actually proposed in PR #301.

Status: implemented and verified offline on 2026-10-14. Both source repositories
changed. Neither artifact was installed, and no game, server or world was launched.
This completes the bounded source/API work for M1; runtime ownership, performance
and complete in-world acceptance remain pending.

## Delivered

FreeTerraForged now exposes raw regional climate through `Climate.sampleRaw` and
`ClimateModule.sampleRaw`, using its own edge displacement and coordinate warping.
The API consumes an already populated cell from the matching owner and position;
it returns an immutable pair without mutating or retaining the cell.

TerraLatitude calls that API. The six private-field accessor methods, accessor
Mixin, copied `RawClimateMath` and its obsolete unit test were removed. Three
Mixins remain: the existing sampler integration and the two NT settings adapters.

FTF now owns `TemperatureShaping` with explicit inputs and two documented precision
contracts. Native FTF keeps its 0..1 float calculation; TerraLatitude uses the
-1..1 double variant with its saved altitude strength. This preserves schema-1
world output instead of silently switching rounding. No second altitude correction,
regional noise graph, terrain sampler, cache or biome-selection engine was added.

The 0.2.2 composition boundary, surface diagnostic reuse, east–west transects and
expanded CSV metadata are retained. The same injector order 900 still runs ahead
of NT. Ownership checks, NT snapshot classes, climate defaults, saved profile schema
and the `ftf-nt-legacy-0.2.1` calculation identifier remain unchanged.

## Source reuse map

FTF source was inspected directly after the owner requested bypassing the broken
Gortex view. Reference: `583a375def5132fba43930367185a2b35fef901b`, branch
`biomeReplacerCompat`, initially clean. The original two climate source units were
compiled against the installed dependency set and compared with the installed
classes. Their normalized instruction streams matched. This verifies the edited
climate pair's provenance, not every class in the source repository.

| Responsibility | FTF source/API | Decision |
| --- | --- | --- |
| Terrain acquisition | `UndergroundBiomeSurfaceProtection.sampleSurfaceY`, existing context lookup | Reuse unchanged; accept an existing cell when available. |
| Heights and elevation gate | `Levels.scale`, `getNormalizedInlandElevation`, `ground` | Reuse unchanged. |
| Regional noise and warping | `ClimateModule.apply`, `applyRegion` | Add public `sampleRaw`; share the existing warp helpers within FTF. |
| Edge displacement | `Climate.apply`, `applyEdge` | Add `Climate.sampleRaw` sharing native edge policy/distance. |
| Elevation curve | `ClimateModule.modifyTemp` | Extract explicit-input `TemperatureShaping`; preserve native float and schema-1 double contracts separately. |
| Owner/selection path | `ClimateQuerySemantics`, `FTFClimateSampler`, `WorldgenPlans.SamplerDecoration` | Keep current binding and interception. SamplerDecoration executes after the inner sampler, so moving only there would let NT cancel first. |
| Preview and other cell consumers | FTF preview/lifecycle and cached native cell climate | Deferred: generation profiles and preview owners need an explicit shared binding; no global setting swaps or cell rewrites. |

FTF remains independent of NT and TerraLatitude at runtime. The API has no foreign
imports or dependencies. The local test API includes both precision helpers
listed above. [FTF PR #301](https://github.com/ETcodehome/FreeTerraForged/pull/301)
subsequently narrowed the upstream proposal to raw sampling and native float
shaping. It excludes the double helper used by this 0.3.0 snapshot, so the PR build
alone is not compatible with this version. See [next work](DEVELOPMENT.md#next-work).

## Supported test pair

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `terralatitude-0.3.0-test-1.21.1.jar` | 58,799 bytes | `a464d15e93f7986a7c9d073a7d150916d6d9b5f3855b127690455dbee2b961f2` |
| `freeterraforged-1.0.0007-climate-api.1-neoforge-1.21.1.jar` | 2,439,677 bytes | `5c43eeb2426d191fe7f8a5a992159dece09a4827fd4793e45b2742aaac769a33` |

These are records of local test artifacts, not repository download links.

Minecraft 1.21.1, NeoForge 21.1.257, Java 21, Natural Temperature 1.1.10.
TerraLatitude metadata requires the new FTF version. Build tooling also pins its
exact checksum and supports `--ftf-jar` / `TERRALATITUDE_FTF_JAR` / `ftf_jar` in
ignored `build.local.toml`, so testing does not require installation.

The full FTF Gradle `:neoforge:remapJar` build passed offline. The local FTF artifact listed
above is deliberately narrower: it overlays only three compiled climate source
units on the pinned installed NeoForge JAR. Exactly five file entries change:
`Climate.class`, `ClimateModule.class`, the two new classes, and NeoForge version
metadata. All other 1,028 file entries are byte-identical. The full Gradle output
is a separate compilation check, not the selected test dependency.

## Verification

| Check | Result and scope |
| --- | --- |
| Native FTF compiled-binary comparison | 27,000 cases and 3,105,000 assertions. Exact float altitude, all public cell fields after climate/region operations, raw noise values/coordinates, and no raw-sampling cell mutations. |
| TerraLatitude compiled 0.2.1 comparison | 12,672 cases and 136,580 assertions. Exact quantized axes and intermediate doubles, independent strengths, caves, bypasses, schema serialization and concurrent profiles. Original `ClimateMath` bytecode is retained independently in the oracle. |
| Populated FTF cell adapter | 252 cases, 2,035 assertions across heights, sea levels, gates, edges and immutable snapshots. |
| NT built-in modes | 191,274 assertions comparing modes 0–10 to the original dependency with supplied inputs and compiled settings redirects. |
| Mixin ordering fixture | 41 assertions; retained explicit pass 900 before NT's default 1000. |
| Package/bytecode verification | 58/58 checks, including the native API descriptors, exact dependency versions, removal of accessors and delegated altitude calculation. |
| Other offline checks | Four ordinary Java test programs, 10 pack-audit unit tests, complete read-only pack inventory. |

[Machine-readable evidence](evidence/native-integration-0.3.0.json) records hashes,
fixture details and limits. Generated reports under `reports/` retain the actual
staging paths used during testing; the public evidence replaces machine paths
with portable placeholders without changing artifact hashes or test results. Native FTF fixtures supply deterministic noise,
terrain and three preset settings; they do not construct a full real world. The
old/new native methods otherwise execute their compiled bytecode.

Composition timing remains inconclusive: the seven-batch range exceeded 5%.
There is no verified speedup, full sampler budget or chunk-generation benchmark.
No test here proves live Mixin startup, runtime owner binding or complete modpack
compatibility. The 129 matching server samples in earlier evidence are from 0.2.1,
not this release.

## Next owner test

When installation is requested, replace both JARs together in a test instance/save
copy and retain the original pair for rollback. Keep the same preset and saved
profile for numerical comparisons. Existing chunks are not rewritten.

1. Run status and sample at representative land, highland and ocean positions.
   Check `MATCH`, the saved profile fingerprint and model identifier.
2. Run north–south and east–west transects from the same origin as a comparison.
   Confirm expected/sample agreement and compare values with the old profile.
3. Check cave behavior, reopen the save, inspect newly generated biomes and compare
   generation responsiveness. Later placement hooks can affect final biome IDs.

Preview/profile synchronization, full sampler profiling and evidence-led placement
refinements remain follow-up work. Rain shadows, geographic moisture modelling,
new hydrology coupling, seasons and automatic tropical-palette expansion remain
deferred. Streams flow and bank rules, RU configuration, and active pack files
were not edited.
