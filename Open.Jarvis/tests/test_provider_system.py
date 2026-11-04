import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from open_jarvis.config.manager import ConfigManager
from open_jarvis.config.paths import ConfigPaths
from open_jarvis.providers import GroqProvider, LocalProvider, ProviderRequest, ProviderResponse, ProviderRouter


class DummyChat:
    def __init__(self, content='{"action":"talk","params":{},"response":"Cloud routed."}'):
        self.completions = self
        self.content = content
        self.calls = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        return type(
            "Resp",
            (),
            {"choices": [type("Choice", (), {"message": type("Message", (), {"content": self.content})()})()]},
        )()


class DummyClient:
    def __init__(self, content='{"action":"talk","params":{},"response":"Cloud routed."}'):
        self.chat = DummyChat(content)


class SpyCloudProvider:
    name = "groq"

    def __init__(self):
        self.calls = []

    def analyze(self, request):
        self.calls.append(request)
        return type(
            "Response",
            (),
            {
                "ok": True,
                "provider": "groq",
                "status": "success",
                "action": {"action": "talk", "params": {}, "response": "Cloud"},
                "error": None,
                "fallback_used": False,
            },
        )()


class FailingProvider:
    name = "failing"

    def analyze(self, _request):
        raise RuntimeError("bad api key sk-real-looking-secret")


