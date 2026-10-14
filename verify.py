#!/usr/bin/env python3
"""Static package/bytecode checks. Does not bootstrap Minecraft or apply Mixins."""
from __future__ import annotations
import argparse
import json
import os
import re
import struct
import sys
if sys.version_info < (3, 11):
    raise SystemExit("Python 3.11+ is required.")
import tomllib
import zipfile
from build import (ARTIFACT, FTF_TARGET, NT_NAME, ROOT, TARGET_DESCRIPTOR,
                   VERSION, add_build_arguments, build_settings, dependencies,
                   java_home, java_tool, run, sha256, source_fingerprints, test_main_classes)

ACTIVE_MIXINS = {"IntegratedSamplerMixin", "NtTemperatureSettingsMixin", "NtHumiditySettingsMixin"}
SAMPLER = "net.minecraft.world.level.biome.Climate$Sampler"
SAMPLE_DESCRIPTOR = "(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;"
RAW_MODULE = "etcodehome.freeterraforged.world.worldgen.cell.climate.ClimateModule"
NT_METHODS = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}


def method_sections(bytecode: str) -> dict[str, list[tuple[str, str]]]:
    headers = list(re.finditer(r"\n  ([^\n]+\([^\n]*\));\n\s+descriptor: ([^\n]+)", bytecode))
    result = {}
    for index, header in enumerate(headers):
        name = header.group(1).split("(", 1)[0].rsplit(" ", 1)[-1]
        end = headers[index + 1].start() if index + 1 < len(headers) else len(bytecode)
        result.setdefault(name, []).append((header.group(2), bytecode[header.end():end]))
    return result


