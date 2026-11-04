import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from open_jarvis.release.portable_policy import is_denied_portable_path
from open_jarvis.release.repo_hygiene import find_hygiene_items


class ArtifactSafetyTests(unittest.TestCase):
    def test_release_policy_blocks_plugin_runtime_artifacts(self):
        for path in ("plugin_cache/state.json", "plugin_state/runtime.json", ".plugin/session.json"):
            with self.subTest(path=path):
                self.assertTrue(is_denied_portable_path(path)["denied"])

    def test_repo_hygiene_detects_plugin_runtime_artifacts(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "plugin_cache").mkdir()
            (root / "plugin_state").mkdir()
            (root / ".plugin").mkdir()

            findings = {item.path for item in find_hygiene_items(root, include_secrets=False)}

        self.assertIn("plugin_cache", findings)
        self.assertIn("plugin_state", findings)
        self.assertIn(".plugin", findings)


if __name__ == "__main__":
    unittest.main()
