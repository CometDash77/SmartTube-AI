"""Check apksigner's verified certificate output against the pinned identity."""
from pathlib import Path
import re
import sys


def matches_identity(output, expected):
    # Android SDK versions use either Signer #1 or V2 Signer labels.
    certificates = re.findall(
        r"^(?:Signer #\d+|V\d+(?:\.\d+)? Signer:?) certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$",
        output,
        re.MULTILINE,
    )
    return bool(re.fullmatch(r"[0-9a-f]{64}", expected)) and {
        value.lower() for value in certificates
    } == {expected}


if __name__ == "__main__":
    expected = Path(sys.argv[1]).read_text(encoding="ascii").strip().lower()
    if not matches_identity(sys.stdin.read(), expected):
        raise SystemExit("APK certificate does not match the pinned project identity")
    print("APK certificate matches the pinned project identity")
