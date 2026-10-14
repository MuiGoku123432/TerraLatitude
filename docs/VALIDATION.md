# Validation — TerraLatitude 0.1.1-test

Validated 2026-10-13 after extraction into its standalone development directory.

| Check | Result |
| --- | --- |
| Java 21 compilation against installed pinned APIs | PASS |
| Pure altitude-model tests | PASS; 514,294 assertions, including parameter sweeps |
| Package, metadata, dependency, source, and bytecode checks | PASS; 41/41 |
| Standalone settings and repeated-build checks | PASS; 9/9 |
| Two staged clean builds | Byte-identical |
| Rebuild and verification from final project directory | PASS; identical artifact |
| Minecraft / server / world launched | No |
| Installed into active instance | No |

Artifact: `dist/terralatitude-0.1.1-test-1.21.1.jar` (12,125 bytes).

SHA-256:

```
edfdcd00fb424e5165361c4257b6286ed7b2f5763abe51ba2543daba294dcd50
```

Local generated evidence is in ignored `reports/build.json`,
`reports/verification.json`, and `reports/reproducibility.json`. The latter records
staging-time settings/reproducibility checks; the first two were regenerated in
the final development directory. No staging location is needed to build.

The tests exercise the baseline altitude calculation. They do not validate the
proposed unified climate pipeline in CLIMATE-DESIGN.md, which is not implemented.

Static verification does not launch the Mixin runtime or establish full-pack
startup, successful runtime injection, actual biome distribution, weather/snow,
or world-generation performance. These remain user world-testing tasks.

The original `fnf_altitude` addon must not be loaded alongside TerraLatitude.
Metadata declares it incompatible to avoid applying altitude correction twice.
