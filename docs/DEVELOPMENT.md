# Development handoff — 0.3.1-test

## Previous runtime correction

Owner-run 0.2.0 diagnostics on 2026-10-14 exposed the early-return ordering bug.
Explicit injector order 900 supersedes the previous reliance on mixin priority.
Status and transects now expose expected versus sampled climate. Ocean samples
use a quart-aligned height at/above the water surface rather than the seabed.
Existing profile schema is unchanged; previously generated chunks stay as they are.

## Existing integration

- World-owned temperature/moisture composition using raw FTF regional noise and
  NT built-in modes0–10, followed by one FTF-based altitude curve using schema-1 arithmetic.
- Independent climate strengths and optional transition smoothing.
- Integration before FTF's owned biome provider/decorators, including existing
  RU/Lithostitched climate predicates for river/coastal choices.
- Immutable persisted world profiles, underlying-registry/generator ownership
  guards, preset fingerprinting, strict parsing, and atomic profile creation.
- Operator status, surface sample, and bounded north–south/east–west CSV transect commands.
- Read-only pack audit for disabled placements, replacements, and packaged predicates.
- Pure tests, bytecode/package checks, original-NT numerical parity fixture, and
  reproducible offline builds. Automated work does not launch a game or world;
  the owner supplies runtime test results.

## Confirmed server sampling

The 2026-10-14 server exports match expected temperature and humidity at all 129
unique positions; all 112 land samples show altitude cooling. The shorter export
is an exact subset of the longer one. This confirms the sampled path and does not
establish preview parity, stored chunk biomes, cave behavior, or performance.
See [validation and evidence](VALIDATION.md).

## Native integration progress

Version 0.3.1 now uses only the public raw API actually proposed in FTF PR #301.
[Compatibility evidence and the exact build pair](PR-API-COMPATIBILITY.md) record
its dependency pin, original-rounding parity and supported/unsupported API checks.
TerraLatitude retains the small schema-1 altitude calculation locally, with bytecode
identical to 0.2.1. FTF remains authoritative for regional inputs, warping and terrain
height normalization. No private field accessors or coordinate duplication returned.

No artifact was installed or world launched. Runtime startup, live ownership and
performance acceptance remain pending. The earlier [native integration record](M1-NATIVE-INTEGRATION.md)
describes the superseded 0.3.0 local test dependency.

## Next work

The [integration specification](INTEGRATION-SPEC.md) is the scope and acceptance
reference for the next iteration:

1. M0/M1: owner-test the matched pair, verify live ownership and unchanged generation,
   then measure cold/warm sampler cost and generation responsiveness. The climate
   source reuse and offline arithmetic work are complete; whole-source artifact
   provenance is not claimed. Optimize only where profiling identifies a useful gain.
2. M2: investigate a bounded preview/profile integration after the generation path
   is verified. Defer it if it requires broad lifecycle or cached-cell rewrites.
3. M3: refine existing biome-placement rules where test evidence shows gaps.
4. M4: use existing Streams bank profiles for local ecology without changing its
   flow simulation or introducing new hydrology inputs.

Reuse existing FTF capabilities first. Where an integration boundary is missing,
prefer a small reusable change in FTF itself. TerraLatitude should own NT
composition, saved integration settings, and diagnostics; retained duplicate logic
needs an explicit compatibility reason. See the specification's reuse policy.

Directional rain shadows, geographic ocean/moisture models, new Streams hydrology
inputs, seasons/weather, and automatic tropical-palette expansion are deferred.
Implementation and installation are separate from this specification update.

## Reference pack

Minecraft1.21.1, NeoForge21.1.257, FTF PR #301 at 2228f6c5 (version1.0.0010), NT1.1.10, RU0.6.2,
Lithostitched1.8.0. Runtime reads actual preset levels and fingerprints the full
preset; do not infer world height or sea level from an example. Common defaults:
temperature1.0, moisture0.5, transition radius128; saved NT influence multiplies
both strengths. No band distance, biome exclusion, or active instance file changes.

The first 0.1.x prototype remains in the original instance's packDev directory and
in repository history. This repository is the development home. Only one addon
version may be installed. Source is hosted at
[MuiGoku123432/TerraLatitude](https://github.com/MuiGoku123432/TerraLatitude).
No downloadable release has been published.
