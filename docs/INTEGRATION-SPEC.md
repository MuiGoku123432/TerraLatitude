# TerraLatitude integration specification

Status: 0.3.0 native API integration is built and verified offline; see
[native integration, source reuse map and evidence](M1-NATIVE-INTEGRATION.md).
The earlier [0.2.2 foundation](M1-PROGRESS.md) is retained as historical evidence.
Full M0/M1 runtime/performance acceptance remains pending.
Updated: 2026-10-14. Baseline: TerraLatitude `0.2.1-test`, commit `762a41c`.

Improve the consistency and maintainability of the existing FreeTerraForged
(FTF) and Natural Temperature (NT) integration while preserving the tested world
climate. Prioritize shared inputs, altitude calculation, preview accuracy, and
targeted use of existing biome and riverbank placement tools.

Keep FTF responsible for terrain and biome selection, NT responsible for the broad
latitude pattern, and TerraLatitude responsible for their composition and saved
settings. Directional rain shadows and other new geographic climate simulations
are deferred. They are not dependencies of this specification.

## Decisions and scope

| Area | Next target | Boundary |
| --- | --- | --- |
| Altitude | Share FTF inputs and a clearly defined calculation contract; reduce duplicated access logic. | Preserve the existing curve and saved-world results. No new physical lapse-rate model. |
| Preview | Give generation, biome preview, and climate displays the same explicit profile and composition. | Do not claim all FTF cell consumers or other mods are unified. |
| Diagnostics | Make parity, profile identity, placement, and cost independently inspectable. | Predictions and stored chunk biomes remain distinct. |
| Biome variety | Audit and refine existing FTF/RU/Lithostitched placement rules. | No second biome-selection engine or automatic expansion of the tropical palette. |
| Riverbank ecology | Use Streams bank styles and placed features for suitable local vegetation and materials. | Preserve network planning, discharge, carving, and water flow. |
| Geographic climate | Retain as future research only. | No wind simulation, directional rain shadows, ocean-distance climate, or new Streams rainfall input in this iteration. |

Specification work does not install a JAR or alter the active server. Future builds
and installation are separate actions. The owner performs in-world acceptance
testing; development can compile, inspect dependencies, and run isolated tests.

## Reuse FTF before adding integration code

FTF is the primary implementation of terrain sampling, climate shaping, biome
selection, and preview infrastructure. TerraLatitude should remain a thin
integration layer for NT composition, saved integration settings, and diagnostics.
Inspect the local FTF source repository before designing replacement logic:
the local `FreeTerraForged` checkout.

Choose an implementation in this order:

1. Reuse an existing FTF API, calculation, input, or extension point when it meets
   the ownership, ordering, and compatibility requirements.
2. If the capability exists internally but is inaccessible, prefer a small,
   reusable extension in FTF itself. Keep FTF independent of NT and TerraLatitude;
   the integration supplies its own settings and composition through that boundary.
3. Retain isolated TerraLatitude compatibility code only when the pinned FTF build
   lacks the needed boundary or an existing world requires legacy arithmetic.
   Document the reason, supported builds, and condition for removing the fallback.

Before M1 implementation, record a reuse map for raw climate inputs, coordinate
warping, altitude shaping, owner lifecycle, sampler ordering, and preview displays.
For each area, identify the FTF source symbol, whether it can be reused unchanged,
the smallest required FTF extension if any, and the TerraLatitude logic it replaces.
Do not add another terrain sampler, noise graph, altitude model, biome selector,
or cache for a capability FTF can already supply under the required contract.

Record the inspected FTF commit and any working-tree changes; establish how that
source relates to the pinned runtime JAR. A newer checkout is not automatically
the source of the installed artifact. Check planned hooks against both the source
and the actual dependency build. Proposed API names and signatures remain open
until this review is complete.

For any FTF extension, verify unchanged standalone FTF behavior as well as the
integrated path, and record the supported FTF/TerraLatitude version pair. Existing
schema-1 output compatibility still applies: maximizing reuse does not authorize
silently switching those worlds to different rounding or climate behavior. Keep
any necessary legacy difference narrow and explicitly versioned.

## Verified baseline

The pinned environment is Minecraft 1.21.1, Java 21, NeoForge 21.1.257, the custom
FTF 1.0.0007 build, and NT 1.1.10. A version label alone is insufficient: preserve
the dependency hashes recorded by the build and the selected FTF preset identity.

The current implementation already provides:

- Paired temperature/moisture composition before FTF provider selection and
  subsequent biome-selection decorators, including supported RU/Lithostitched rules.
