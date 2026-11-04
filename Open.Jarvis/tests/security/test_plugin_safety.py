import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from open_jarvis.plugins.loader import load_plugin
from open_jarvis.plugins.manifest import validate_plugin_manifest_schema


class PluginSafetyTests(unittest.TestCase):
    def test_loader_blocks_entrypoint_that_escapes_plugin_directory(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            entry = {
                "id": "escape_plugin",
                "enabled": True,
                "path": str(root / "plugin"),
                "manifest": {"entrypoint": "../outside.py"},
                "permissions": [],
            }

            result = load_plugin(entry)

        self.assertEqual(result["status"], "blocked")
        self.assertTrue(any("escapes" in issue or "outside" in issue for issue in result["issues"]))

    def test_manifest_unknown_permission_fails_closed(self):
        result = validate_plugin_manifest_schema(
            {
                "id": "unknown_permission",
                "name": "Unknown Permission",
                "version": "1.0.0",
                "entrypoint": "main.py",
                "permissions": ["network.everything"],
            }
        )

        self.assertFalse(result["valid"])
        self.assertIn("network.everything", result["blocked_permissions"])


if __name__ == "__main__":
    unittest.main()
