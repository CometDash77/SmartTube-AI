"""Create the existing Android updater's feed from the verified release assets."""
import argparse
import json
from pathlib import Path
from urllib.parse import quote


def create_manifest(repository, tag, version, code, apk_dir):
    if repository != "CometDash77/SmartTube-AI":
        raise ValueError("Release feed must belong to this fork")
    base = f"https://github.com/{repository}/releases/download/{quote(tag, safe='')}/"
    package = {}
    for abi in ("universal", "armeabi-v7a", "arm64-v8a", "x86"):
        matches = list(apk_dir.glob(f"*_{abi}.apk"))
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one {abi} APK, found {len(matches)}")
        key = "downloadUrl" if abi == "universal" else f"downloadUrlList_{abi}"
        url = base + quote(matches[0].name)
        package[key] = url if abi == "universal" else [url]
    # x86_64 devices use the compatible x86 build, not the ARM universal APK.
    package["downloadUrlList_x86_64"] = package["downloadUrlList_x86"]
    return {
        "package": package,
        version: {
            "versionCode": code,
            "changelog": [
                "SmartTube AI: upstream 32.54 plus AI subtitles, context, segmentation and export.",
                "Dedicated project signature; updates come only from SmartTube-AI stable releases.",
            ],
        },
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--apk-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = create_manifest(args.repository, args.tag, args.version, args.code, args.apk_dir)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
