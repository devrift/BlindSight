import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from open_jarvis.config.manager import ConfigManager
from open_jarvis.config.paths import ConfigPaths
from open_jarvis.memory.controls import MemoryControlService
from open_jarvis.providers import GroqProvider, ProviderRouter


class CapturingChat:
    def __init__(self):
        self.completions = self
        self.calls = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        return type(
            "Resp",
            (),
            {"choices": [type("Choice", (), {"message": type("Message", (), {"content": '{"action":"talk","params":{}}'})()})()]},
        )()


class CapturingClient:
    def __init__(self):
        self.chat = CapturingChat()


class ProviderPrivacyTests(unittest.TestCase):
    def _manager(self, root: Path, **privacy) -> ConfigManager:
        manager = ConfigManager(paths=ConfigPaths(config_dir=root, settings_file=root / "settings.json"), env={})
        manager.load()
        updates = {
            "ai.cloud_fallback_enabled": True,
            "ai.groq_enabled": True,
            "ai.cloud_provider": "groq",
        }
        updates.update({f"privacy.{key}": value for key, value in privacy.items()})
        manager.set_many(updates)
        manager.save()
        manager.load()
        return manager

    def _write_memory(self, path: Path) -> None:
        path.write_text(
            json.dumps(
                {
                    "preferences": {"favorite_app": "private browser"},
                    "notes": [{"text": "private note"}],
                    "habits": {"private command": 3},
                    "total_commands": 3,
                }
            ),
            encoding="utf-8",
        )

    def test_privacy_mode_strips_memory_context_from_cloud_prompt(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write_memory(root / "memory.json")
            client = CapturingClient()
            router = ProviderRouter(
                config_manager=self._manager(root, privacy_mode=True),
                cloud_provider=GroqProvider(client=client, enabled=True, api_key="fake"),
                memory_path=root / "memory.json",
            )

            response = router.route("compose something")

            self.assertTrue(response.ok)
            system_prompt = client.chat.calls[0]["messages"][0]["content"]
            self.assertNotIn("private browser", system_prompt)
            self.assertNotIn("private note", system_prompt)
            self.assertNotIn("private command", system_prompt)

    def test_disabled_memory_strips_memory_context_from_cloud_prompt(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write_memory(root / "memory.json")
            client = CapturingClient()
            router = ProviderRouter(
                config_manager=self._manager(root, memory_enabled=False),
                cloud_provider=GroqProvider(client=client, enabled=True, api_key="fake"),
                memory_path=root / "memory.json",
            )

            response = router.route("compose something")

            self.assertTrue(response.ok)
            system_prompt = client.chat.calls[0]["messages"][0]["content"]
            self.assertNotIn("private browser", system_prompt)
            self.assertNotIn("private note", system_prompt)
            self.assertNotIn("private command", system_prompt)

    def test_memory_admin_controls_still_work_when_privacy_mode_is_enabled(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            memory_path = root / "memory.json"
            self._write_memory(memory_path)
            service = MemoryControlService(memory_path=memory_path, config_manager=self._manager(root, privacy_mode=True))

            self.assertEqual(len(service.list_memory()), 3)
            self.assertEqual(service.delete_note(0)["status"], "deleted")
            export_path = root / "private-memory-export.json"
            self.assertEqual(service.export_memory(export_path)["status"], "exported")
            self.assertEqual(service.clear_memory()["status"], "cleared")


if __name__ == "__main__":
    unittest.main()
