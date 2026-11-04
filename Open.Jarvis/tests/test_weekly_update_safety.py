import unittest
from unittest.mock import patch

from open_jarvis.evaluation import haftalik_guncelleme


class WeeklyUpdateSafetyTests(unittest.TestCase):
    def test_groq_failure_returns_safe_error_without_secret_text(self):
        class FailingCompletions:
            def create(self, **_kwargs):
                raise RuntimeError("bad api key sk-real-looking-secret")

        class FailingChat:
            completions = FailingCompletions()

        class FailingClient:
            chat = FailingChat()

        with patch.object(haftalik_guncelleme, "client", FailingClient()):
            result = haftalik_guncelleme.analyze_with_groq([{"name": "demo", "stars": 1, "description": "demo"}])

        self.assertEqual(result, "Groq analysis failed: provider_auth_failed")
        self.assertNotIn("sk-real-looking-secret", result)


if __name__ == "__main__":
    unittest.main()
