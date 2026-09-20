import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("identity", Path(__file__).with_name("verify-signing-identity.py"))
identity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(identity)


class SigningIdentityTest(unittest.TestCase):
    fingerprint = "a" * 64

    def test_sdk_signer_formats(self):
        for label in ("Signer #1", "V2 Signer:", "V3.1 Signer:"):
            with self.subTest(label=label):
                self.assertTrue(identity.matches_identity(f"{label} certificate SHA-256 digest: {self.fingerprint}\n", self.fingerprint))

    def test_public_key_digest_is_not_certificate(self):
        self.assertFalse(identity.matches_identity(f"V2 Signer: public key SHA-256 digest: {self.fingerprint}", self.fingerprint))

    def test_wrong_or_additional_signer_refused(self):
        output = "Signer #1 certificate SHA-256 digest: " + "b" * 64
        self.assertFalse(identity.matches_identity(output, self.fingerprint))
        output += f"\nSigner #2 certificate SHA-256 digest: {self.fingerprint}"
        self.assertFalse(identity.matches_identity(output, self.fingerprint))

    def test_missing_certificate_refused(self):
        self.assertFalse(identity.matches_identity("Verifies", self.fingerprint))


if __name__ == "__main__":
    unittest.main()