def mixin_annotation(bytecode: str) -> str:
    matches = re.findall(r"org\.spongepowered\.asm\.mixin\.Mixin\((.*?)\n\s*\)", bytecode, re.DOTALL)
    return matches[-1] if matches else ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_build_arguments(parser)
    args = parser.parse_args()
    checks, errors = [], []
    reports = ROOT / "reports"
    reports.mkdir(parents=True, exist_ok=True)
    report = {"status": "FAIL", "version": VERSION, "static_only": True, "checks": checks, "errors": errors,
              "limits": ["No game, server, or world was launched.",
                         "Compiled annotations and target bytecode are inspected; Mixins are not applied.",
                         "Actual Mixin ordering, full-pack startup, biome generation, and performance need runtime testing."]}

    def require(condition, description):
        checks.append({"check": description, "pass": bool(condition)})
        if not condition:
            errors.append(description)

    try:
        settings = build_settings(args)
        jdk = java_home(settings.java_home)
        deps, _ = dependencies(settings)
        artifact = ROOT / "dist" / ARTIFACT
        build = json.loads((reports / "build.json").read_text())
        report["artifact"] = {"path": str(artifact), "sha256": sha256(artifact)}
        report["dependencies"] = [{"path": str(p), "sha256": sha256(p)} for p in deps[:5]]
        require(build["status"] == "PASS", "Java 21 compilation and ordinary offline tests passed")
        require(build["artifact"]["sha256"] == sha256(artifact), "Artifact matches the successful build report")
        require(report["dependencies"] == build["classpath"][:5], "Installed target dependencies match the tested build")
        require(source_fingerprints() == build["source_files"], "Production and offline test inputs match the tested build")
        expected_tests = test_main_classes(sorted((ROOT / "src/test/java").rglob("*.java")))
        require([t["class"] for t in build.get("tests", [])] == expected_tests, "Build ran every ordinary *Test main class")
        report["tests"] = build.get("tests", [])
        with zipfile.ZipFile(artifact) as z:
            names = z.namelist()
            classes = [p for p in names if p.endswith(".class")]
            require(len(names) == len(set(names)), "Archive has no duplicate entries")
            require(classes and all(p.startswith("dev/fnf/terralatitude/") for p in classes), "No upstream classes are bundled")
            required = {"TerraLatitude", "IntegratedClimate", "ClimateComposition", "ClimateMath", "ClimateProfile", "WorldProfiles", "ClimateCommands", "RawClimateAccess", "NaturalClimateAdapter"}
            require(all(f"dev/fnf/terralatitude/{n}.class" in names for n in required), "Integrated climate, world profiles, diagnostics, and adapters are packaged")
            require(all(struct.unpack(">H", z.read(p)[6:8])[0] == 65 for p in classes), "All packaged classes target Java 21")
            require(not any(p.endswith("Test.class") for p in classes), "Offline test classes are excluded")
            require(not any("fnf_altitude" in p or "dev/fnf/altitude/" in p for p in names), "No retired prototype archive paths remain")
            manifest = z.read("META-INF/MANIFEST.MF").decode()
            require("Implementation-Title: TerraLatitude\r\n" in manifest and f"Implementation-Version: {VERSION}\r\n" in manifest, "Manifest declares the current name and version")
            metadata = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
            require(metadata["modLoader"] == "javafml", "JavaFML metadata is declared")
            mod = next(m for m in metadata["mods"] if m["modId"] == "terralatitude")
            require(len(metadata["mods"]) == 1 and mod["displayName"] == "TerraLatitude" and mod["version"] == VERSION, "Exactly one TerraLatitude identity matches the build version")
            depmeta = {d["modId"]: d for d in metadata["dependencies"]["terralatitude"]}
            old = depmeta.get("fnf_altitude", {})
            require(old.get("type") == "incompatible" and old.get("versionRange") == "[0,)" and old.get("side") == "BOTH", "Old altitude prototype is incompatible to prevent duplicate cooling")
            for name, version in {"minecraft": "[1.21.1]", "freeterraforged": "[1.0.0007-climate-api.1]", "naturaltemperature": "[1.1.10]"}.items():
                dep = depmeta.get(name, {})
                require(dep.get("type") == "required" and dep.get("versionRange") == version and dep.get("side") == "BOTH", f"Both sides require tested {name} {version}")
            neo = depmeta.get("neoforge", {})
            require(neo.get("type") == "required" and neo.get("versionRange") == "[21.1.257,21.2)", "NeoForge dependency matches the tested baseline")
            require([m["config"] for m in metadata["mixins"]] == ["terralatitude.mixins.json"], "Exactly one TerraLatitude mixin configuration is registered")
            config = json.loads(z.read("terralatitude.mixins.json"))
            require(config.get("required") is True and config.get("injectors", {}).get("defaultRequire", 0) >= 1, "Missing required mixin targets fail clearly")
            require(config.get("compatibilityLevel") == "JAVA_21", "Mixin Java 21 compatibility is declared")
            require(config.get("package") == "dev.fnf.terralatitude.mixin" and set(config.get("mixins", [])) == ACTIVE_MIXINS and len(config["mixins"]) == len(ACTIVE_MIXINS) and not config.get("client") and not config.get("server"), "Only the three integrated mixins are active on both sides; old return hook is inactive")
            require(all(f"dev/fnf/terralatitude/mixin/{n}.class" in names for n in ACTIVE_MIXINS), "Every active mixin class is packaged")

        ftf = deps[3]
        nt = settings.instance / "minecraft/mods" / NT_NAME
        for path, name, expected in ((ftf, "freeterraforged", "1.0.0007-climate-api.1"), (nt, "naturaltemperature", "1.1.10")):
            with zipfile.ZipFile(path) as z:
                metadata = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
                require(next(m["version"] for m in metadata["mods"] if m["modId"] == name) == expected, f"Pinned installed {name} declares {expected}")
        classpath = os.pathsep.join(map(str, (artifact, *deps)))
        evidence_cache = {}

        def inspect(class_name):
            if class_name not in evidence_cache:
                text = run([java_tool(jdk, "javap"), "-p", "-s", "-c", "-v", "-classpath", classpath, class_name])
                evidence_cache[class_name] = text
                name = class_name.rsplit(".", 1)[-1].replace("$", "-") + "-bytecode.txt"
                (reports / name).write_text(text + "\n")
            return evidence_cache[class_name]

        sampler = method_sections(inspect(SAMPLER))
        require(any(d == SAMPLE_DESCRIPTOR for d, _ in sampler.get("sample", [])), "Minecraft sampler exposes the exact three-coordinate target method")
        integrated = inspect("dev.fnf.terralatitude.mixin.IntegratedSamplerMixin")
        ann = mixin_annotation(integrated)
        callback_desc = "(IIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"
        callbacks = method_sections(integrated).get("terraLatitude$sample", [])
        require(len(callbacks) == 1 and callbacks[0][0] == callback_desc, "Integrated callback has three coordinates and CallbackInfoReturnable")
        require(SAMPLER.replace(".", "/") in ann and "priority=2000" in ann and "remap=false" in ann, "Integrated mixin targets the named Minecraft sampler at priority 2000")
        body = callbacks[0][1] if callbacks else ""
        require("sample" + SAMPLE_DESCRIPTOR in body and 'value="HEAD"' in body and "order=900" in body and "cancellable=true" in body and "require=1" in body and "allow=1" in body, "Integrated callback declares one cancellable HEAD injection in pass 900 on the exact sampler method")
        require("IntegratedClimate.evaluate:" in body and "CallbackInfoReturnable.setReturnValue:" in body, "Callback delegates to integrated composition before supplying its replacement")
        painter = inspect("com.naturaltemperature.mixin.WorldPainter")
        ann = mixin_annotation(painter)
        with zipfile.ZipFile(nt) as z:
            nt_config = json.loads(z.read("naturaltemperature.mixins.json"))
        priority = re.search(r"priority=(\d+)", ann)
        priority = int(priority.group(1)) if priority else nt_config.get("mixinPriority", 1000)
        require(SAMPLER.replace(".", "/") in ann and priority == 1000, "Pinned NT sampler mixin retains its expected default mixin priority")
        callbacks = method_sections(painter).get("modifyTemperature", [])
        require(len(callbacks) == 1 and callbacks[0][0] == callback_desc and 'value="HEAD"' in callbacks[0][1] and "cancellable=true" in callbacks[0][1], "Pinned NT callback is the expected cancellable HEAD injection")
        nt_order = re.search(r"\border=(\d+)", callbacks[0][1]) if callbacks else None
        require((int(nt_order.group(1)) if nt_order else 1000) > 900,
                "Integrated injector pass 900 precedes NT's cancellable callback pass; mixin priority alone is insufficient")

        module = inspect(RAW_MODULE)
        climate_name = "etcodehome.freeterraforged.world.worldgen.cell.climate.Climate"
        climate = inspect(climate_name)
        raw_desc = "(Letcodehome/freeterraforged/world/worldgen/cell/Cell;FF)Letcodehome/freeterraforged/world/worldgen/cell/climate/ClimateModule$RawSample;"
        raw = method_sections(climate).get("sampleRaw", [])
        require(len(raw) == 1 and raw[0][0] == raw_desc and "ACC_PUBLIC" in raw[0][1],
                "FTF exposes the public populated-cell raw climate API with the expected descriptor")
        require(raw and "ClimateModule.sampleRaw:" in raw[0][1] and "putfield" not in raw[0][1]
                and "WorldLookup" not in raw[0][1], "Native raw climate applies its edge policy without mutation or terrain lookup")
        require("dev/fnf/terralatitude/mixin/ClimateModuleAccessor.class" not in names
                and "dev/fnf/terralatitude/RawClimateMath.class" not in names,
                "Private FTF accessors and copied warp arithmetic are absent from the addon")
        shaping = inspect("etcodehome.freeterraforged.world.worldgen.cell.climate.TemperatureShaping")
        require("PresetManager" not in shaping and "dev/fnf/terralatitude" not in shaping
                and "com/naturaltemperature" not in shaping,
                "Native elevation helper is independent of presets and integration mods")
        math = inspect("dev.fnf.terralatitude.ClimateMath")
        require("TemperatureShaping.applyNormalized:" in math and "Math.pow:" not in math,
                "TerraLatitude delegates altitude arithmetic to FTF")
        methods = method_sections(module)
        desc = "(Letcodehome/freeterraforged/world/worldgen/cell/Cell;FFFFZ)V"
        body = next((b for d, b in methods.get("apply", []) if d == desc), "")
        require(body and 0 <= body.find("Field temperature:") < body.find("Method modifyTemp:(FF)F") and "Field moisture:" in body and "Method modifyMoisture:(FF)F" in body and "Cell.temperature:F" in body and "Cell.moisture:F" in body, "FTF computes raw regional noises before terrain shaping and final cell climate")
        decoration = method_sections(inspect(FTF_TARGET)).get("sample", [])
        require(len(decoration) == 1 and decoration[0][0] == TARGET_DESCRIPTOR and "Climate$Sampler.sample:" in decoration[0][1], "FTF sampler decoration invokes the Minecraft sampler containing the integration")
        selection = method_sections(inspect("etcodehome.freeterraforged.world.worldgen.runtime.WorldgenBiomeSelection$Executable"))
        resolve, select = selection.get("resolveOwned", []), selection.get("select", [])
        require(len(resolve) == 1 and "SamplerDecoration.sample:" in resolve[0][1] and len(select) == 1 and "ProviderSelection.resolveRequired:" in select[0][1] and "SelectionDecoration.apply:" in select[0][1], "FTF samples climate before provider and replacement selection")

        composition = inspect("dev.fnf.terralatitude.ClimateComposition")
        require(all(token not in composition for token in ("RawClimateAccess.sample:", "WorldProfiles.",
                    "GeneratorContext", "WorldLookup", "getChunk", "PresetManager")),
                "Supplied-input composition has no world/terrain acquisition or global preset dependency")
        raw_methods = method_sections(inspect("dev.fnf.terralatitude.RawClimateAccess"))
        capture = raw_methods.get("fromCell", [])
        capture_body = capture[0][1] if len(capture) == 1 else ""
        require(capture_body and "Levels.getNormalizedInlandElevation:" in capture_body
                and "Levels.scale:" in capture_body and "Levels.ground:" in capture_body
                and "putfield" not in capture_body and "WorldLookup" not in capture_body
                and "sampleSurfaceY:" not in capture_body and "getResource:" not in capture_body,
                "Populated-cell snapshot uses native FTF height/gate inputs without lookup, pooling, or mutation")
        require("Climate.sampleRaw:" in capture_body and "Noise.compute:" not in capture_body,
                "Populated-cell snapshot delegates raw regional sampling to the native FTF API")
        integrated_methods = method_sections(inspect("dev.fnf.terralatitude.IntegratedClimate"))
        surface = integrated_methods.get("evaluateSurface", [])
        surface_body = surface[0][1] if len(surface) == 1 else ""
        require(surface_body.count("RawClimateAccess.sample:") == 1
                and "ClimateComposition.compose:" in surface_body and "Method evaluate:" not in surface_body,
                "Surface expectation performs one raw acquisition and composes from that snapshot")
        commands = inspect("dev.fnf.terralatitude.ClimateCommands")
        require("IntegratedClimate.evaluateSurface:" in commands and "RawClimateAccess.sample:" not in commands
                and "SamplerDecoration.sample:" in commands,
                "Diagnostics reuse expected surface inputs while independently checking the actual decorated sampler")

        redirects = {}
        for axis in ("Temperature", "Humidity"):
            calculator = "com.naturaltemperature." + axis + "Calculator"
            targets = method_sections(inspect(calculator))
            mixin = inspect("dev.fnf.terralatitude.mixin.Nt" + axis + "SettingsMixin")
            methods, ann = method_sections(mixin), mixin_annotation(mixin)
            require(calculator.replace(".", "/") in ann and "remap=false" in ann and "implements dev.fnf.terralatitude.NtSettingsRedirected" in mixin, f"{axis} redirects target the correct calculator with their runtime marker")
            desc = f"(Lcom/naturaltemperature/{axis}Context;)D"
            require(all(len(targets.get(n, [])) == 1 and targets[n][0][0] == desc for n in NT_METHODS), f"All four redirected {axis.lower()} mode methods have expected context descriptors")
            fields = {f"{axis.lower()}_zone_{i}" for i in range(1, 7)} | {f"percentage_coverage_zone_{i}" for i in range(1, 7)}
            field_evidence = {}
            for field in sorted(fields):
                handler = methods.get("terralatitude$" + field, [])
                body = handler[0][1] if len(handler) == 1 else ""
                selected = re.search(r"method=\[([^\]]+)\]", body)
                selected = set(re.findall(r'"([^\"]+)"', selected.group(1))) if selected else set()
                counts = {n: len(re.findall(r"\bgetstatic\s+[^\n]+// Field com/naturaltemperature/Config\." + re.escape(field) + r":D\b", targets.get(n, [("", "")])[0][1])) for n in sorted(NT_METHODS)}
                valid = (len(handler) == 1 and handler[0][0] == "()D" and selected == NT_METHODS
                         and f'target="Lcom/naturaltemperature/Config;{field}:D"' in body
                         and 'value="FIELD"' in body and "opcode=178" in body and "require=1" in body
                         and "remap=false" in body and "NtSnapshotScope.number:" in body and sum(counts.values()) > 0)
                field_evidence[field] = {"read_counts": counts, "matches": valid}
            redirects[calculator] = field_evidence
            require(all(v["matches"] for v in field_evidence.values()), f"All 12 {axis.lower()} snapshot redirects match actual GETSTATIC reads and callback descriptors")
            require(len([n for n in methods if n.startswith("terralatitude$")]) == 12, f"{axis} mixin contains exactly the 12 audited redirect handlers")
        report["nt_redirect_targets"] = redirects
        entrypoint = inspect("dev.fnf.terralatitude.TerraLatitude")
        require("net.neoforged.fml.common.Mod" in entrypoint and 'value="terralatitude"' in entrypoint, "Entrypoint carries the TerraLatitude @Mod annotation")
        require("terralatitude-common.toml" in entrypoint and "fnf-altitude-common.toml" not in entrypoint, "Entrypoint registers the TerraLatitude common configuration")
        require("WorldProfiles" in entrypoint and "ClimateCommands" in entrypoint, "Entrypoint registers world lifecycle and diagnostic handlers")
        report["model_test_output"] = build.get("model_test_output", "")
    except Exception as exc:
        errors.append(f"Verification could not complete: {exc}")
    report["status"] = "PASS" if not errors else "FAIL"
    (reports / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"Static verification {report['status']}: {sum(c['pass'] for c in checks)}/{len(checks)} checks passed")
    for error in errors:
        print(error, file=sys.stderr)
    return bool(errors)


if __name__ == "__main__":
    raise SystemExit(main())
