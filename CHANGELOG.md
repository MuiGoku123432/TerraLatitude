# Changelog

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
