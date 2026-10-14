# Shared climate implementation — 0.2.0-test

## Status against priorities 1–5

| Priority | Implemented | Remaining limitation |
| --- | --- | --- |
| 1. Shared climate | Paired temperature/moisture enter the owned FTF sampler and its provider/decorator path. | Native cached cell climate and FTF preview are unchanged; arbitrary feature consumers are not unified. |
| 2. Moisture controls | Independent temperature and moisture strengths; moisture retains native terrain shaping. | This is climate blending, not a rainfall/wind simulation. |
| 3. Rivers/coasts | Same output feeds the existing climate-filtered placement path. | Pack-disabled and globally replaced biomes remain unavailable; no new placements are injected. |
| 4. World settings/diagnostics | Persisted NT+TerraLatitude profile, preset fingerprint, status/sample/CSV commands, pack audit. | No client preview synchronization; full pack rules and all external mod settings are not frozen. |
| 5. Transitions | Optional deterministic five-tap smoothing of NT climate, with existing NT/FTF variation. | This does not guarantee an ecological transition biome exists in the installed palette. |

No world or game was launched. Live integration order and actual generated results
remain runtime validation work.

## Sampling and ownership

`IntegratedSamplerMixin` injects at the head of `Climate.Sampler.sample`, priority
2000, ahead of NT's default-priority cancellable injection. It cancels only for an
enabled, saved, FTF-owned Overworld sample belonging to the active server. It does
not call `sample` recursively and never temporarily changes NT global config.
FTF's existing outer sampler wrappers and later `SamplerDecoration` stages remain.

Ownership requires the world seed, WORLDGEN_EPOCH owner type, the underlying biome
and level-stem registry identities, and the actual Overworld generator identity.
FTF wraps RegistryAccess in a new frozen container, so wrapper identity is not used.
Underlying MappedRegistry instances and the selected generator retain identity.
This also rejects stale samplers from a previous same-seed server session.

ServerAboutToStart captures defaults/NT settings or loads an existing profile.
The first owned FTF sample binds the preset and completes the profile file before
publishing it to generation threads. Profiles are immutable during a session;
ServerStopped drops the server session. New disabled worlds bypass NT capture.

## Raw FTF inputs without mutating shared cells

`RawClimateAccess` uses FTF's cached-or-calculated terrain lookup once, then reads
the original regional temperature/moisture noise graphs via six Mixin accessors
on `ClimateModule`. It reproduces FTF's regional-edge and coordinate warping before
sampling those graphs. This is direct raw noise evaluation, not algebraic reversal
of clamped/quantized climate. It repeats climate-noise work but does not force a
second terrain generation pass or load chunks.

FTF's region/domain coordinates are geometric; installed bytecode computes them
independently of climate temperature/moisture. The owned selection path computes
the domain, obtains the climate point, and passes that point into provider and
selection-decorator stages. Native cached Cell temperature/moisture remain intact.
Preview and consumers reading those cells do not automatically see TerraLatitude.

## Composition

Let `n` be the saved NT influence (0..1), `t` and `h` the independent band strengths,
and `rawT/rawH` FTF's pre-altitude regional noise. NT's native calculators evaluate
both axes together using those inputs; their mode-specific regional contribution
is retained. Temperature is evaluated once and supplied to the humidity context.

```
NTpair = evaluate saved NT mode(rawT, rawH, position)
NTpair = optionally smooth its coordinate field over the configured radius
seaT = lerp(rawT, NTpair.temperature, n * t)
surfaceT = FTF elevation curve(seaT, predicted ground elevation, saved strength)
moisture = lerp(native FTF moisture, NTpair.humidity, n * h)
```

The FTF curve uses normalized inland elevation and its native above-ground gate.
It is applied once. Terrain below that gate receives no additional height cooling.
Humidity retains FTF's existing terrain shaping through its native input; no second
rain-shadow/coastal correction is layered on. Existing upstream global FTF behavior
is not replaced by an independent weather model.

Final changed axes are clamped and quantized once. Zero-strength axes preserve the
original quantized value; zero NT influence preserves both native axes exactly.
Continentalness, erosion, depth, and weirdness are unchanged by this addon.
Explicit randomized NT caves retain native NT influence/randomization and bypass
the surface blend/cooling/smoothing. Existing FTF cave policies still follow.

## Natural Temperature adapter

`NtSettings` captures all fields read by the installed built-in modes and binds
the owner's world seed. `NaturalClimateAdapter` constructs NT's public temperature
and humidity contexts and invokes its dependency calculators. Modes 0–10 are
supported; custom 101–105 cannot guarantee snapshot isolation and are rejected.

Some calculators read zone settings directly from static Config fields. Two
mixins redirect exactly those field reads to an exception-safe, nesting-safe
ThreadLocal snapshot. Outside the adapter scope, the real NT globals are used.
No global value is assigned by production code. Marker interfaces let modes that
need these redirects detect an omitted transform instead of silently reading globals.

The isolated parity harness compares against original installed WorldPainter
context/coordinate calculations with supplied density inputs. Test-only transforms
apply the actual compiled redirect handlers. This verifies numerical equivalence
and snapshot isolation, not the actual Mixin runtime or modpack startup.

## Transitions, rivers, and coasts

The filter weights the center by one half and four cardinal samples by one eighth
each. It smooths only NT's coordinate field, using the same local raw FTF inputs
at each tap. Radius zero bypasses it. Constant/linear fields are preserved and the
convex filter cannot overshoot its inputs. There is no extra random-noise layer.
FTF's biome-edge controls and NT's waves retain their original roles.

RU/Lithostitched climate predicates receive the resulting climate through FTF's
existing owned selection path. Cold/muddy/tropical river and coast choices remain
subject to all their other terrain, erosion, availability, and replacement rules.
No extra cooling derives from underwater depth; no wetland rainfall is inferred
merely from the presence of a river.

## Validation boundaries and next questions

- Test the actual Mixin application and full pack startup.
- Confirm sea-level, upland, mountain, cave, river, and coastal selection across seeds.
- Measure repeated regional-noise evaluation and five-mode-evaluation overhead.
- Compare operator CSV output with generated biome IDs and later pack replacements.
- Verify save/reopen persistence and intentionally invalid/mismatched profile handling.
- Decide whether a future upstream FTF hook should also update preview/cell consumers.
- Consider an optional biome profile only after explicitly deciding which exclusions
  to remove. Current jungle, mangrove, tropical-river, and ocean replacements persist.

Generation uses stable annual climate. Seasons must not move worldgen bands while
chunks are created. Snow, crop, survival-temperature, and weather adapters are later
work. True directional rain shadows need wind/upwind terrain modelling and a
performance budget; the existing FTF setting name alone does not establish that.

## Evidence

Integration is pinned to the installed FTF/NT hashes recorded by build.py. Static
checks inspect exact field/method descriptors, active mixins, target counts, callback
shape, and declared priorities. Reference behavior was inspected from those binaries.

Public documentation provides context, not proof about the custom FTF build:
[Natural Temperature](https://modrinth.com/mod/natural-temperature),
[NeoForge 1.21.1 configuration](https://docs.neoforged.net/docs/1.21.1/misc/config/),
[Lithostitched injectors](https://github.com/Apollounknowndev/lithostitched/wiki/Intro-To-Biome-Injectors).
