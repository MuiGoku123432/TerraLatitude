#!/usr/bin/env python3
"""Read pack inputs and write a static climate-placement inventory; never run Minecraft."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

if sys.version_info < (3, 11):
    raise SystemExit("Python 3.11 or newer is required.")
import tomllib

ROOT = Path(__file__).resolve().parents[1]
ID = r"[a-z0-9_.-]+:[a-z0-9_./-]+"
EXACT_ID = re.compile(ID + r"\Z")
WATCHED_MODS = {"regions_unexplored", "still_life_west"}
LIMITATIONS = [
    "Static candidate counts are not reachable-biome counts or spawn frequencies.",
    "Registry loading, injector order, region selection, terrain, tags, other mods, and datapack overrides are not simulated.",
    "An outgoing replacement is unconditional within its declared dimension, not proof the biome can never appear by another path.",
    "Climate families are name-based audit hints, not biome temperature tags or scientific classifications.",
    "Predicates are preserved as authored; their ranges are not solved or normalized.",
    "Only Regions Unexplored and Still Life West packaged resources are inventoried; other JARs and world datapacks are outside scope.",
]


def strip_json_comments(text: str) -> str:
    """Strip // and /* */ without damaging quoted text or JSON error line numbers."""
    out: list[str] = []
    i = 0
    quoted = False
    while i < len(text):
        char = text[i]
        if quoted:
            out.append(char)
            if char == "\\" and i + 1 < len(text):
                i += 1
                out.append(text[i])
            elif char == '"':
                quoted = False
            i += 1
        elif char == '"':
            quoted = True
            out.append(char)
            i += 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            end = len(text) if end == -1 else end
            out.append(" " * (end - i))
            i = end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            if end == -1:
                raise ValueError("Unterminated JSON block comment")
            out.extend("\n" if c == "\n" else " " for c in text[i:end + 2])
            i = end + 2
        else:
            out.append(char)
            i += 1
    return "".join(out)


def parse_replacements(text: str) -> tuple[list[dict], list[dict]]:
    """Understand exact-ID A > B rules only; retain unknown syntax as issues."""
    rules: list[dict] = []
    issues: list[dict] = []
    dimension: str | None = None
    valid_section = True
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith(("!", "#")):
            continue
        section = re.fullmatch(r"\[(" + ID + r")\]", line)
        if section:
            dimension = section[1]
            valid_section = True
            continue
        if line.startswith("["):
            dimension = None
            valid_section = False
            issues.append({"line": number, "reason": "Unsupported dimension section", "raw": raw})
            continue
        match = re.fullmatch(r"(" + ID + r")\s*>\s*(" + ID + r")", line)
        if not match or not valid_section or dimension is None:
            issues.append({"line": number, "reason": "Unparsed or unscoped rule; not counted as unconditional", "raw": raw})
            continue
        rules.append({"line": number, "dimension": dimension, "source": match[1],
                      "replacement": match[2], "unconditional_within_dimension": True,
                      "changes_biome": match[1] != match[2]})
    return rules, issues


def climate_families(biome: str) -> list[str]:
    name = biome.split(":", 1)[-1]
    families: list[str] = []
    if any(s in name for s in ("jungle", "rainforest", "tropic", "mangrove")):
        families.append("tropical_or_rainforest")
    if "river" in name:
        families.append("river")
    if any(s in name for s in ("ocean", "beach", "coast", "reef", "mangrove")):
        families.append("coast_or_ocean")
    if any(s in name for s in ("frozen", "snow", "icy", "cold", "tundra", "alpine")):
        families.append("cold_or_alpine")
    if any(s in name for s in ("desert", "arid", "savanna", "shrub", "steppe", "prairie")):
        families.append("dry_or_open")
    return families


def ru_placements(document: dict) -> tuple[list[dict], dict]:
    groups = document.get("biome_groups", {})
    layout = document.get("biome_placements")
    if not isinstance(layout, dict) or "placements" not in layout:
        raise ValueError("RU biome_placements.placements is missing")
    placements = layout["placements"]
    if not isinstance(groups, dict) or not isinstance(placements, dict):
        raise ValueError("RU biome_groups/biome_placements.placements must be objects")
    rows = []
    for biome, spec in sorted(placements.items()):
        if not isinstance(spec, dict):
            raise ValueError(f"RU placement {biome} must be an object")
        enabled = spec.get("enabled", spec.get("special_enabled"))
        if enabled is not None and not isinstance(enabled, bool):
            raise ValueError(f"RU placement {biome} enabled flag must be boolean")
        group_id = spec.get("group")
        group = groups.get(group_id, {})
        group_enabled = group.get("enabled") if isinstance(group, dict) else None
        state = "disabled" if enabled is False or group_enabled is False else "enabled" if enabled is True else "unknown"
        rows.append({"biome": biome, "status": state, "enabled": enabled,
                     "special_placement": "special_enabled" in spec,
                     "dimension": spec.get("dimension"), "group": group_id,
                     "group_enabled": group_enabled, "can_replace": spec.get("can_replace"),
                     "parameters": spec.get("parameters", {}), "weight": spec.get("weight"),
                     "families": climate_families(biome)})
    return rows, groups


