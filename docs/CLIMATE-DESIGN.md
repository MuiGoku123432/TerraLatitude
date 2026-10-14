# One climate calculation for latitude and terrain

Status: proposed next architecture, not implemented in 0.1.1-test.

## Why improve the prototype?

The current mod adjusts the already combined temperature at the return of
`WorldgenPlans$SamplerDecoration.sample`. That is a useful isolated baseline,
but does not know how much original FTF elevation cooling survived NT's blend.
Weighting the added cooling by NT influence reduces this issue without fully
solving it. Lowering a final number cannot reconstruct discarded regional climate.

The intended landscape is warm valleys, cooler uplands, and cold high mountains,
with the elevation needed for colder biomes varying by latitude. Local wet and
dry areas should remain possible within a latitude band. Terrain geometry stays
with FTF; biome eligibility and replacements remain with the biome mods and pack.

## Verified evidence from the installed binaries

Reference versions: FTF 1.0.0007, NT 1.1.10, Minecraft 1.21.1.

| Component | Verified behavior | Design consequence |
| --- | --- | --- |
| FTF `ClimateModule.apply` | Computes raw temperature, passes it to `modifyTemp(height, raw)`, then maps the result into [-1,1]. | A pre-elevation temperature exists conceptually inside FTF. |
| FTF `ClimateModule.modifyTemp` | Raises clamped raw [0,1] temperature to `2^(normalizedElevation * altitudeCoolingStrength)`. | FTF already has elevation cooling; do not add a second full model. |
| NT `WorldPainter.modifyTemperature` | Blends its result with the original FTF temperature using configured influence. | Partial influence retains FTF's earlier cooling. |
| NT default tropical calculation | Also retains a 0.1 contribution of original temperature internally. | Even 100% NT influence is not necessarily independent of FTF temperature. |
| NT `TemperatureModeRegistry` / `TemperatureMode` | Provides registry lookup and `calculate(TemperatureContext)`. | Explore reusing NT modes instead of duplicating their latitude mathematics. |
| NT humidity calculation | Computes temperature again and supplies it to `HumidityContext`. | Temperature and humidity must be evaluated from coherent inputs. |
| FTF `ClimateQuerySemantics` | Exposes generator context and preset through the owned sampler. | Preserve the existing owner/dimension checks. |
| FTF moisture shaping | Has coastal and rain-shadow settings whose inspected math uses elevation. | This does not establish directional windward/leeward rainfall. |

Bytecode inspection locations: raw temperature in `ClimateModule.apply` around
206–239 (modifyTemp call at 227); final NT blend around 119–140; retained tropical
input in `TemperatureCalculator.calculateDefault` around 181–194; temperature
passed into humidity context around 755–770. These offsets describe the pinned
binaries only. They are investigation references, not injection contracts.

## Preferred implementation

A small explicit integration API in the user's FTF fork is preferable to a broad
mixin against its private climate internals. Such an API does not exist in this
release and no FTF source change was made for this project extraction.

The provider should expose the pre-elevation regional climate plus ground height,
sea level, seed, coordinates, preset identity, and dimension ownership at a stage
where a composed result can still propagate consistently to downstream consumers.
TerraLatitude can then act as an adapter to Natural Temperature's selected mode.

Conceptual temperature pipeline (one normalized domain throughout):

```
regional = FTF temperature before altitude
banded = NT selected mode, with regional supplied as its original-temperature input
seaLevelClimate = lerp(regional, banded, NT influence)
surfaceClimate = selectedElevationModel(seaLevelClimate, groundY, seaLevel)
```

The blend uses NT's configured influence exactly once; any regional contribution
inside the selected NT mode remains that mode's own behavior. Apply either FTF's
existing elevation curve or TerraLatitude's lapse-inspired curve, not both.
Mapping between FTF's [0,1] internal values, the [-1,1] climate axis, and quantized
Minecraft values must be explicit and quantization should occur only at output.

For disabled integration or zero NT influence, return the original FTF result
exactly. Do not route zero influence through a different altitude model. Keep the
current baseline mode available for comparison during development.

NT's normal sampler injection must be bypassed only for the exact owned sample
being supplied by the integrated provider. Never disable NT globally or modify
its global configuration during generation. Whether the adapter needs a narrow,
exception-safe per-thread scope or an explicit upstream hook requires a spike.
Reusing the mode registry alone does not suppress the existing injection.

Prefer FTF's curve as the first integrated comparison, since that keeps preset
altitude settings meaningful. Introduce the alternative lapse model only with
separate validation of influence transitions and height scaling.

## Humidity and regional consistency

First retain current humidity behavior as a documented baseline while validating
temperature. Then introduce an optional independent moisture blend using FTF's
regional moisture and NT's latitude moisture. NT humidity must receive the same
pre-elevation input/temperature convention as the paired temperature calculation;
do not silently mix old-temperature humidity with newly composed temperature.

The same chosen climate must reach both base biome selection and Lithostitched
filters. Audit FTF derived region classifications, cached cells, and feature
placement before moving the hook earlier. Changing a cell after a region label
was derived could leave inconsistent consumers. Do not mutate shared cached cells
in place or infer that temperature replacement alone updates all classifications.

Continentalness, erosion, depth, and weirdness should retain FTF's existing values.
Explicit NT randomized-underground behavior stays respected. Decide and test
surface-versus-cave composition separately, using predicted ground elevation,
not player height or arbitrary biome-query Y.

## What the spike must establish

1. Exact raw climate access and which stage precedes derived region classification.
2. An owner-scoped bypass of NT's sampler hook with safe nesting and cleanup.
3. Mode registry/context coverage of global modifiers, mode settings, offsets,
   looping, humidity options, and cave randomization for the pinned NT version.
4. Complete propagation of composed climate to base biomes and replacement filters.
5. Thread safety, context lifetime, cache consistency, and no extra terrain pass.
6. Continuous, deterministic behavior at influence, latitude, elevation, and cave
   boundaries; exact native FTF behavior at zero influence.

Do not reverse the final sample algebraically to recover raw FTF temperature:
clamping, quantization, blending, and other decorators make that unreliable.
Do not promise actual snowlines or biome names from a temperature threshold;
selection is multidimensional and pack replacement rules still apply.

## Separate later integrations

Per-world persisted climate settings and a configuration fingerprint would prevent
accidental changes between generation sessions; the current common config is not
world-scoped or automatically synchronized. Diagnostics should expose latitude,
raw temperature, altitude contribution, humidity, final climate, and biome ID.

Seasonal snow, weather, player warmth, cloud forests, true wind-driven rain shadows,
and physiological treelines need their own models/adapters and testing. Enabling
previously excluded jungle biomes is a pack configuration decision, not a benefit
that this mod can provide while those biomes remain excluded.

## Sources and version scope

- Installed reference binaries are pinned and their hashes appear in local build
  reports. Bytecode is the evidence for this custom FTF build and NT 1.1.10.
- [Natural Temperature's official description](https://modrinth.com/mod/natural-temperature)
  describes configurable bands and modes; it may cover versions newer than the
  installed one, so bytecode verification takes precedence for integration details.
- [NeoForge 1.21.1 configuration documentation](https://docs.neoforged.net/docs/1.21.1/misc/config/)
  describes common/server config behavior and mod-bus events.
