#!/usr/bin/env python3
"""Reproduce cancelling HEAD callback ordering with installed Mixin components.

Runs the installed MixinInfo comparator, MethodHead locator, Target injection-node
tracking/insertion, and compiled synthetic NT/TerraLatitude cancelling callbacks.
The surrounding order loop and cancellation shell follow inspected library code.
This is a bounded regression fixture, NOT full Mixin bootstrap or Minecraft.
"""
from __future__ import annotations
import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("terralatitude_build_order", ROOT / "build.py")
build = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = build
spec.loader.exec_module(build)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    build.add_build_arguments(parser)
    parser.add_argument("--addon-jar", type=Path, help="Addon jar to inspect; defaults to the current dist artifact")
    args = parser.parse_args()
    settings = build.build_settings(args)
    paths, _ = build.dependencies(settings)
    jdk = build.java_home(settings.java_home)
    classpath = os.pathsep.join(map(str, paths))
    output = ROOT / "build" / "sampler-order"
    output.mkdir(parents=True, exist_ok=True)
    source = ROOT / "tests/java/sampler-order/OrderRegression.java"
    addon = args.addon_jar or ROOT / "dist" / build.ARTIFACT
    nt = next(p for p in paths if p.name == build.NT_NAME)
    mixin = next(p for p in paths if "sponge-mixin" in p.name)
    report = {"scope": "installed Mixin ordering/HEAD/insertion components with compiled synthetic callbacks",
              "limitations": ["Synthetic callbacks model NT unconditional cancellation and TerraLatitude owned-sample cancellation.",
                              "The surrounding sorted-order loop and callback bytecode shell mirror inspected Mixin internals.",
                              "Does not bootstrap the full Mixin transformer, run FTF wrapping, or launch Minecraft."],
              "passed": False,
              "artifact_fix_verified": False,
              "source_sha256": {str(source.relative_to(ROOT)): build.sha256(source),
                                str(Path(__file__).relative_to(ROOT)): build.sha256(Path(__file__))}}
    report_path = ROOT / "reports" / "sampler-order.json"
    report_path.parent.mkdir(exist_ok=True)
    try:
        report["addon_sha256"] = build.sha256(addon)
        report["natural_temperature_sha256"] = build.sha256(nt)
        report["mixin_sha256"] = build.sha256(mixin)
        report["compile"] = build.run([build.java_tool(jdk, "javac"), "--release", "21", "-proc:none",
                                      "-classpath", classpath, "-d", str(output), str(source)])
        report["tests"] = build.run([build.java_tool(jdk, "java"), "-ea", "-classpath",
                                    os.pathsep.join((str(output), classpath)), "OrderRegression",
                                    str(nt), str(addon), str(output)])
        report["passed"] = True
        report["artifact_fix_verified"] = True
        print(report["tests"].strip())
        return 0
    except Exception as error:
        report["error"] = str(error)
        print(error, file=sys.stderr)
        return 1
    finally:
        report_path.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
