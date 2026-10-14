#!/usr/bin/env python3
"""Compare supplied-input composition against the compiled, pinned 0.2.1 release.

No Minecraft bootstrap, world, server, terrain lookup or live Mixin application.
The legacy fixture replaces input acquisition only, preserving arithmetic bytecode.
"""
from __future__ import annotations
import argparse
import ast
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("terralatitude_build_composition", ROOT / "build.py")
build = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = build
spec.loader.exec_module(build)
LEGACY_SHA256 = "a8caea0ab0e4f656331934d5fdcdd5c6b18f8da4e41dba106130f603fdd71cd8"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    build.add_build_arguments(parser)
    parser.add_argument("--baseline-jar", type=Path, default=ROOT / "dist/terralatitude-0.2.1-test-1.21.1.jar")
    args = parser.parse_args()
    report = {"passed": False, "scope": "compiled legacy composition with supplied native/raw inputs",
              "limitations": ["No live ownership binding, Mixin startup, terrain acquisition or world generation.",
                             "Timing covers composition only, not complete FTF sampling or chunk generation.",
                             "Synthetic corpus is not a replay of rounded server CSV inputs."]}
    report_path = ROOT / "reports/composition-compatibility.json"
    report_path.parent.mkdir(exist_ok=True)
    try:
        if build.sha256(args.baseline_jar) != LEGACY_SHA256:
            raise RuntimeError("Baseline must be the exact recorded 0.2.1 release; rebuild commit 762a41c with its pinned dependencies if unavailable.")
        report["baseline_sha256"] = LEGACY_SHA256
        settings = build.build_settings(args)
        deps, _ = build.dependencies(settings)
        jdk = build.java_home(settings.java_home)
        addon = ROOT / "dist" / build.ARTIFACT
        report["addon_sha256"] = build.sha256(addon)
        with zipfile.ZipFile(addon) as current, zipfile.ZipFile(args.baseline_jar) as legacy:
            unchanged = ["WorldProfiles", "ClimateMath", "NaturalClimateAdapter", "ClimateProfile",
                         "mixin/NtTemperatureSettingsMixin", "mixin/NtHumiditySettingsMixin"]
            report["unchanged_contract_classes"] = {}
            for name in unchanged:
                entry = f"dev/fnf/terralatitude/{name}.class"
                identical = current.read(entry) == legacy.read(entry)
                report["unchanged_contract_classes"][name] = identical
                if not identical:
                    raise RuntimeError(f"Unexpected change to legacy ownership/math/mixin contract: {name}")
        report["dependencies"] = [{"path": str(p), "sha256": build.sha256(p)} for p in deps[:5]]
        output = ROOT / "build/composition-fixture"
        if output.exists():
            shutil.rmtree(output)
        output.mkdir(parents=True)
        with zipfile.ZipFile(addon) as jar:
            for entry in jar.namelist():
                if entry.startswith("dev/fnf/terralatitude/") and entry.endswith(".class"):
                    destination = output / entry
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(jar.read(entry))
        cp = os.pathsep.join(map(str, [output, *deps]))
        sources = sorted((ROOT / "tests/java/nt-adapter").glob("*.java"))
        sources += sorted((ROOT / "tests/java/composition").glob("*.java"))
        report["fixture_sources"] = {str(p.relative_to(ROOT)): build.sha256(p) for p in sources}
        report["compile"] = build.run([build.java_tool(jdk, "javac"), "--release", "21", "-proc:none",
                                        "-classpath", cp, "-d", str(output), *map(str, sources)])
        nt = next(p for p in deps if p.name == build.NT_NAME)
        java = [build.java_tool(jdk, "java"), "-ea", "-classpath", cp]
        report["nt_fixture"] = build.run([*java, "NtFixtureTransformer", str(nt), str(output)])
        report["legacy_fixture"] = build.run([*java, "LegacyCompositionFixture", str(args.baseline_jar), str(output)])
        ftf = deps[3]
        report["cell_fixture"] = build.run([*java, "FtfCellFixture", str(ftf), str(output)])
        report["cell_tests"] = build.run([*java, "dev.fnf.terralatitude.FtfCellInputTest"])
        report["tests"] = build.run([*java, "dev.fnf.terralatitude.CompositionCompatibilityTest",
                                     str(ROOT / "docs/evidence/server-climate-profile-2026-10-14.json")])
        match = re.search(r"COMPOSITION_BENCHMARK (\[.*?\]) (\[.*?\])", report["tests"])
        if match:
            before, after = (ast.literal_eval(x) for x in match.groups())
            regression = statistics.median(after) / statistics.median(before) - 1
            spread = max((max(x) - min(x)) / statistics.median(x) for x in (before, after))
            report["composition_benchmark"] = {
                "legacy_ns": before, "current_ns": after, "median_change_percent": regression * 100,
                "max_relative_range_percent": spread * 100,
                "assessment": "inconclusive: variability exceeds 5%" if spread > .05 else
                              "within proposed 5% budget" if regression <= .05 else "exceeds proposed 5% budget",
                "not_a_full_sampler_benchmark": True}
        report["passed"] = True
        print(report["tests"])
        print(report["cell_tests"])
        print("Limit: supplied-input fixture; runtime integration and full sampler cost remain unverified.")
        return 0
    except Exception as error:
        report["error"] = str(error)
        print(error, file=sys.stderr)
        return 1
    finally:
        report_path.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