def recursive_climate_predicates(value: object, path: str = "$") -> list[dict]:
    """Keep unknown/nested predicate structures rather than interpreting their math."""
    found = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in ("temperature", "humidity"):
                found.append({"path": child_path, "axis": key, "raw": child})
            else:
                found.extend(recursive_climate_predicates(child, child_path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            found.extend(recursive_climate_predicates(child, f"{path}[{index}]"))
    return found


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_jar(path: Path) -> dict | None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        metadata_path = "META-INF/neoforge.mods.toml"
        if metadata_path not in names:
            return None
        metadata = tomllib.loads(archive.read(metadata_path).decode("utf-8"))
        mods = [{"id": m.get("modId"), "version": m.get("version")} for m in metadata.get("mods", [])]
        if not WATCHED_MODS.intersection(m["id"] for m in mods):
            return None
        biomes = []
        injectors = []
        other_predicates = []
        errors = []
        for name in sorted(names):
            biome = re.fullmatch(r"data/([^/]+)/worldgen/biome/(.+)\.json", name)
            if biome:
                biomes.append(f"{biome[1]}:{biome[2]}")
            if not name.endswith(".json") or "/lithostitched/" not in name:
                continue
            try:
                doc = json.loads(strip_json_comments(archive.read(name).decode("utf-8")))
                predicates = recursive_climate_predicates(doc)
                if "/lithostitched/biome_injector/" in name:
                    if not isinstance(doc, dict):
                        raise ValueError("Injector resource must be an object")
                    injectors.append({"resource": name, "type": doc.get("type"),
                                      "dimension": doc.get("dimension"), "region": doc.get("region"),
                                      "priority": doc.get("priority"), "targets": doc.get("targets"),
                                      "replacement": doc.get("replacement", doc.get("biome")),
                                      "parameters": doc.get("parameters"),
                                      "climate_predicates": predicates})
                elif predicates:
                    other_predicates.append({"resource": name, "climate_predicates": predicates})
            except (ValueError, UnicodeError) as exc:
                errors.append({"resource": name, "reason": str(exc)})
        return {"file": path.name, "sha256": sha256(path), "mods": mods,
                "packaged_biome_ids": sorted(set(biomes)), "injectors": injectors,
                "other_lithostitched_climate_predicates": other_predicates, "errors": errors}


def audit(instance: Path) -> dict:
    game = instance / "minecraft"
    issues: list[dict] = []
    evidence = []

    def input_text(relative: str) -> str | None:
        path = game / relative
        try:
            text = path.read_text(encoding="utf-8-sig")
            evidence.append({"path": relative, "sha256": sha256(path)})
            return text
        except (OSError, UnicodeError) as exc:
            issues.append({"source": relative, "reason": str(exc)})
            return None

    replacements = []
    replacement_text = input_text("config/biome_replacer.properties")
    if replacement_text is not None:
        replacements, parse_issues = parse_replacements(replacement_text)
        issues.extend({"source": "config/biome_replacer.properties", **item} for item in parse_issues)
    ru_rows, groups = [], {}
    ru_text = input_text("config/regions_unexplored/common.json")
    if ru_text is not None:
        try:
            document = json.loads(strip_json_comments(ru_text))
            if not isinstance(document, dict):
                raise ValueError("RU config must be an object")
            ru_rows, groups = ru_placements(document)
        except (ValueError, AttributeError, TypeError) as exc:
            issues.append({"source": "config/regions_unexplored/common.json", "reason": str(exc)})

    jars = []
    mods_dir = game / "mods"
    if not mods_dir.is_dir():
        issues.append({"source": "mods", "reason": "Mods directory is missing"})
    else:
        for path in sorted(mods_dir.glob("*.jar")):
            try:
                jar = read_jar(path)
                if jar is not None:
                    jars.append(jar)
                    issues.extend({"source": path.name, **error} for error in jar["errors"])
            except (OSError, ValueError, UnicodeError, zipfile.BadZipFile, KeyError, TypeError, AttributeError) as exc:
                issues.append({"source": path.name, "reason": f"Cannot inspect mod metadata/resources: {exc}"})
    present = {mod["id"] for jar in jars for mod in jar["mods"]}
    for missing in sorted(WATCHED_MODS - present):
        issues.append({"source": "mods", "reason": f"No active JAR declaring {missing} found"})
    for mod_id in sorted(WATCHED_MODS):
        copies = [jar["file"] for jar in jars if any(m["id"] == mod_id for m in jar["mods"])]
        if len(copies) > 1:
            issues.append({"source": "mods", "reason": f"Multiple active JARs declare {mod_id}: {copies}"})

    candidates = {row["biome"] for row in ru_rows if row["status"] == "enabled"}
    packaged_candidates = set()
    for jar in jars:
        for injector in jar["injectors"]:
            replacement = injector["replacement"]
            if isinstance(replacement, str) and EXACT_ID.fullmatch(replacement):
                packaged_candidates.add(replacement)
    candidates.update(packaged_candidates)
    replacement_flags = [{**rule, "families": climate_families(rule["source"])}
                         for rule in replacements if rule["changes_biome"]]
    flagged = {rule["source"] for rule in replacement_flags}
    family_flags = [rule for rule in replacement_flags if rule["families"]]
    excluded = [row for row in ru_rows if row["status"] == "disabled"]
    summary = {
        "parsed_replacement_rules": len(replacements),
        "climate_family_replacement_flags": len(family_flags),
        "ru_placement_entries": len(ru_rows),
        "ru_enabled_entries": sum(row["status"] == "enabled" for row in ru_rows),
        "ru_disabled_entries": len(excluded),
        "ru_unknown_entries": sum(row["status"] == "unknown" for row in ru_rows),
        "packaged_injectors": sum(len(jar["injectors"]) for jar in jars),
        "packaged_injector_candidate_ids": len(packaged_candidates),
        "static_candidate_ids": len(candidates),
        "candidate_ids_with_outgoing_replacement": len(candidates & flagged),
        "unflagged_static_candidate_ids": len(candidates - flagged),
        "issue_count": len(issues),
    }
    return {"schema_version": 1, "status": "incomplete" if issues else "complete",
            "instance": str(instance.resolve()), "scope": "RU config, Biome Replacer, RU/Still Life West packaged resources",
            "limitations": LIMITATIONS, "summary": summary, "inputs": evidence,
            "replacements": replacements, "climate_family_replacement_flags": family_flags,
            "ru": {"groups": groups, "placements": ru_rows,
                   "excluded_biome_ids": [row["biome"] for row in excluded]},
            "jars": jars, "static_candidate_ids": sorted(candidates),
            "candidate_replacement_flags": [rule for rule in replacement_flags if rule["source"] in candidates],
            "issues": issues}


def render_summary(report: dict) -> str:
    stats = report["summary"]
    lines = [f"TerraLatitude static pack audit: {report['status']}",
             f"RU placements: {stats['ru_enabled_entries']} enabled, {stats['ru_disabled_entries']} disabled, {stats['ru_unknown_entries']} unknown.",
             f"Packaged injectors: {stats['packaged_injectors']}; {stats['packaged_injector_candidate_ids']} named candidate biomes.",
             f"Combined static candidates: {stats['static_candidate_ids']}; {stats['candidate_ids_with_outgoing_replacement']} have outgoing replacement rules.",
             "These counts describe configuration only, not reachable biomes or generation frequency."]
    for jar in report["jars"]:
        lines.append(f"  {jar['file']}: {len(jar['injectors'])} packaged biome injectors; {len(jar['packaged_biome_ids'])} biome definitions.")
    lines.append("Unconditional replacement flags for climate-related names:")
    for rule in report["climate_family_replacement_flags"]:
        lines.append(f"  [{rule['dimension']}] {rule['source']} -> {rule['replacement']}")
    disabled = [row["biome"] for row in report["ru"]["placements"] if row["status"] == "disabled" and row["families"]]
    lines.append("Disabled RU climate-related placements: " + (", ".join(disabled) or "none found"))
    if report["issues"]:
        lines.append(f"Incomplete evidence: {len(report['issues'])} issue(s); consult the JSON report.")
    lines.append("No pack files were changed and no game was launched.")
    return "\n".join(lines)


def selected_instance(cli: str | None, root: Path = ROOT) -> Path:
    value = cli or os.environ.get("TERRALATITUDE_INSTANCE")
    if not value:
        settings = root / "build.local.toml"
        local = tomllib.loads(settings.read_text(encoding="utf-8")) if settings.is_file() else {}
        value = local.get("instance")
    if not isinstance(value, str) or not value:
        raise ValueError("Supply --instance /path/to/Prism/instances/Name, TERRALATITUDE_INSTANCE, or instance in build.local.toml")
    path = Path(os.path.expandvars(value)).expanduser()
    return (root / path).resolve() if not path.is_absolute() else path.resolve()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", help="Prism instance directory containing minecraft/")
    parser.add_argument("--output", type=Path, default=ROOT / "reports/pack-audit.json", help="Report path, outside the instance")
    args = parser.parse_args(argv)
    try:
        instance = selected_instance(args.instance)
        if not instance.is_dir():
            raise ValueError(f"Instance directory does not exist: {instance}")
        output = args.output.expanduser().resolve()
        if output == instance or instance in output.parents:
            raise ValueError("Write the audit report outside the instance so pack inputs stay read-only.")
        report = audit(instance)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(render_summary(report))
        print(f"Report: {output}")
        return 1 if report["issues"] else 0
    except (OSError, ValueError) as exc:
        print(f"Audit failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
