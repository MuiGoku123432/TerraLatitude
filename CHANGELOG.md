# Changelog

## 0.3.0-test — 2026-10-14

- Require the paired FTF 1.0.0007-climate-api.1 API test build.
- Replace six private-field accessors and copied coordinate warping with FTF raw sampling.
- Delegate altitude to FTF’s explicit-input helper while preserving schema-1 double arithmetic.
- Keep owner guards, injector order, NT snapshot behavior and climate defaults unchanged.
- Verify native FTF behavior against the original compiled binary and freeze the original
  TerraLatitude altitude bytecode as an independent compatibility oracle.
- Add an explicit build-time FTF path; no instance or server installation.
- Preview, full sampler timing, and advanced climate/hydrology remain deferred.

## 0.2.2-test

- Separate supplied-input climate composition from owned FTF world access.
- Add immutable snapshots from populated FTF cells; reuse surface-diagnostic raw
  inputs without adding a terrain model, cache, or chunk request.
- Preserve schema-1 and 0.2.1 numerical behavior; retain the existing sampler hook.
- Add optional `east_west` transects and climate/profile/elevation CSV metadata.
- Add a compiled-0.2.1 compatibility fixture and native FTF cell-input checks.
- FTF source/API changes and preview integration remain pending; advanced features
  comparable in complexity to rain shadows are deferred.


## 0.2.0-test — 2026-10-13

- Compose raw FTF regional climate with world-snapshotted NT temperature/moisture,
  then apply one native elevation curve before owned biome placement.
- Add independent moisture strength and optional transition smoothing.
- Persist world settings and preset identity; add ownership and strict parsing checks.
- Add operator climate diagnostics and read-only pack restriction audit.
- Reuse NT built-in modes0–10 with scoped configuration redirects; test parity
  against original NT calculations without launching Minecraft.
- Retire the old active altitude-return injection. Original prototype classes are
  retained as inactive reference code; only the integrated hook is registered.
- No active pack installation, biome enablement, or world testing performed.

## 0.1.1-test — 2026-10-13

- Name the standalone project TerraLatitude; use `terralatitude` for its mod ID,
  package identity, mixin configuration, artifact, and config filename.
- Make the offline build independent of its old position inside a Prism instance.
- Pin the tested Natural Temperature binary as well as the custom FTF binary.
- Prevent loading together with the old `fnf_altitude` prototype.
- Preserve the original altitude algorithm and its tests.
- Record the design and staged development of a future single-pass climate model.
- No installation, game launch, or world testing performed.

## 0.1.0-test — original FTF Natural Temperature Altitude prototype

- Add configurable ground-elevation cooling after NT latitude bands and before
  FTF biome selection, preserving other climate coordinates.
- Compile and test offline against the F&F Terra instance's custom FTF 1.0.0007
  and Natural Temperature 1.1.10.
