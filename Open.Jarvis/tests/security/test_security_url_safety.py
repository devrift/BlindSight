import unittest

from open_jarvis.integrations.url_safety import normalize_web_url


class StrictUrlSafetyTests(unittest.TestCase):
    def test_rejects_local_or_script_protocols(self):
        for url in ("file:///C:/Windows/System32", "javascript:alert(1)", "data:text/plain,hello", "powershell:Start-Process calc"):
            with self.subTest(url=url):
                self.assertIsNone(normalize_web_url(url))

    def test_rejects_unc_and_backslash_paths(self):
        for url in ("\\\\server\\share", "//server/share", "example.com\\share"):
            with self.subTest(url=url):
                self.assertIsNone(normalize_web_url(url))

    def test_accepts_http_https_and_normalized_domain(self):
        self.assertEqual(normalize_web_url("https://example.com/path"), "https://example.com/path")
        self.assertEqual(normalize_web_url("example.com"), "https://example.com")


if __name__ == "__main__":
    unittest.main()