- Separate temperature and moisture strengths, multiplied by saved NT influence.
- FTF elevation cooling after latitude composition, and native terrain-shaped
  moisture blended without applying its terrain correction a second time.
- Optional five-point smoothing of the NT field, immutable world profiles,
  preset fingerprint checks, and operator diagnostics.

The owner's two server exports on October 14 contain 65 and 129 rows. The shorter
export repeats 65 rows of the longer export exactly, leaving **129 unique sampled
positions**. All sampled temperature and humidity values match the intended
composition. All 112 land samples have negative altitude corrections; the 17 ocean
samples use Y=64. At X=1224, Z=828, ground Y=335, the pre-altitude temperature is
0.2675026 and the integrated result is -0.1213. These are Minecraft climate units,
not Celsius. See [the evidence record](evidence/server-transects-2026-10-14.json).

These exports confirm the active diagnostic sampler path at those positions.
Their biome IDs are generator predictions, not reads of stored chunk biomes.
They do not establish complete palette coverage, preview parity, cave behavior,
generation performance, or server stability.

FTF already uses terrain-related climate coordinates, spatial biome regions,
selection decorators, climate-aware river candidates, and edge controls.
Conditional replacements can supply additional biome variants through existing
mechanisms. The next work extends these capabilities rather than replacing them.
[FTF selection model](https://github.com/ETcodehome/FreeTerraForged/wiki/biome-selection-and-compatibility-runtime),
[Lithostitched replacement rules](https://github.com/Apollounknowndev/lithostitched/wiki/Biome-Injector-Types).

## Altitude audit and required improvement

### What is already coupled to FTF

`RawClimateAccess` obtains FTF's terrain cell, uses its `Levels` object and
`getNormalizedInlandElevation(cell.height)`, and checks `cell.height > levels.ground`.
`WorldProfiles` captures the selected preset's `altitudeCoolingStrength` and rejects
a changed preset fingerprint. The cooling calculation uses FTF's normalized terrain
height, not the player's Y or the diagnostic sample Y.

The conceptual surface calculation is:

```text
latitude climate = NT(raw FTF regional climate, position, saved NT settings)
pre-altitude T  = blend(raw FTF T, smoothed latitude T, saved temperature influence)
e              = clamp(FTF normalized inland elevation, 0, 1)
surface T      = 2 * ((clamp(pre-altitude T, -1, 1) + 1) / 2)^(2^(e * strength)) - 1
```

The last step applies only above FTF's ground threshold and when integrated
temperature is active. That threshold and the normalized elevation calculation are
different inputs; do not replace both with an assumed `Y > 62` rule. The diagnostic
surface height is separately rounded upward to the quart grid at or above terrain
and sea level. It is not the source of the altitude correction.

### Baseline duplication and current resolution

The 0.2.1/0.2.2 baseline mirrored the short FTF altitude formula and reconstructed
regional coordinates using private-field accessors. Version 0.3.0 instead calls
the paired FTF raw API and explicit-input elevation helper. Height normalization
continues to call FTF directly. Raw noise still needs reevaluation after the terrain
lookup; this release adds no raw-value cell cache and makes no throughput claim.

Calling FTF's existing private `modifyTemp` directly is not the desired shortcut:
the pinned method reads `PresetManager.PM.climateSettings`, a global preset value.
An integration helper must accept the owner-specific saved strength explicitly.

The schema-1 integration contract uses double arithmetic while native FTF uses
intermediate floats. The new FTF helper explicitly retains both contracts.
An isolated comparison of 1,286,285 synthetic cases found 69,531 different final
quantized results, with a maximum difference of one unit, or 0.0001 climate unit.
This reconstructs the inspected FTF arithmetic; it does not execute FTF's runtime
method or demonstrate a gameplay defect. It establishes that replacing the helper
is not automatically behavior-preserving. See [the arithmetic record](evidence/altitude-arithmetic-2026-10-14.json).

### Altitude requirements

| ID | Requirement | Acceptance |
| --- | --- | --- |
| ALT01 | Separate acquisition of FTF inputs from pure climate composition. Consume an already computed cell when the caller has one. | Cached and standalone queries agree; no extra terrain lookup is introduced when equivalent inputs are supplied. |
| ALT02 | Define the curve's input units, ground gate, normalized elevation, strength, rounding, and final quantization. | An independent dependency-based characterization covers gates, extrema, different sea levels/world heights, and representative strengths. |
| ALT03 | Apply integrated temperature and its altitude correction once. | Tests distinguish NT-only, integrated, native pass-through, and deliberately double-applied results. |
| ALT04 | Preserve the `0.2.1` calculation for existing schema-1 worlds. | The old profile and new implementation produce identical quantized outputs for the baseline fixtures and sampled positions. |
| ALT05 | Avoid mutable global preset reads in the new calculation boundary. | Concurrent owners with different seeds, presets, and strengths cannot affect each other's output. |
| ALT06 | Measure repeated climate work before adding caches. | Any cache is bounded, keyed by the relevant owner/profile/position inputs, and released with its owner. |

A pure FTF helper with explicit inputs is the preferred long-term boundary if a
small upstream change is practical. Its name and API are not established yet.
Retain a tested compatibility adapter where the installed FTF build cannot provide
that contract. Removing a few mirrored formula lines alone is not a sufficient
reason to change terrain, climate distribution, or deployment dependencies.

## Integration architecture

### Input and result contracts

Use an immutable request containing the owner's seed and identity, preset/profile
identity, aligned coordinates, raw regional temperature/moisture, native climate
target, FTF terrain elevation/levels, and saved integration settings. A precomputed
cell may supply inputs, but the result must not retain a mutable pooled cell.

Return a structured result containing native climate, latitude climate,
pre-altitude temperature, elevation correction, integrated climate, and the
algorithm/profile identity. Diagnostics should consume this result rather than
reconstructing a separate version of the calculation.

Preserve continentalness, erosion, depth, and weirdness at the integration boundary.
FTF's legitimate later policies retain their existing authority over those values.
Keep native and integrated temperature/moisture distinguishable wherever both are
needed. Do not overwrite shared cached cells after publication.

### Placement in the FTF pipeline

FTF exposes ordered sampler decoration and owner-scoped provider machinery, but
those interfaces are not proven drop-in replacements for the present hook. The
current injector order 900 prevents NT's order-1000 callback from cancelling first.
Moving only the composition to a later decorator could leave NT applied earlier
and cause double processing.

Before changing the interception point, demonstrate the full order of raw sampling,
NT handling, TerraLatitude composition, FTF sampler decorators, provider selection,
biome decorators, and final surface/cave policy. If an existing FTF extension point
cannot preserve that order, retain the working interception point and improve its
input/composition boundaries first. A new upstream hook must justify its necessity
with this evidence.

A cell-level hook must compose from its supplied inputs. It must not call
`RawClimateAccess.sample()`: that performs a terrain lookup and could recurse through
terrain generation back into climate. No climate path may request chunks, wait for
Streams networks, or read a neighboring chunk's generated biome.

### Ownership and pass-through

- Enabled integration with NT influence zero returns native FTF climate exactly.
  A zero axis strength preserves that axis's original quantized value.
- Disabled or unowned integration leaves the existing upstream FTF/NT path alone;
  this is distinct from enabled integration with zero influence.
- Explicit NT underground randomization keeps its saved threshold and original
  shared influence behavior. FTF's cave policy still runs at its established stage.
- Keep world/dimension/generator and registry identity checks. Preview support must
  add an explicit preview-owner binding, not weaken server ownership globally.
- Settings and results are deterministic across request order and worker threads.
  No production path temporarily assigns NT or FTF global settings.

## Delivery targets

### M0 Baseline and characterization

Archive the runtime evidence and exact dependency/profile identities. Add focused
characterization for raw coordinate acquisition and altitude arithmetic, including
region edges, mountain-chain handling, inland elevation, and ocean/ground gates.
Retain the old arithmetic as the comparison oracle for compatibility; separately
characterize native FTF arithmetic instead of assuming the two are bit-identical.

Capture baseline sampler cost with a fixed coordinate corpus, warmup, cold/warm
cache conditions, allocation observations, and repeated runs. The existing server
transect is one fixture, not a sufficient global sample set.

**Exit:** an FTF source reuse map with source/artifact provenance, recorded
arithmetic/ownership contracts, reproducible comparison inputs, and a baseline
suitable for evaluating M1. No climate default changes.

### M1 Altitude and raw-input integration

Implement ALT01–ALT06 using the M0 reuse map and the implementation priority above.
Prefer existing FTF capabilities or a small reusable FTF extension over maintaining
parallel logic in TerraLatitude. First split input acquisition from composition.
Then replace private access or duplicate work only where the new path proves
equivalent. A supported FTF raw-climate interface
should expose the required inputs directly rather than force TerraLatitude to
reverse clamped or already cooled values.

**Exit:** schema-1 output compatibility, unchanged NT-once/altitude-once behavior,
owner isolation, and no added chunk/network dependencies. Quantify performance
before claiming an optimization. A new arithmetic model is not required to complete
this milestone.

### M2 Preview and diagnostics

Support FTF world-creation preview with an immutable snapshot of the selected seed,
preset, NT settings, and TerraLatitude settings. Transfer or verify the same snapshot
when that world is created. An existing server world's preview requires an explicitly
supplied saved profile; client defaults must never stand in silently for that profile.
Automatic client/server profile synchronization is outside this iteration.

Cover both biome preview selection and temperature/moisture display layers. The
pinned display modes directly read native cell fields, so making only the biome
resolver use TerraLatitude is insufficient. An immutable display result or sidecar
is acceptable; mutating shared cached terrain cells is not. Preserve cancellation,
owner disposal, and cache invalidation when seed, preset, or profile changes.

Extend diagnostics with:

- Profile and algorithm identity; native, band, pre-altitude, integrated, and final
  decorated temperature/moisture; normalized elevation and applied strength.
- Bounded east–west as well as north–south transects, preserving the current sample
  cap unless a separately measured limit justifies a change.
- An optional comparison to already loaded chunk biomes, reporting `not loaded`
  without force-loading chunks. Keep predicted and stored results in separate fields.
- Stage-specific mismatch reporting. A later legitimate sampler decorator must not
  be mislabeled as an NT ordering failure.

**Exit:** matching integrated outputs and biome selection for identical preview and
generation inputs at the corresponding surface coordinates, including oceans,
region edges, and mountains. Temperature/moisture displays show the integrated
values. Unsupported preview combinations report unavailable rather than native
climate presented as integrated climate.

### M3 Biome placement refinement

Inventory the active world's datapacks, RU settings, Still Life injectors, biome
tags, region weights, and replacement chains. The existing offline audit is a
starting point; candidate counts alone do not establish reachability or frequency.
FTF's nearest-candidate climate selection must not be confused with a hard climate
eligibility filter.

Use existing placement mechanisms for narrowly demonstrated gaps: inappropriate
terrain targets, missing intermediate variants, or unintended exclusion/replacement.
For every proposed change, record the original rule, new rule, intended terrain and
climate, eligible alternatives, priority, and expected fallback. Use real available
biomes and preserve other provider domains.

The American West palette remains the default scope. Restoring jungle, mangrove,
rainforest, or tropical-river coverage is a separate palette decision. Preserve
Hybrid Aquatic's authored distinction between cold deep reefs and tropical reefs;
the cold northern deep-reef samples do not establish tropical leakage.

**Exit:** an auditable set of targeted rule changes with before/after predictions,
no unresolved references or unintended candidate removal, and owner-reviewed
generated examples. No generalized ecological adjacency engine is required.

### M4 Streams bank profiles

Use Streams' existing biome/tag selection, bank rings, placed-feature definitions,
and materials to refine local riverbank appearance. Validate selected feature IDs
against the installed palette. Keep dry-region greenery local to suitable banks;
do not raise regional humidity to simulate access to river water.

FTF has river/wetland terrain and climate-aware river selection, while Streams has
its own channels and decoration rules. Do not assume an FTF river mask identifies
every Streams channel. Follow Streams' own bank placement mechanism for this work.
The installed schema references are
`config/streamsreflowing/bank_features/README.md` and the adjacent bank-style
documentation in the reference instance. Verify them against the pinned Streams
JAR when authoring entries.

**Exit:** valid bank definitions and biome/tag matches, suitable owner-tested dry
and wet river examples, and no changes to network/discharge/carving settings or code.
This milestone can proceed after M0 without waiting for an upstream FTF API.

## Validation and release criteria

| Area | Required evidence |
| --- | --- |
| FTF reuse | Completed source reuse map; any retained duplicated logic has a specific compatibility reason. FTF extensions preserve standalone behavior and declare the supported dependency pair. |
| Legacy compatibility | Identical quantized T/H for schema-1 fixtures across cold/warm lookups, repeat calls, and query order. Preserve the other four target coordinates at the integration boundary. |
| Independent altitude check | Compare against the pinned dependency's characterized gate/normalization/float sequence and report differences separately from the legacy compatibility result. |
| Ownership | Separate worlds and preview requests with different profiles; stale-owner rejection; no cross-dimension or vanilla-generator interception. |
| Ordering | Prove one NT composition and one altitude application through the actual transformed path; pure-helper self-comparisons alone cannot establish this. |
| Preview | Equivalent profile/preset/seed gives equivalent climate and biome selection; climate displays agree; cancelled or superseded requests cannot publish stale results. |
| Performance | Compare the same fixed corpus and cache conditions over at least five measured runs after warmup. Target no more than 5% median batch-time regression; if normal variance exceeds that threshold, report the benchmark as inconclusive and improve the measurement. This is a proposed budget, not an achieved result. |
| Cache lifecycle | Bounded growth under a broad coordinate scan and release on owner disposal. Do not cache across different settings, algorithm versions, or worlds. |
| Pack behavior | Check biome rules and bank definitions independently from climate math. Validate surface, river/coast, and cave cases in owner-run tests. |
| Persistence | Reopening a saved profile preserves climate; incompatible seed/preset/model inputs produce a clear diagnostic rather than silently changing generation. |

Use the existing Java tests, package/bytecode checks, NT parity fixture, sampler
ordering fixture, and pack audit where applicable. Add tests for the new contracts;
do not present a synthetic test as proof of full-pack runtime behavior.

The owner runs Minecraft/server tests using a separate fresh comparison world or
save copy. Record seed, preset/profile identities, mod/data manifests, coordinates,
and results. Existing chunks are not rewritten when a new algorithm or placement
rule is enabled. Sampler success does not resolve unrelated server crashes.

## Profile and migration policy

Treat a schema-1 profile without an algorithm identifier as the `0.2.1` compatibility
model. Do not rewrite it merely to add bookkeeping. If a future schema needs an
explicit algorithm identifier, retain schema-1 reading and deterministic mapping
to the old model.

A change to arithmetic, coordinate warping, moisture composition, or biome rules
can affect newly generated terrain even when differences are small. Such changes
must be explicit and versioned, with old and new results compared. Do not silently
upgrade an existing world to native-float altitude arithmetic or new climate defaults.
The next iteration's default is behavior preservation.

Record relevant pack-rule fingerprints with validation results. A TerraLatitude
profile freezes its own settings and checks the FTF preset; it does not freeze all
external datapacks or biome replacements.

## Deferred features

Complexity rule: defer features comparable in scope to directional rain shadows.
This includes new geographic simulations, cross-mod hydrology coupling, broad
cached-cell rewrites, or major reconstruction of FTF's execution pipeline. Prefer
bounded reuse of existing extension points. Preview work proceeds only if source
review establishes a contained integration; a broad preview/lifecycle rewrite is
outside the current pass.

- Directional rain shadows, wind, ocean-distance climate, and ocean currents.
- Feeding continuous TerraLatitude moisture into Streams discharge or width.
  Streams currently reads biome rainfall; mapping climate humidity to rainfall
  would require a new calibrated contract and could change river networks.
- Seasons, weather, snow/ice behavior, crops, and player survival temperature.
  These have separate consumers and cannot be assumed to follow biome-selection T.
- A replacement biome engine, guaranteed biome adjacency sequences, or automatic
  expansion into a South/Central American palette.
- Broad rewriting of native cached climate for every feature consumer.

Revisit a deferred item only after the core targets have measured benefits and
there is a concrete landscape problem it would solve. No deferred feature blocks
M1–M4.

## Evidence and implementation references

- [Current climate design](CLIMATE-DESIGN.md), [validation](VALIDATION.md), and
  [development handoff](DEVELOPMENT.md) describe the existing implementation.
- Source boundaries: `RawClimateAccess`, `RawClimateMath`, `ClimateMath`,
  `IntegratedClimate`, `WorldProfiles`, `ClimateCommands`, and the sampler mixins.
- [Server evidence](evidence/server-transects-2026-10-14.json) records original CSV
  hashes and the saved profile identity. [Altitude arithmetic evidence](evidence/altitude-arithmetic-2026-10-14.json)
  distinguishes numerical characterization from runtime verification.
- [Feasibility evidence](evidence/integration-feasibility-2026-10-14.json) records
  inspected dependency hashes, relevant binary methods, and research conclusions.
- [FTF climate controls](https://github.com/ETcodehome/FreeTerraForged/wiki/Climate-Settings)
  describe map/edge settings; [hydrology](https://github.com/ETcodehome/FreeTerraForged/wiki/hydrology-and-shore-geometry)
  distinguishes river, wetland, and coastline geometry. Those controls do not
  establish a wind-driven rainfall simulation.

Installed binary behavior governs compatibility with the reference pack. Public
documentation provides architectural context and must not substitute for checking
the exact dependency build when implementing a hook.
