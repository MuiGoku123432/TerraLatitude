# Development handoff

## Current state — 0.1.1-test

Standalone TerraLatitude repository extracted from the F&F Terra instance's
`packDev/compat-patches/ftf-naturaltemperature-altitude` prototype. The original
prototype remains available there as a reference; this repository is the home for
future development. The initial extraction does not alter active pack files.

Working baseline: final-sample altitude correction; unchanged cooling algorithm;
renamed mod/package/config; relocatable offline build; exact dependency pins;
model tests and static verification. No game or world was launched.

## Next work, in order

1. **User baseline world testing.** Compare same-seed fresh worlds with and without
   this addon. Check cooler mountains, latitude progression, caves, loading, and
   generation cost. Record actual biome IDs, coordinates, preset, and configs.
2. **Raw-climate integration spike.** In the separate FreeTerraForged development
   project, design the explicit raw-climate/provider seam and inspect downstream
   region/cache behavior. In TerraLatitude, prove a scoped NT adapter can avoid
   duplicate application. Keep this work separate from the baseline release.
3. **Integrated temperature mode.** Reuse NT's modes with raw FTF regional input;
   apply one selected altitude model. Test zero influence/native output, partial
   influence, extreme heights, poles/looping, caves, and non-FTF dimensions.
4. **Climate diagnostics and calibration.** Add sample export/inspection before
   choosing new defaults; compare biome distribution and generation cost across
   seeds and presets. No exact Celsius or snowfall claim without an explicit model.
5. **Independent moisture blend.** Preserve NT moisture-band intent while retaining
   configurable FTF regional wet/dry variation. Audit region tags and replacement
   filters against the final paired climate result.
6. **World configuration and optional weather adapters.** Persist or fingerprint
   world settings, handle server/client expectations, and evaluate seasonal snow
   or player temperature separately.

Each step needs a reviewable implementation and appropriate offline checks. The
owner handles runtime world testing unless they explicitly request otherwise.

## Pack context recorded 2026-10-13

- Target F&F Terra: Minecraft 1.21.1; NeoForge 21.1.257; custom FTF 1.0.0007;
  NT 1.1.10; RU 0.6.2; Lithostitched 1.8.0.
- The prototype used preset sea level 62 and world height 528 as an example;
  runtime reads actual FTF preset levels rather than hard-coding them.
- American West is the main theme, with potential Mexican/Central American
  tropical additions still undecided. Current exclusions/replacements may prevent
  jungles even when climate is suitable. Do not silently enable biomes.
- Test with a fresh world. Previously generated chunks retain stored biomes.
- The original prototype was not installed by the altitude task. This extraction
  does not install or update either JAR in the active instance.

## Build and distribution boundaries

The offline build deliberately uses the exact installed, named Prism dependencies
rather than a speculative public FTF artifact. Machine-specific paths live only
in ignored build.local.toml. Source is MIT; dependency licenses remain their own.
There is no remote repository or published release set up by this extraction.
A standard Gradle development environment can follow once a reproducible supported
FTF dependency artifact/API is available; do not treat the current local classpath
builder as a portable public SDK.
