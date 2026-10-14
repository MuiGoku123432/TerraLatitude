# Development handoff — 0.2.1-test

## Runtime correction

Owner-run 0.2.0 diagnostics on 2026-10-14 exposed the early-return ordering bug.
Explicit injector order 900 supersedes the previous reliance on mixin priority.
Status and transects now expose expected versus sampled climate. Ocean samples
use a quart-aligned height at/above the water surface rather than the seabed.
Existing profile schema is unchanged; previously generated chunks stay as they are.

## Implemented this iteration

- World-owned temperature/moisture composition using raw FTF regional noise and
  NT built-in modes0–10, followed by one native FTF altitude curve.
- Independent climate strengths and optional transition smoothing.
- Integration before FTF's owned biome provider/decorators, including existing
  RU/Lithostitched climate predicates for river/coastal choices.
- Immutable persisted world profiles, underlying-registry/generator ownership
  guards, preset fingerprinting, strict parsing, and atomic profile creation.
- Operator status, surface sample, and bounded north–south CSV transect commands.
- Read-only pack audit for disabled placements, replacements, and packaged predicates.
- Pure tests, bytecode/package checks, original-NT numerical parity fixture, and
  reproducible offline builds. Automated work does not launch a game or world;
  the owner supplies runtime test results.

## Next work

1. User tests the corrected build in a separate fresh FTF world. Confirm full pack startup, profile creation,
   `/terralatitude status`, sampled/generated biome agreement, caves, rivers, coasts,
   save/reopen consistency, and generation cost. Record seed, configs, preset, and
   profile fingerprint with results.
2. Evaluate biome frequency and transitions with exported transects across seeds.
   Adjust climate defaults from observations, not assumed Celsius mappings.
3. Consider a direct FTF API and preview/cell integration if needed. Current raw
   accessors deliberately avoid changing FTF source or shared terrain cells.
4. Decide on an optional American West + Mesoamerica ecology profile. The current
   pack's jungle/rainforest/tropical-river exclusions are reported but unchanged.
5. Explore client diagnostics or seasons/weather only after baseline world tests.

## Reference pack

Minecraft1.21.1, NeoForge21.1.257, FTF1.0.0007 custom build, NT1.1.10, RU0.6.2,
Lithostitched1.8.0. Target preset example sea level62/worldheight528; runtime reads
actual preset values and fingerprints the full preset. New common defaults:
temperature1.0, moisture0.5, transition radius128; saved NT influence multiplies
both strengths. No band distance, biome exclusion, or active instance file changes.

The first 0.1.x prototype remains in the original instance's packDev directory and
in repository history. This repository is the development home. Only one addon
version may be installed. There is no remote repository or published release.
