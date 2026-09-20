"""Read-only checks for development handoff documents; no Android build required."""
import argparse
import datetime
import json
from pathlib import Path
import re


def problems(path, text):
    errors = []
    for number, line in enumerate(text.splitlines(), 1):
        if line.rstrip() != line:
            errors.append(f"{path}:{number}: trailing whitespace")
        if r"\r\n" in line:
            errors.append(f"{path}:{number}: literal escaped CRLF; inspect intended formatting")
    if path.parent.name == "development" and re.fullmatch(r"\d{4}-\d{2}-\d{2}", path.stem):
        try:
            datetime.date.fromisoformat(path.stem)
        except ValueError:
            errors.append(f"{path}: invalid calendar date")
        for heading in ("Scope", "Progress", "Verification", "Outstanding work and blockers", "Next step"):
            if text.count(f"\n## {heading}\n") != 1:
                errors.append(f"{path}: expected exactly one {heading!r} section")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()
    errors = []
    for path in args.paths:
        try:
            text = path.read_text(encoding="utf-8-sig")
            if path.suffix == ".json":
                json.loads(text)
            else:
                errors.extend(problems(path, text))
        except (OSError, ValueError) as error:
            errors.append(f"{path}: {type(error).__name__}")
    print("\n".join(errors) if errors else f"PASS: {len(args.paths)} files")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
