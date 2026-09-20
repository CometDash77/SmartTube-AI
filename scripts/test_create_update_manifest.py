import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("manifest", Path(__file__).with_name("create-update-manifest.py"))
manifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)


class UpdateManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apks = Path(self.temp.name)
        for abi in ("universal", "arm64-v8a", "armeabi-v7a", "x86"):
            (self.apks / f"SmartTube_beta_32.54_{abi}.apk").touch()

    def build(self):
        return manifest.create_manifest("CometDash77/SmartTube-AI", "stbeta-32.54-rc28", "32.54", 2444, self.apks)

    def test_updater_contract_and_architecture_routing(self):
        feed = self.build()
        self.assertEqual(feed["32.54"]["versionCode"], 2444)
        package = feed["package"]
        self.assertTrue(package["downloadUrl"].endswith("_universal.apk"))
        self.assertEqual(package["downloadUrlList_x86_64"], package["downloadUrlList_x86"])
        for abi in ("arm64-v8a", "armeabi-v7a", "x86"):
            self.assertTrue(package[f"downloadUrlList_{abi}"][0].endswith(f"_{abi}.apk"))
            self.assertIn("/CometDash77/SmartTube-AI/releases/download/stbeta-32.54-rc28/", package[f"downloadUrlList_{abi}"][0])

    def test_missing_architecture_refuses_publication(self):
        next(self.apks.glob("*_x86.apk")).unlink()
        with self.assertRaises(ValueError):
            self.build()

    def test_ambiguous_architecture_refuses_publication(self):
        (self.apks / "other_x86.apk").touch()
        with self.assertRaises(ValueError):
            self.build()

    def test_upstream_repository_refused(self):
        with self.assertRaises(ValueError):
            manifest.create_manifest("yuliskov/SmartTube", "32.54", "32.54", 2444, self.apks)


if __name__ == "__main__":
    unittest.main()
