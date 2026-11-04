import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from open_jarvis.commands import groq_router
from open_jarvis.config.manager import ConfigManager
from open_jarvis.config.paths import ConfigPaths
from open_jarvis.memory import build_context_prompt
from open_jarvis.memory.controls import MemoryControlService
from open_jarvis.memory.memory_habits import get_top_habits
from open_jarvis.memory.memory_notes import add_note, get_notes
from open_jarvis.memory.memory_preferences import get_preference
from open_jarvis.memory.privacy_mode import memory_reads_enabled, memory_writes_enabled
from open_jarvis.ui.memory_panel import MemoryPanelModel


class MemoryPrivacyControlsTests(unittest.TestCase):
    def _config(self, root: Path, **privacy: bool) -> ConfigManager:
        manager = ConfigManager(paths=ConfigPaths(config_dir=root, settings_file=root / "settings.json"), env={})
        manager.load()
        manager.set_many({f"privacy.{key}": value for key, value in privacy.items()})
        manager.save()
        return manager

    def test_config_privacy_disables_normal_memory_note_writes(self):
        with TemporaryDirectory() as tmp:
            manager = self._config(Path(tmp), privacy_mode=True)
            memory = {"notes": [], "preferences": {}, "habits": {}}

            self.assertFalse(memory_writes_enabled(manager))
            with (
                patch("open_jarvis.memory.memory_notes.load_memory", return_value=memory) as load_memory,
                patch("open_jarvis.memory.memory_notes.save_memory") as save_memory,
            ):
                add_note("private note", config_manager=manager)

            load_memory.assert_not_called()
            save_memory.assert_not_called()
            self.assertEqual(memory["notes"], [])

    def test_privacy_mode_suppresses_personalization_context_and_reads(self):
        with TemporaryDirectory() as tmp:
            manager = self._config(Path(tmp), privacy_mode=True)

            self.assertFalse(memory_reads_enabled(manager))
            with (
                patch("open_jarvis.memory.load_memory") as context_memory,
                patch("open_jarvis.memory.memory_habits.load_memory") as habits_memory,
                patch("open_jarvis.memory.memory_preferences.load_memory") as preference_memory,
                patch("open_jarvis.memory.memory_notes.load_memory") as notes_memory,
            ):
                self.assertEqual(build_context_prompt(config_manager=manager), "")
                self.assertEqual(get_top_habits(config_manager=manager), [])
                self.assertIsNone(get_preference("favorite_app", config_manager=manager))
                self.assertEqual(get_notes(config_manager=manager), [])

            context_memory.assert_not_called()
            habits_memory.assert_not_called()
            preference_memory.assert_not_called()
            notes_memory.assert_not_called()

    def test_disabled_memory_suppresses_personalization_context_and_reads(self):
        with TemporaryDirectory() as tmp:
            manager = self._config(Path(tmp), memory_enabled=False)

            self.assertFalse(memory_reads_enabled(manager))
            self.assertEqual(build_context_prompt(config_manager=manager), "")
            self.assertEqual(get_top_habits(config_manager=manager), [])
            self.assertIsNone(get_preference("favorite_app", config_manager=manager))
            self.assertEqual(get_notes(config_manager=manager), [])

    def test_groq_context_does_not_include_persisted_memory_during_privacy_mode(self):
        captured = {}

        class DummyChat:
            def __init__(self):
                self.completions = self

            def create(self, **kwargs):
                captured["system"] = kwargs["messages"][0]["content"]
                return type(
                    "Resp",
                    (),
                    {"choices": [type("Choice", (), {"message": type("Message", (), {"content": '{"action":"talk","params":{}}'})()})()]},
                )()

        class DummyClient:
            def __init__(self):
                self.chat = DummyChat()

        memory = {"preferences": {"favorite_app": "private browser"}, "habits": {"private command": 9}, "notes": []}
        with (
            patch.dict("os.environ", {"JARVIS_PRIVACY_MODE": "true"}),
            patch("open_jarvis.memory.load_memory", return_value=memory),
            patch("open_jarvis.memory.get_top_habits", return_value=[("private command", 9)]),
        ):
            result = groq_router.analyze_with_groq("hello", client=DummyClient())

        self.assertEqual(result["action"], "talk")
        self.assertNotIn("private browser", captured["system"])
        self.assertNotIn("private command", captured["system"])

    def test_view_list_delete_clear_and_export_keep_memory_user_controlled(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            memory_path = root / "memory.json"
            memory_path.write_text(
                json.dumps(
                    {
                        "preferences": {"favorite_app": "Chrome", "GROQ_API_KEY": "do-not-export"},
                        "notes": [{"text": "first"}, {"text": "PASSWORD=private"}],
                        "habits": {"open chrome": 2},
                        "total_commands": 2,
                    }
                ),
                encoding="utf-8",
            )
            manager = self._config(root, privacy_mode=True)
            service = MemoryControlService(memory_path=memory_path, config_manager=manager)

            view = service.view_memory()
            listing = service.list_memory()
            self.assertEqual(view["preferences"]["GROQ_API_KEY"], "***")
            self.assertEqual(view["notes"][1]["text"], "PASSWORD=***")
            self.assertEqual([entry["category"] for entry in listing], ["preference", "preference", "note", "note", "habit"])
            self.assertEqual(service.delete_note(0)["status"], "deleted")

            export_path = root / "private-memory-export.json"
            exported = service.export_memory(export_path)
            exported_text = export_path.read_text(encoding="utf-8")
            self.assertEqual(exported["status"], "exported")
            self.assertNotIn("do-not-export", exported_text)
            self.assertNotIn("PASSWORD=private", exported_text)
            self.assertIn("PASSWORD=***", exported_text)

            cleared = service.clear_memory()
            self.assertEqual(cleared["status"], "cleared")
            self.assertEqual(service.view_memory()["notes"], [])
            self.assertTrue(memory_path.exists())

    def test_missing_memory_can_be_viewed_without_creating_file(self):
        with TemporaryDirectory() as tmp:
            memory_path = Path(tmp) / "memory.json"
            service = MemoryControlService(memory_path=memory_path, config_manager=self._config(Path(tmp)))

            self.assertEqual(service.view_memory()["notes"], [])
            self.assertEqual(service.list_memory(), [])
            self.assertFalse(memory_path.exists())

    def test_memory_panel_model_uses_safe_service_actions(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            memory_path = root / "memory.json"
            memory_path.write_text(json.dumps({"preferences": {}, "notes": [{"text": "delete me"}], "habits": {}}), encoding="utf-8")
            model = MemoryPanelModel(MemoryControlService(memory_path=memory_path, config_manager=self._config(root)))

            self.assertEqual(model.view_model()["counts"]["notes"], 1)
            self.assertEqual(model.delete_note(0)["status"], "deleted")
            self.assertEqual(model.view_model()["counts"]["notes"], 0)


if __name__ == "__main__":
    unittest.main()
