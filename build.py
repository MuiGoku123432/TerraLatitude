#!/usr/bin/env python3
"""Offline Java 21 build using installed Prism dependencies; never starts Minecraft."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
if sys.version_info < (3, 11):
    raise SystemExit("Python 3.11 or newer is required (TOML settings use the standard-library tomllib module).")
import tomllib
import zipfile
from dataclasses import dataclass

ROOT = Path(__file__).resolve().parent
VERSION = "0.2.0-test"
ARTIFACT = f"terralatitude-{VERSION}-1.21.1.jar"
FTF_NAME = "freeterraforged-1.0.0-neoforge-1.21.1.jar"
NT_NAME = "naturaltemperature-1.1.10-NEOFORGE-MC-1.21.1.jar"
FTF_SHA256 = "0ab7d71948cc9b43037e0f02611fb3be782c84379e034ab7e487fd98a1fc5529"
NT_SHA256 = "6ee8a45d1fce319c5f6654c08c73663254b7fee7c970710249537329b0fb1d2a"
FTF_TARGET = "etcodehome.freeterraforged.world.worldgen.runtime.WorldgenPlans$SamplerDecoration"
TARGET_DESCRIPTOR = "(Lnet/minecraft/world/level/biome/Climate$Sampler;III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;"


@dataclass(frozen=True)
class BuildSettings:
    instance: Path
    prism: Path
    java_home: str | None


def add_build_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--instance", help="Prism instance directory containing mmc-pack.json")
    parser.add_argument("--prism-dir", help="Prism data directory containing libraries/ and meta/")
    parser.add_argument("--java-home", help="Installed Java 21 JDK directory")


def default_prism_dir() -> Path:
    if sys.platform == "darwin":
        return Path.home() / "Library/Application Support/PrismLauncher"
    if sys.platform == "win32":
        return Path(os.environ.get("APPDATA", Path.home() / "AppData/Roaming")) / "PrismLauncher"
    return Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "PrismLauncher"


def build_settings(args: argparse.Namespace) -> BuildSettings:
    local_file = ROOT / "build.local.toml"
    local = tomllib.loads(local_file.read_text()) if local_file.is_file() else {}
    unknown = set(local) - {"instance", "prism", "java_home"}
    if unknown:
        raise RuntimeError(f"Unknown build.local.toml settings: {', '.join(sorted(unknown))}")
    for key, value in local.items():
        if not isinstance(value, str):
            raise RuntimeError(f"build.local.toml setting {key!r} must be a quoted path string.")

    def selected(cli: str | None, environment: str, key: str) -> str | None:
        return cli or os.environ.get(environment) or local.get(key) or None

    def expanded(value: str | Path) -> Path:
        path = Path(os.path.expandvars(str(value))).expanduser()
        return (ROOT / path).resolve() if not path.is_absolute() else path.resolve()

    instance = selected(args.instance, "TERRALATITUDE_INSTANCE", "instance")
    if not instance:
        raise RuntimeError("Select installed dependencies with --instance /path/to/Prism/instances/YourInstance, "
                           "TERRALATITUDE_INSTANCE, or instance in build.local.toml "
                           "(copy build-settings.example.toml). No dependencies will be downloaded.")
    prism = selected(args.prism_dir, "TERRALATITUDE_PRISM_DIR", "prism") or default_prism_dir()
    jdk = selected(args.java_home, "TERRALATITUDE_JAVA_HOME", "java_home") or os.environ.get("JAVA_HOME")
    return BuildSettings(expanded(instance), expanded(prism), str(expanded(jdk)) if jdk else None)


def java_tool(jdk: Path, name: str) -> str:
    return str(jdk / "bin" / (name + (".exe" if sys.platform == "win32" else "")))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], *, cwd: Path = ROOT) -> str:
    result = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {command!r}\n{result.stdout}")
    return result.stdout.strip()


def java_home(override: str | None = None) -> Path:
    if override:
        selected = Path(override)
    elif sys.platform == "darwin":
        selected = Path(run(["/usr/libexec/java_home", "-v", "21"]))
    elif compiler := shutil.which("javac"):
        selected = Path(compiler).resolve().parent.parent
    else:
        raise RuntimeError("Java 21 JDK not found. Supply --java-home or java_home in build.local.toml.")
    version = run([java_tool(selected, "javac"), "-version"])
    if not version.startswith("javac 21."):
        raise RuntimeError(f"Java 21 is required, found {version}")
    return selected


def maven_path(coordinate: str, libraries: Path) -> Path:
    parts = coordinate.split(":")
    group, artifact, version = parts[:3]
    suffix = "-" + parts[3] if len(parts) > 3 else ""
    return libraries / group.replace(".", "/") / artifact / version / f"{artifact}-{version}{suffix}.jar"


def dependencies(settings: BuildSettings) -> tuple[list[Path], list[str]]:
    # Use active version metadata, never an arbitrary newest/oldest cached JAR.
    if not (settings.instance / "mmc-pack.json").is_file():
        raise RuntimeError(f"Prism instance metadata not found: {settings.instance / 'mmc-pack.json'}. "
                           "Set --instance to the instance directory, not its minecraft/ subdirectory.")
    libraries = settings.prism / "libraries"
    components = json.loads((settings.instance / "mmc-pack.json").read_text())["components"]
    versions = {v["uid"]: v["version"] for v in components}
    if versions.get("net.neoforged") != "21.1.257" or versions.get("net.minecraft") != "1.21.1":
        raise RuntimeError(f"This test build is pinned to Minecraft 1.21.1 / NeoForge 21.1.257: {versions}")
    required = [
        libraries / "net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar",
        libraries / "net/neoforged/neoforge/21.1.257/neoforge-21.1.257-universal.jar",
        libraries / "net/neoforged/fancymodloader/loader/4.0.45/loader-4.0.45.jar",
        settings.instance / "minecraft/mods" / FTF_NAME,
        settings.instance / "minecraft/mods" / NT_NAME,
    ]
    for path in required:
        if not path.is_file():
            raise RuntimeError(f"Required installed dependency missing: {path}")
    if sha256(required[3]) != FTF_SHA256:
        raise RuntimeError("Installed FreeTerraForged changed. Review its integration before updating the pinned checksum.")
    if sha256(required[4]) != NT_SHA256:
        raise RuntimeError("Installed Natural Temperature changed. Review its integration before updating the pinned checksum.")
    paths = list(required)
    unavailable = []
    for uid, version in (("net.neoforged", "21.1.257"), ("net.minecraft", "1.21.1")):
        metadata = settings.prism / "meta" / uid / f"{version}.json"
        if not metadata.is_file():
            raise RuntimeError(f"Prism metadata missing: {metadata}. Set --prism-dir to the existing Prism data directory.")
        for item in json.loads(metadata.read_text()).get("libraries", []):
            candidate = maven_path(item["name"], libraries)
            if candidate.is_file() and candidate not in paths:
                paths.append(candidate)
            elif not candidate.is_file():
                unavailable.append(item["name"])
    return paths, unavailable


def deterministic_jar(path: Path, files: dict[str, bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".jar.tmp")
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, contents in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 13, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, contents, compresslevel=9)
    temporary.replace(path)


def source_fingerprints() -> dict[str, str]:
    """Record production resources and all offline test inputs, excluding outputs."""
    paths = [p for directory in ("src", "tests", "tools")
             for p in (ROOT / directory).rglob("*")
             if p.is_file() and "__pycache__" not in p.parts]
    paths.extend((ROOT / "build.py", ROOT / "verify.py"))
    return {p.relative_to(ROOT).as_posix(): sha256(p) for p in sorted(paths)}


def test_main_classes(tests: list[Path]) -> list[str]:
    classes = []
    for path in tests:
        if not path.name.endswith("Test.java"):
            continue
        source = path.read_text(encoding="utf-8")
        if not re.search(r"public\s+static\s+void\s+main\s*\(\s*String(?:\s*\[\s*\]|\s*\.\.\.)", source):
            raise RuntimeError(f"Ordinary test must expose public static void main(String[] args): {path}")
        package = re.search(r"^\s*package\s+([\w.]+)\s*;", source, re.MULTILINE)
        classes.append((package.group(1) + "." if package else "") + path.stem)
    if not classes:
        raise RuntimeError("No ordinary *Test.java main classes were discovered.")
    return classes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_build_arguments(parser)
    args = parser.parse_args()
    reports = ROOT / "reports"
    reports.mkdir(parents=True, exist_ok=True)
    report = {"version": VERSION, "status": "FAIL", "static_only": True,
              "limits": ["Minecraft was not launched.", "Mixin runtime application and biome appearance require in-world testing."]}
    try:
        settings = build_settings(args)
        jdk = java_home(settings.java_home)
        paths, missing = dependencies(settings)
        classpath = os.pathsep.join(map(str, paths))
        report["java"] = run([java_tool(jdk, "java"), "-version"])
        report["classpath"] = [{"path": str(p), "sha256": sha256(p)} for p in paths]
        report["unavailable_metadata_libraries"] = missing
        source_root = ROOT / "src/main/java"
        resource_root = ROOT / "src/main/resources"
        sources = sorted(source_root.rglob("*.java"))
        tests = sorted((ROOT / "src/test/java").rglob("*.java"))
        if not sources or not tests:
            raise RuntimeError("Main Java sources and the plain Java model test are both required.")
        fingerprints_before = source_fingerprints()
        build = ROOT / "build"
        for folder in (build / "classes", build / "test-classes"):
            if folder.exists():
                shutil.rmtree(folder)
            folder.mkdir(parents=True)
        javac = java_tool(jdk, "javac")
        options = ["--release", "21", "-proc:none", "-encoding", "UTF-8", "-g:none"]
        report["compile_output"] = run([javac, *options, "-classpath", classpath,
                                       "-d", str(build / "classes"), *map(str, sources)])
        test_classpath = os.pathsep.join(map(str, (build / "classes", build / "test-classes", *paths)))
        report["test_compile_output"] = run([javac, *options, "-classpath", test_classpath,
                                            "-d", str(build / "test-classes"), *map(str, tests)])
        report["tests"] = [{"class": test_class,
                            "output": run([java_tool(jdk, "java"), "-ea", "-classpath", test_classpath, test_class])}
                           for test_class in test_main_classes(tests)]
        report["model_test_output"] = "\n".join(test["output"] for test in report["tests"])
        files = {str(p.relative_to(build / "classes")).replace(os.sep, "/"): p.read_bytes()
                 for p in sorted((build / "classes").rglob("*.class"))}
        for path in sorted(resource_root.rglob("*")):
            if path.is_file():
                name = path.relative_to(resource_root).as_posix()
                if name in files:
                    raise RuntimeError(f"Resource collides with compiled class: {name}")
                files[name] = path.read_bytes()
        files.setdefault("META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\r\nImplementation-Title: TerraLatitude\r\n"
                         f"Implementation-Version: {VERSION}\r\n\r\n").encode("utf-8"))
        artifact = ROOT / "dist" / ARTIFACT
        deterministic_jar(artifact, files)
        report["source_files"] = source_fingerprints()
        if fingerprints_before != report["source_files"]:
            raise RuntimeError("Sources/resources changed while compiling. Rebuild after edits finish.")
        report["artifact"] = {"path": str(artifact), "sha256": sha256(artifact), "bytes": artifact.stat().st_size,
                              "classes": len([p for p in files if p.endswith(".class")]), "entries": len(files)}
        report["status"] = "PASS"
        print(f"Compiled Java 21; model tests passed; {artifact.name} ({artifact.stat().st_size} bytes)")
        print(f"SHA256 {report['artifact']['sha256']}")
    except Exception as exc:
        report["error"] = str(exc)
        print(str(exc), file=sys.stderr)
    (reports / "build.json").write_text(json.dumps(report, indent=2) + "\n")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
