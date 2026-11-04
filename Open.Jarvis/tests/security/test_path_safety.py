import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from open_jarvis.security.path_safety import is_private_runtime_path, validate_path_within_root


class PathSafetyTests(unittest.TestCase):
    def test_relative_path_inside_root_is_allowed(self):
        with TemporaryDirectory() as tmp:
            result = validate_path_within_root(tmp, "docs/readme.txt")

        self.assertTrue(result.allowed)

    def test_path_traversal_is_rejected(self):
        with TemporaryDirectory() as tmp:
            result = validate_path_within_root(tmp, "../escape.txt")

        self.assertFalse(result.allowed)
        self.assertIn("escapes", result.reason)

    def test_absolute_path_outside_root_is_rejected(self):
        with TemporaryDirectory() as tmp:
            result = validate_path_within_root(tmp, Path(tmp).parent / "outside.txt")

        self.assertFalse(result.allowed)

    def test_private_runtime_paths_are_rejected_by_default(self):
        with TemporaryDirectory() as tmp:
            for path in (".env", "memory.json", "config/settings.json", "logs/jarvis.log", "provider_cache/state.json", "plugin_state/state.json"):
                with self.subTest(path=path):
                    self.assertFalse(validate_path_within_root(tmp, path).allowed)

    def test_private_runtime_path_detection_covers_plugin_and_provider_state(self):
        self.assertTrue(is_private_runtime_path(".provider/session.json"))
        self.assertTrue(is_private_runtime_path(".plugin/session.json"))
        self.assertTrue(is_private_runtime_path("groq_cache/response.json"))
        self.assertFalse(is_private_runtime_path("docs/security.md"))


if __name__ == "__main__":
    unittest.main()
