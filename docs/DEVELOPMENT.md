# Development handoff — 0.3.0-test

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

The input/composition split, diagnostic reuse and native FTF API integration are
implemented. [Results, reuse map and supported JAR pair](M1-NATIVE-INTEGRATION.md)
record source provenance and compiled-binary comparisons for both projects.
FTF `Climate` and `ClimateModule` were modified; `TemperatureShaping` was added.
TerraLatitude no longer has private FTF accessors or its own coordinate-warp math.
Schema-1 results and the existing ownership/injector contracts remain unchanged.
No artifact was installed or world launched. Offline progress does not complete
all runtime/performance requirements of M0/M1.

## Next work

The [integration specification](INTEGRATION-SPEC.md) is the scope and acceptance
reference for the next iteration:

1. Align the next TerraLatitude test version with the smaller API proposed in
   [FTF PR #301](https://github.com/ETcodehome/FreeTerraForged/pull/301). Keep the
   schema-1 double-rounding compatibility calculation in the addon, preserving
   existing world output; use FTF for raw inputs, warping and height normalization.
   Pin and document the resulting build pair. Do not assume the current 0.3.0 JAR
   works with the PR's float-only altitude helper or an unmodified FTF release.
2. M0/M1: owner-test the matched pair, verify live ownership and unchanged generation,
   then measure cold/warm sampler cost and generation responsiveness. The climate
   source reuse and offline arithmetic work are complete; whole-source artifact
   provenance is not claimed. Optimize only where profiling identifies a useful gain.
3. M2: investigate a bounded preview/profile integration after the generation path
   is verified. Defer it if it requires broad lifecycle or cached-cell rewrites.
4. M3: refine existing biome-placement rules where test evidence shows gaps.
5. M4: use existing Streams bank profiles for local ecology without changing its
   flow simulation or introducing new hydrology inputs.

Reuse existing FTF capabilities first. Where an integration boundary is missing,
prefer a small reusable change in FTF itself. TerraLatitude should own NT
composition, saved integration settings, and diagnostics; retained duplicate logic
needs an explicit compatibility reason. See the specification's reuse policy.

Directional rain shadows, geographic ocean/moisture models, new Streams hydrology
inputs, seasons/weather, and automatic tropical-palette expansion are deferred.
Implementation and installation are separate from this specification update.

## Reference pack

Minecraft1.21.1, NeoForge21.1.257, FTF1.0.0007-climate-api.1 paired test build, NT1.1.10, RU0.6.2,
Lithostitched1.8.0. Runtime reads actual preset levels and fingerprints the full
preset; do not infer world height or sea level from an example. Common defaults:
temperature1.0, moisture0.5, transition radius128; saved NT influence multiplies
both strengths. No band distance, biome exclusion, or active instance file changes.

The first 0.1.x prototype remains in the original instance's packDev directory and
in repository history. This repository is the development home. Only one addon
version may be installed. Source is hosted at
[MuiGoku123432/TerraLatitude](https://github.com/MuiGoku123432/TerraLatitude).
No downloadable release has been published.
