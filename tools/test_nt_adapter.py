#!/usr/bin/env python3
"""Compare the adapter to installed NT code in an isolated JVM; never launch Minecraft.

Fixture-only bytecode substitutions replace WorldPainter's density-function reads
with supplied raw values and apply the addon's compiled calculator redirect
handlers. NT coordinate and climate algorithms remain the installed dependency's
code. This is NOT a Mixin runtime or modpack startup test.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("terralatitude_build", ROOT / "build.py")
build = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = build
spec.loader.exec_module(build)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    build.add_build_arguments(parser)
    args = parser.parse_args()
    settings = build.build_settings(args)
    paths, _ = build.dependencies(settings)
    jdk = build.java_home(settings.java_home)
    classpath = os.pathsep.join(map(str, paths))
    output = ROOT / "build" / "nt-adapter-parity"
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    source_root = ROOT / "src/main/java/dev/fnf/terralatitude"
    names = ["NtSettings.java", "NtSnapshotScope.java", "NtSettingsRedirected.java", "NaturalClimateAdapter.java",
             "mixin/NtTemperatureSettingsMixin.java", "mixin/NtHumiditySettingsMixin.java"]
    sources = [source_root / name for name in names]
    sources.extend(sorted((ROOT / "tests/java/nt-adapter").glob("*.java")))
    report = {"scope": "offline NT numerical parity and compiled redirect handlers",
              "limitations": ["Does not start Mixin or verify live injection order.",
                              "Does not launch Minecraft, load a world, or validate biome outcomes."],
              "source_sha256": {str(p.relative_to(ROOT)): build.sha256(p) for p in sources},
              "passed": False}
    report_path = ROOT / "reports" / "nt-adapter-parity.json"
    report_path.parent.mkdir(exist_ok=True)
    try:
        report["compile"] = build.run([build.java_tool(jdk, "javac"), "--release", "21", "-proc:none",
                                      "-classpath", classpath, "-d", str(output), *map(str, sources)])
        nt = next(p for p in paths if p.name == build.NT_NAME)
        report["natural_temperature_sha256"] = build.sha256(nt)
        runtime = os.pathsep.join((str(output), classpath))
        report["fixture"] = build.run([build.java_tool(jdk, "java"), "-classpath", runtime,
                                      "NtFixtureTransformer", str(nt), str(output)])
        report["tests"] = build.run([build.java_tool(jdk, "java"), "-ea", "-classpath", runtime,
                                    "dev.fnf.terralatitude.NaturalClimateAdapterTest"])
        report["passed"] = True
        print(report["fixture"].strip())
        print(report["tests"].strip())
        print("Limit: isolated bytecode fixture, not actual Mixin startup or world generation.")
        return 0
    except Exception as error:
        report["error"] = str(error)
        print(error, file=sys.stderr)
        return 1
    finally:
        report_path.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
