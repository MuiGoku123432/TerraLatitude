# Offline validation — TerraLatitude 0.2.0-test

Status: compilation, ordinary tests, pinned-bytecode checks, NT adapter parity, and reproducibility passed. No Minecraft client, dedicated server, or world was launched.

Artifact: `dist/terralatitude-0.2.0-test-1.21.1.jar` — 54,183 bytes, 32 compiled addon classes.
SHA-256: `c32c4871b9ee4e1eb211fc14a25dfcdde0f32449b512a3323456153972b65325`.

## Checks performed

- Java 21 compilation against the pinned installed Minecraft 1.21.1, NeoForge 21.1.257, FTF 1.0.0007, and NT 1.1.10 dependencies.
- All four ordinary test programs passed: altitude baseline, integrated climate math, world-profile serialization/validation/file handling, and raw climate coordinate math. Assertion counts include parameter sweeps and are not counts of distinct test cases.
- 49 static package and bytecode checks passed. These cover the exact sampler descriptor and declared priority, six raw FTF accessor fields, the four active mixins, dependency hashes, metadata, source fingerprints, and downstream FTF selection calls.
- The NT parity fixture passed across built-in modes 0–10. Its 24 compiled redirect handlers were exercised against 68 temperature and 84 humidity field reads in isolated transformed dependency fixtures. Nested scopes, exceptional cleanup, thread isolation, saved zone settings, and unsupported custom modes were checked.
- Two clean Java/test builds produced identical JAR bytes.

## Runtime limits

These checks do **not** prove that the actual Mixin runtime applies these hooks, that injection ordering wins against every mod, or that the full pack starts. The parity harness explicitly uses isolated bytecode fixtures and does not bootstrap Mixin or Minecraft.

In-world testing still needs to assess biome distribution, river/coast outcomes, cave behavior, world-profile lifecycle and command access, actual generation cost, and visible transitions. FTF's existing preset preview reads native cached cell values; TerraLatitude diagnostics report the composed climate used by the addon.

Machine-readable evidence is generated under `reports/`: `build.json`, `verification.json`, `nt-adapter-parity.json`, and `reproducibility.json`. The build report records all production and offline test inputs, and verification compares them with the current files.

The read-only pack audit and all 10 parsing/inventory tests also passed. Audit results describe current configuration and packaged predicates, not reachable biome frequency. The active pack may change independently of this repository; rerun the audit when evaluating a new test instance.

The final development-directory rebuild produced the identical artifact hash above and passed all 49 static checks. The new addon is not installed in the active Prism instance.
