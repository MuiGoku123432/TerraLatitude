#!/usr/bin/env python3
"""Static package/bytecode checks. Does not bootstrap Minecraft or apply Mixins."""
from __future__ import annotations

import json
import argparse
import re
import struct
import sys
if sys.version_info < (3, 11):
    raise SystemExit("Python 3.11 or newer is required (TOML metadata uses the standard-library tomllib module).")
import tomllib
import zipfile

from build import (ARTIFACT, FTF_NAME, FTF_TARGET, NT_NAME, ROOT,
                   TARGET_DESCRIPTOR, VERSION, add_build_arguments, build_settings,
                   dependencies, java_home, java_tool, run, sha256)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_build_arguments(parser)
    args = parser.parse_args()
    checks = []
    errors = []
    report = {"status": "FAIL", "static_only": True, "checks": checks, "errors": errors,
              "limits": ["No game, server, or world was launched.",
                         "These checks do not prove that Mixin transforms successfully in the complete pack.",
                         "Actual biome distribution, snow/weather, appearance, and performance require a new test world."]}

    def require(condition: bool, description: str) -> None:
        checks.append({"check": description, "pass": bool(condition)})
        if not condition:
            errors.append(description)

    try:
        settings = build_settings(args)
        jdk = java_home(settings.java_home)
        deps, _ = dependencies(settings)
        artifact = ROOT / "dist" / ARTIFACT
        build = json.loads((ROOT / "reports/build.json").read_text())
        report["artifact"] = {"path": str(artifact), "sha256": sha256(artifact)}
        report["dependencies"] = [{"path": str(p), "sha256": sha256(p)} for p in deps[:5]]
        require(build["status"] == "PASS", "Java 21 compilation and isolated model test passed")
        require(build["artifact"]["sha256"] == sha256(artifact), "Artifact matches the successful build report")
        require(report["dependencies"] == build["classpath"][:5], "Installed target dependencies match those used by the build")
        source_files = {p.relative_to(ROOT).as_posix(): sha256(p)
                        for p in sorted((ROOT / "src").rglob("*")) if p.is_file()}
        require(source_files == build["source_files"], "All current sources/resources match the tested build")
        with zipfile.ZipFile(artifact) as z:
            names = z.namelist()
            require(len(names) == len(set(names)), "Archive has no duplicate entries")
            classes = [p for p in names if p.endswith(".class")]
            require(all(p.startswith("dev/fnf/terralatitude/") for p in classes), "No Minecraft or third-party classes are bundled")
            require("dev/fnf/terralatitude/TerraLatitude.class" in names, "Mod entrypoint class is packaged")
            require("dev/fnf/terralatitude/AltitudeModel.class" in names, "Tested altitude model class is packaged")
            require("dev/fnf/terralatitude/mixin/SamplerDecorationMixin.class" in names, "Sampler mixin class is packaged")
            require(all(struct.unpack(">H", z.read(p)[6:8])[0] == 65 for p in classes), "All class files target Java 21")
            require(not any(p.endswith("Test.class") for p in classes), "Test classes are not included in the mod")
            require(not any("fnf_altitude" in p or "dev/fnf/altitude/" in p for p in names),
                    "No prototype package or resource names remain in the archive")
            manifest = z.read("META-INF/MANIFEST.MF").decode()
            require("Implementation-Title: TerraLatitude\r\n" in manifest
                    and f"Implementation-Version: {VERSION}\r\n" in manifest,
                    "Manifest declares the TerraLatitude name and version")
            metadata = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
            require(metadata["modLoader"] == "javafml", "JavaFML loader metadata is declared")
            mod = next(m for m in metadata["mods"] if m["modId"] == "terralatitude")
            require(mod["version"] == VERSION, "Mod ID and version match this test build")
            require(len(metadata["mods"]) == 1 and mod["displayName"] == "TerraLatitude",
                    "Archive declares only the TerraLatitude mod identity")
            dependencies_meta = {d["modId"]: d for d in metadata["dependencies"]["terralatitude"]}
            old_mod = dependencies_meta.get("fnf_altitude", {})
            require(old_mod.get("type") == "incompatible" and old_mod.get("versionRange") == "[0,)"
                    and old_mod.get("side") == "BOTH",
                    "Prototype fnf_altitude is incompatible to prevent duplicate altitude cooling")
            expected_ranges = {"minecraft": "[1.21.1]", "freeterraforged": "[1.0.0007]", "naturaltemperature": "[1.1.10]"}
            for mod_id, range_value in expected_ranges.items():
                dep = dependencies_meta.get(mod_id, {})
                require(dep.get("type") == "required" and dep.get("versionRange") == range_value and dep.get("side") == "BOTH",
                        f"Both sides require exact tested {mod_id} version {range_value}")
            require(dependencies_meta.get("neoforge", {}).get("type") == "required", "NeoForge is a required dependency")
            require(dependencies_meta.get("neoforge", {}).get("versionRange") == "[21.1.257,21.2)",
                    "NeoForge version range matches the tested 1.21.1 baseline")
            mixin_configs = [item["config"] for item in metadata["mixins"]]
            require(len(mixin_configs) == 1, "Exactly one mixin configuration is registered")
            require(mixin_configs == ["terralatitude.mixins.json"], "Renamed TerraLatitude mixin resource is registered")
            config = json.loads(z.read(mixin_configs[0]))
            require(config.get("required") is True, "Mixin configuration fails clearly if it cannot apply")
            require(config.get("compatibilityLevel") == "JAVA_21", "Mixin Java 21 compatibility is declared")
            require(config.get("package") == "dev.fnf.terralatitude.mixin" and "SamplerDecorationMixin" in config.get("mixins", []),
                    "Sampler mixin is enabled on both client and dedicated server")
            require(config.get("injectors", {}).get("defaultRequire", 0) >= 1, "A missing required injection cannot silently succeed")
        ftf = settings.instance / "minecraft/mods" / FTF_NAME
        nt = settings.instance / "minecraft/mods" / NT_NAME
        for path, mod_id, expected in ((ftf, "freeterraforged", "1.0.0007"), (nt, "naturaltemperature", "1.1.10")):
            with zipfile.ZipFile(path) as z:
                metadata = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
                actual = next(m["version"] for m in metadata["mods"] if m["modId"] == mod_id)
                require(actual == expected, f"Installed {mod_id} declares tested version {expected}")
        javap = java_tool(jdk, "javap")
        target = run([javap, "-p", "-s", "-c", "-classpath", str(ftf), FTF_TARGET])
        (ROOT / "reports/ftf-target-bytecode.txt").write_text(target + "\n")
        sample = re.search(r"public net\.minecraft\.world\.level\.biome\.Climate\$TargetPoint sample\([^\n]+\);\n\s+descriptor: ([^\n]+)", target)
        require(bool(sample) and sample.group(1) == TARGET_DESCRIPTOR, "Installed FTF target has the exact sample method descriptor")
        sample_body = re.split(r"\n  (?:public|private|protected) ", target[sample.end():], maxsplit=1)[0] if sample else ""
        require(len(re.findall(r"\b\d+: areturn\b", sample_body)) == 1, "Target sample method has exactly one RETURN injection site")
        mixin = run([javap, "-p", "-s", "-v", "-classpath", str(artifact), "dev.fnf.terralatitude.mixin.SamplerDecorationMixin"])
        (ROOT / "reports/mixin-bytecode.txt").write_text(mixin + "\n")
        require(FTF_TARGET in mixin or FTF_TARGET.replace(".", "/") in mixin, "Compiled Mixin targets FTF SamplerDecoration")
        require("sample" + TARGET_DESCRIPTOR in mixin, "Compiled injector selects the exact sample descriptor")
        require('value="RETURN"' in mixin and "cancellable=true" in mixin, "Compiled injection is a cancellable RETURN hook")
        require("require=1" in mixin and "allow=1" in mixin, "Compiled injector requires exactly one matching injection")
        require("remap=false" in mixin, "Custom FTF target is excluded from Minecraft name remapping")
        callback = "(Lnet/minecraft/world/level/biome/Climate$Sampler;IIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"
        require(callback in mixin, "Injection callback matches the sampler and all three coordinate arguments")
        entrypoint = run([javap, "-p", "-v", "-classpath", str(artifact), "dev.fnf.terralatitude.TerraLatitude"])
        require("net.neoforged.fml.common.Mod" in entrypoint and 'value="terralatitude"' in entrypoint, "Compiled mod entrypoint carries its @Mod annotation")
        require("terralatitude-common.toml" in entrypoint and "fnf-altitude-common.toml" not in entrypoint,
                "Compiled entrypoint registers the renamed TerraLatitude common config")
        require("fnf_altitude" not in entrypoint, "Compiled entrypoint has no retired prototype mod identity")
        report["model_test_output"] = build.get("model_test_output", "")
    except Exception as exc:
        errors.append(f"Verification could not complete: {exc}")
    report["status"] = "PASS" if not errors else "FAIL"
    (ROOT / "reports").mkdir(parents=True, exist_ok=True)
    (ROOT / "reports/verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"Static verification {report['status']}: {sum(c['pass'] for c in checks)}/{len(checks)} checks passed")
    for error in errors:
        print(error, file=sys.stderr)
    return bool(errors)


if __name__ == "__main__":
    raise SystemExit(main())
