"""Offline parser and report tests using synthetic, independently authored fixtures."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("audit_pack", Path(__file__).resolve().parents[1] / "tools/audit_pack.py")
audit_pack = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit_pack)


class ParserTests(unittest.TestCase):
    def test_replacements_comments_sections_and_real_arrow_syntax(self):
        rules, issues = audit_pack.parse_replacements("""
! Comment with misleading minecraft:desert > test:wrong
# Another comment
[minecraft:overworld]
minecraft:jungle > test:temperate_forest
 minecraft:river>test:mud_river
[test:other_world]
test:old > test:new
""")
        self.assertEqual([], issues)
        self.assertEqual(3, len(rules))
        self.assertEqual("minecraft:overworld", rules[0]["dimension"])
        self.assertEqual("test:other_world", rules[-1]["dimension"])
        self.assertEqual("test:mud_river", rules[1]["replacement"])

    def test_unknown_rules_are_not_silently_counted_as_unconditional(self):
        rules, issues = audit_pack.parse_replacements("""minecraft:jungle > test:a
[minecraft:overworld]
minecraft:jungle -> test:a
minecraft:jungle > test:a 0.5
#minecraft:is_jungle > test:a
minecraft:river > test:a > test:b
[invalid dimension]
minecraft:river > test:b
[minecraft:overworld]
minecraft:desert > minecraft:desert
""")
        self.assertEqual(1, len(rules))
        self.assertFalse(rules[0]["changes_biome"])
        self.assertEqual(6, len(issues))

    def test_json_comments_preserve_string_slashes_escapes_and_newlines(self):
        fixture = '{\n// note\n"url":"https://example.test/a//b", "quote":"\\\"/*ok*/", /*\n comment */ "v": 3\n}'
        cleaned = audit_pack.strip_json_comments(fixture)
        self.assertEqual(fixture.count("\n"), cleaned.count("\n"))
        self.assertEqual({"url": "https://example.test/a//b", "quote": '"/*ok*/', "v": 3}, json.loads(cleaned))
        with self.assertRaisesRegex(ValueError, "Unterminated"):
            audit_pack.strip_json_comments('{"v":3 /*')

    def test_ru_group_disabled_and_special_flags(self):
        rows, _ = audit_pack.ru_placements({
            "biome_groups": {"wet": {"enabled": False}},
            "biome_placements": {"placements": {
                "test:a": {"enabled": True, "group": "wet", "parameters": {"humidity": {"min": 0.4}}},
                "test:b": {"special_enabled": True},
                "test:c": {"enabled": False},
                "test:d": {},
            }},
        })
        self.assertEqual(["disabled", "enabled", "disabled", "unknown"], [r["status"] for r in rows])
        self.assertEqual({"humidity": {"min": 0.4}}, rows[0]["parameters"])
        self.assertTrue(rows[1]["special_placement"])
        with self.assertRaisesRegex(ValueError, "boolean"):
            audit_pack.ru_placements({"biome_placements": {"placements": {"test:a": {"enabled": "true"}}}})
        with self.assertRaisesRegex(ValueError, "missing"):
            audit_pack.ru_placements({})

    def test_nested_unknown_predicates_preserved(self):
        raw = {"conditions": [{"humidity": {"custom_operator": [0.3, 0.7]}}], "temperature": "custom:noise"}
        found = audit_pack.recursive_climate_predicates(raw)
        self.assertEqual({"custom_operator": [0.3, 0.7]}, found[0]["raw"])
        self.assertEqual("$.conditions[0].humidity", found[0]["path"])
        self.assertEqual("custom:noise", found[1]["raw"])


class InventoryTests(unittest.TestCase):
    def write_jar(self, directory, name, mod_id, resources):
        path = directory / name
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("META-INF/neoforge.mods.toml", f'[[mods]]\nmodId="{mod_id}"\nversion="1"\n')
            for resource, document in resources.items():
                jar.writestr(resource, json.dumps(document))
        return path

    def test_jar_metadata_discovery_counts_only_named_replacements(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = self.write_jar(root, "unexpected-name.jar", "still_life_west", {
                "data/test/worldgen/biome/hill.json": {},
                "data/test/lithostitched/biome_injector/hill.json": {
                    "type": "lithostitched:replace_partially", "replacement": "test:hill",
                    "parameters": {"temperature": {"min_inclusive": -0.3}},
                },
            })
            result = audit_pack.read_jar(path)
            self.assertEqual(["test:hill"], result["packaged_biome_ids"])
            self.assertEqual("test:hill", result["injectors"][0]["replacement"])
            self.assertEqual({"min_inclusive": -0.3}, result["injectors"][0]["climate_predicates"][0]["raw"])
            self.assertEqual(64, len(result["sha256"]))
            irrelevant = self.write_jar(root, "irrelevant.jar", "other_mod", {})
            self.assertIsNone(audit_pack.read_jar(irrelevant))

    def test_missing_inputs_produce_incomplete_evidence_not_false_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = audit_pack.audit(Path(tmp))
            self.assertEqual("incomplete", report["status"])
            self.assertGreaterEqual(len(report["issues"]), 3)
            self.assertEqual([], report["static_candidate_ids"])
            self.assertIn("not reachable", audit_pack.render_summary(report))

    def test_end_to_end_deduplicates_and_flags_without_claiming_reachability(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = root / "minecraft/config"
            (config / "regions_unexplored").mkdir(parents=True)
            mods = root / "minecraft/mods"
            mods.mkdir()
            rules = config / "biome_replacer.properties"
            rules.write_text("[minecraft:overworld]\nminecraft:jungle > test:forest\ntest:hill > test:new_hill\n")
            ru = config / "regions_unexplored/common.json"
            ru.write_text(json.dumps({"biome_placements": {"placements": {
                "test:hill": {"enabled": True}, "test:rainforest": {"enabled": False}
            }}}))
            self.write_jar(mods, "ru.jar", "regions_unexplored", {})
            self.write_jar(mods, "still.jar", "still_life_west", {
                "data/test/lithostitched/biome_injector/hill.json": {"replacement": "test:hill", "parameters": {"humidity": [0, 1]}}
            })
            before = {p: p.read_bytes() for p in root.rglob("*") if p.is_file()}
            report = audit_pack.audit(root)
            self.assertEqual("complete", report["status"])
            self.assertEqual(1, report["summary"]["static_candidate_ids"])
            self.assertEqual(1, report["summary"]["candidate_ids_with_outgoing_replacement"])
            self.assertEqual(["test:rainforest"], report["ru"]["excluded_biome_ids"])
            self.assertEqual(1, len(report["climate_family_replacement_flags"]))
            self.assertEqual(before, {p: p.read_bytes() for p in root.rglob("*") if p.is_file()})

    def test_settings_fallback_and_explicit_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "build.local.toml").write_text('instance = "relative-instance"\n')
            with patch.dict("os.environ", {}, clear=True):
                self.assertEqual((root / "relative-instance").resolve(), audit_pack.selected_instance(None, root))
                self.assertEqual((root / "explicit").resolve(), audit_pack.selected_instance("explicit", root))

    def test_cli_refuses_to_write_inside_pack(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch("sys.stderr"):
                result = audit_pack.main(["--instance", str(root), "--output", str(root / "config.json")])
            self.assertEqual(2, result)
            self.assertFalse((root / "config.json").exists())


if __name__ == "__main__":
    unittest.main()