class ProviderSystemTests(unittest.TestCase):
    def _manager(self, root: Path, **settings) -> ConfigManager:
        manager = ConfigManager(paths=ConfigPaths(config_dir=root, settings_file=root / "settings.json"), env={})
        manager.load()
        if settings:
            manager.set_many(settings)
            manager.save()
            manager.load()
        return manager

    def test_provider_request_repr_does_not_expose_prompt_or_context(self):
        request = ProviderRequest(command="secret prompt", context="GROQ_API_KEY=secret", metadata={"token": "hidden"})

        rendered = repr(request)

        self.assertNotIn("secret prompt", rendered)
        self.assertNotIn("GROQ_API_KEY=secret", rendered)
        self.assertNotIn("hidden", rendered)

    def test_provider_response_repr_does_not_expose_action_text(self):
        response = ProviderResponse(
            provider="groq",
            status="error",
            action={"action": "talk", "response": "GROQ_API_KEY=secret"},
            text="sk-real-looking-secret",
            error="provider_auth_failed",
        )

        rendered = repr(response)

        self.assertNotIn("GROQ_API_KEY=secret", rendered)
        self.assertNotIn("sk-real-looking-secret", rendered)
        self.assertIn("provider_auth_failed", rendered)

    def test_local_provider_handles_supported_command_without_cloud(self):
        provider = LocalProvider()

        response = provider.analyze(ProviderRequest(command="open chrome"))

        self.assertTrue(response.ok)
        self.assertEqual(response.provider, "local")
        self.assertEqual(response.action["action"], "open_app")

    def test_router_defaults_to_local_and_does_not_call_cloud_for_supported_command(self):
        with TemporaryDirectory() as tmp:
            cloud = GroqProvider(client=DummyClient(), enabled=True, api_key="fake")
            router = ProviderRouter(config_manager=self._manager(Path(tmp)), cloud_provider=cloud)

            response = router.route("open chrome")

            self.assertTrue(response.ok)
            self.assertEqual(response.provider, "local")
            self.assertEqual(cloud.client.chat.calls, [])

    def test_router_default_config_disables_cloud_even_when_key_exists(self):
        with TemporaryDirectory() as tmp:
            manager = ConfigManager(
                paths=ConfigPaths(config_dir=Path(tmp), settings_file=Path(tmp) / "settings.json"),
                env={"GROQ_API_KEY": "fake"},
            )
            manager.load()
            cloud = SpyCloudProvider()
            router = ProviderRouter(config_manager=manager, cloud_provider=cloud)

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(cloud.calls, [])

    def test_unsupported_command_without_cloud_fallback_returns_safe_failure(self):
        with TemporaryDirectory() as tmp:
            cloud = GroqProvider(client=DummyClient(), enabled=True, api_key="fake")
            router = ProviderRouter(config_manager=self._manager(Path(tmp)), cloud_provider=cloud)

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(response.status, "unsupported")
            self.assertEqual(cloud.client.chat.calls, [])
            self.assertIn("local", response.error.lower())

    def test_groq_disabled_prevents_cloud_provider_call(self):
        with TemporaryDirectory() as tmp:
            manager = self._manager(
                Path(tmp),
                **{
                    "ai.cloud_fallback_enabled": True,
                    "ai.groq_enabled": False,
                    "ai.cloud_provider": "groq",
                },
            )
            cloud = SpyCloudProvider()
            router = ProviderRouter(config_manager=manager, cloud_provider=cloud)

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(cloud.calls, [])

    def test_non_groq_cloud_provider_prevents_groq_call(self):
        with TemporaryDirectory() as tmp:
            manager = self._manager(
                Path(tmp),
                **{
                    "ai.cloud_fallback_enabled": True,
                    "ai.groq_enabled": True,
                    "ai.cloud_provider": "local",
                },
            )
            cloud = SpyCloudProvider()
            router = ProviderRouter(config_manager=manager, cloud_provider=cloud)

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(cloud.calls, [])

    def test_cloud_fallback_requires_explicit_config_and_returns_action(self):
        with TemporaryDirectory() as tmp:
            manager = self._manager(
                Path(tmp),
                **{
                    "ai.cloud_fallback_enabled": True,
                    "ai.groq_enabled": True,
                    "ai.cloud_provider": "groq",
                },
            )
            cloud = GroqProvider(client=DummyClient(), enabled=True, api_key="fake")
            router = ProviderRouter(config_manager=manager, cloud_provider=cloud)

            response = router.route("compose a haiku about release gates")

            self.assertTrue(response.ok)
            self.assertEqual(response.provider, "groq")
            self.assertTrue(response.fallback_used)
            self.assertEqual(response.action["action"], "talk")
            self.assertEqual(len(cloud.client.chat.calls), 1)

    def test_invalid_cloud_provider_config_fails_safely_without_cloud_call(self):
        with TemporaryDirectory() as tmp:
            manager = self._manager(
                Path(tmp),
                **{
                    "ai.cloud_fallback_enabled": True,
                    "ai.groq_enabled": True,
                    "ai.cloud_provider": "invalid-provider",
                },
            )
            cloud = SpyCloudProvider()
            router = ProviderRouter(config_manager=manager, cloud_provider=cloud)

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(cloud.calls, [])

    def test_groq_provider_missing_key_returns_unavailable_without_network(self):
        provider = GroqProvider(client=DummyClient(), enabled=True, api_key="")

        response = provider.analyze(ProviderRequest(command="hello", allow_cloud=True))

        self.assertFalse(response.ok)
        self.assertEqual(response.status, "unavailable")
        self.assertEqual(provider.client.chat.calls, [])

    def test_groq_provider_errors_are_safe_and_do_not_expose_api_key(self):
        class FailingChat:
            def __init__(self):
                self.completions = self

            def create(self, **_kwargs):
                raise RuntimeError("bad api key sk-real-looking-secret")

        class FailingClient:
            def __init__(self):
                self.chat = FailingChat()

        provider = GroqProvider(client=FailingClient(), enabled=True, api_key="sk-real-looking-secret")

        response = provider.analyze(ProviderRequest(command="hello", allow_cloud=True))

        self.assertFalse(response.ok)
        self.assertNotIn("sk-real-looking-secret", response.error)

    def test_router_converts_local_provider_exception_to_safe_failure(self):
        with TemporaryDirectory() as tmp:
            router = ProviderRouter(
                config_manager=self._manager(Path(tmp)),
                local_provider=FailingProvider(),
                cloud_provider=SpyCloudProvider(),
            )

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(response.status, "unsupported")
            self.assertEqual(response.error, "local_provider_error")
            self.assertNotIn("sk-real-looking-secret", repr(response))

    def test_router_converts_cloud_provider_exception_to_safe_failure(self):
        with TemporaryDirectory() as tmp:
            manager = self._manager(
                Path(tmp),
                **{
                    "ai.cloud_fallback_enabled": True,
                    "ai.groq_enabled": True,
                    "ai.cloud_provider": "groq",
                },
            )
            router = ProviderRouter(config_manager=manager, cloud_provider=FailingProvider())

            response = router.route("compose a haiku about release gates")

            self.assertFalse(response.ok)
            self.assertEqual(response.status, "error")
            self.assertEqual(response.error, "provider_error")
            self.assertTrue(response.fallback_used)
            self.assertNotIn("sk-real-looking-secret", repr(response))

    def test_groq_provider_client_factory_exception_returns_safe_failure(self):
        def failing_factory(_api_key):
            raise RuntimeError("bad api key sk-real-looking-secret")

        provider = GroqProvider(client_factory=failing_factory, enabled=True, api_key="sk-real-looking-secret")

        response = provider.analyze(ProviderRequest(command="hello", allow_cloud=True))

        self.assertFalse(response.ok)
        self.assertEqual(response.error, "provider_auth_failed")
        self.assertNotIn("sk-real-looking-secret", repr(response))


if __name__ == "__main__":
    unittest.main()
