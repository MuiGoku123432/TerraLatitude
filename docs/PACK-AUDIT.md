# Offline pack climate audit

Run with Python 3.11 or newer from this repository:

```sh
python3 tools/audit_pack.py --instance "/path/to/PrismLauncher/instances/YourInstance"
python3 -m unittest discover -s tests -p 'test_audit_pack.py' -v
```

If `python3` is the macOS system Python 3.9, select a newer installed Python
explicitly. The instance can also come from `TERRALATITUDE_INSTANCE` or the
`instance` setting in ignored `build.local.toml`. An explicit argument wins.

The audit reads the instance's Biome Replacer and Regions Unexplored configs,
then discovers active RU and Still Life West JARs by mod metadata. It inventories
packaged biome definitions and Lithostitched biome injectors. Temperature and
humidity conditions are retained as authored, including unfamiliar nested forms.
It does not extract or copy upstream classes, assets, or complete biome definitions.

The command prints a short summary and writes `reports/pack-audit.json` with input
hashes, raw predicates, replacement flags, excluded RU IDs, and parsing issues.
Use `--output /path/outside/the/instance/report.json` to select another report.
Pack inputs are read-only: report paths inside the audited instance are refused.
No game or world is launched. Exit 0 means the requested inputs were inspected;
exit 1 means the report has incomplete evidence; exit 2 means the command failed.

## Interpreting the report

**Static candidate counts are not reachable-biome counts or spawn frequencies.**
They combine explicitly enabled RU configuration entries with exact biome IDs
named by packaged injectors, deduplicated across those sources. These counts
include special placements and dimensions other than the Overworld. They omit
vanilla-only entries and other mods. They do not evaluate climate ranges, terrain,
tags, region weights, injector ordering, world datapacks, or later replacements.

An unconditional replacement flag means an exact `source > replacement` rule
exists inside its declared dimension. It does not prove that the source biome is
impossible through every generation path. Unknown/unscoped syntax is reported,
not guessed. Rule chains are not simulated. The climate-family labels are useful
name-based hints, not a substitute for biome tags or botanical classification.

RU's installed JAR currently has no packaged biome injector JSON resources;
its configuration is inventoried separately. This is not an assertion that RU
has no runtime injections. A packaged biome definition likewise does not prove
that a corresponding placement is enabled.

The current pack report exposes the existing jungle-to-redwood, mangrove-to-marsh,
tropical-river-to-muddy-river, and warm-ocean-to-lukewarm-ocean mappings, together
with disabled RU rainforest entries. These are palette decisions to review if
southern tropical regions are enabled later. The audit never changes them.
