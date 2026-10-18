import contextlib
import importlib.util
import io
import os
import pathlib
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("appclone_build", ROOT / "build.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BuildTests(unittest.TestCase):
    def test_certificate_digest_normalizes_colons(self):
        self.assertEqual(module.certificate_digest("AA:" * 31 + "AA"), "aa" * 32)

    def test_certificate_digest_rejects_bad_values(self):
        for value in ("", "00", "g" * 64, "0" * 63):
            with self.assertRaises(ValueError):
                module.certificate_digest(value)

    def assert_refused(self, args, env, message):
        with mock.patch.dict(os.environ, env, clear=True), \
                mock.patch("sys.argv", ["build.py", *args]):
            with mock.patch.object(module.subprocess, "run") as run, \
                    contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as exit_code:
                    module.main()
                self.assertIn(message, str(exit_code.exception))
                run.assert_not_called()

    def test_release_requires_explicit_key_material(self):
        self.assert_refused(["--release"], {}, "Release signing requires")

    def test_release_never_generates_missing_keystore(self):
        with tempfile.TemporaryDirectory() as temp:
            env = dict(SIGNING_KEYSTORE=str(pathlib.Path(temp) / "missing.keystore"),
                       SIGNING_STORE_PASSWORD="dummy", SIGNING_KEY_PASSWORD="dummy",
                       SIGNING_KEY_ALIAS="dummy", SIGNING_CERT_SHA256="0" * 64)
            self.assert_refused(["--release"], env, "refusing to generate a replacement")

    def test_release_requires_certificate_pin(self):
        with tempfile.TemporaryDirectory() as temp:
            key = pathlib.Path(temp) / "placeholder.keystore"
            key.touch()
            env = dict(SIGNING_KEYSTORE=str(key), SIGNING_STORE_PASSWORD="dummy",
                       SIGNING_KEY_PASSWORD="dummy", SIGNING_KEY_ALIAS="dummy")
            self.assert_refused(["--release"], env, "SIGNING_CERT_SHA256")

    def test_manifest_has_stable_identity(self):
        root = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        self.assertEqual(root.attrib["package"], "com.lixingchi.appclone")
        self.assertGreater(
            int(root.attrib["{http://schemas.android.com/apk/res/android}versionCode"]), 0)


if __name__ == "__main__":
    unittest.main()
