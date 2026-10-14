# TerraLatitude 0.3.1 — align with the submitted FTF API

Version 0.3.1 removes the dependency on the double-precision helper omitted from
[FTF PR #301](https://github.com/ETcodehome/FreeTerraForged/pull/301). The addon uses
FTF's public raw sampling and existing height/owner APIs. Its small schema-1
altitude calculation stays local to preserve established rounding.

`ClimateMath.class` is byte-identical to the compiled 0.2.1 version. The independent
legacy fixture also retains that original class, so the numerical comparison does
not accidentally share a new implementation on both sides. FTF's raw API continues
to own edge displacement and warping; the removed private-field hooks remain absent.

## Exact test pair

- TerraLatitude: `terralatitude-0.3.1-test-1.21.1.jar`, 60,299 bytes;
  SHA-256 `2f914fbfa74c3226f27fd6ad92bd1a92ceafad1119622df3344aca436ae64d17`.
- FTF: NeoForge build from PR #301 commit
  `2228f6c57f7457a4a0d516f9e5983627c3087ffd`, `freeterraforged-1.0.0010.jar`;
  SHA-256 `60f40abb32b32ebbef3780b57ac5de1340c1192642b4ff93f5a1a6fd9cbf69f0`.
- Minecraft 1.21.1, Java 21, NeoForge 21.1.257 and Natural Temperature 1.1.10.

The FTF JAR is the actual full Gradle artifact built for the upstream PR, containing
only its native float altitude helper. No excluded helper was reintroduced and
no new FTF source change was needed. `build.local.toml` supplies its path without
installing it. Source and evidence are published; dependency JARs remain local.

FTF's PR keeps its base version label, so ordinary FTF 1.0.0010 can have the same
label without the new API. The builder pins the tested checksum. At startup,
TerraLatitude verifies the public raw method, result type and float accessors before
registering config/lifecycle handlers, and reports a clear PR/API requirement when
those members are absent. This checks the API shape; it does not hash the entire
FTF artifact at runtime or guarantee compatibility with arbitrary same-version mods.

## Offline checks

- 60/60 static package and bytecode checks, including an explicit check that the
  selected FTF artifact does **not** provide `applyNormalized` and the addon does
  not link to `TemperatureShaping`.
- 12,672 comparisons / 136,580 assertions against compiled TerraLatitude 0.2.1,
  including exact output/intermediates, schema serialization and concurrent profiles.
- 252 populated-cell cases / 2,035 assertions using the new FTF dependency.
- 191,274 original-NT parity assertions, 41 injector-order assertions, five ordinary
  Java test programs, and 10 pack-audit tests.
- API guard accepts the actual PR artifact and clearly rejects the original FTF
  binary without the raw API. This invokes the guard directly; it is not a complete
  NeoForge startup test.

[Evidence](evidence/pr-api-compatibility-0.3.1.json) records hashes and results.
`build.py`, `verify.py` and the existing NT/composition/sampler-order tools are the
standard checks. The added API guard comparison is reproducible with:

```sh
python3 tools/test_ftf_api.py --unsupported-ftf-jar /path/to/original-ftf-1.0.0007.jar
```

The build must have run first. Use the original pinned non-API FTF JAR as the
negative fixture; do not install or launch it for this test.

## Runtime scope

No game, server, save, installed JAR or pack config was changed. The climate/profile
calculation is preserved by supplied-input checks. The FTF PR uses a newer upstream
base than the old custom 1.0.0007 test pair and excludes unrelated fork PRs such as
Biome Replacer compatibility (#291). These tests do not establish identical terrain
or full pack compatibility across those FTF builds.

The next runtime pass should use an intentional matched test setup, check status
and transects, caves and save/reopen behavior, then measure generation cost. Preview
and placement refinements follow once that path is validated. Advanced climate and
hydrology changes remain deferred.
