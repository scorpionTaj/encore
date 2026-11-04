#!/usr/bin/env python3
"""Checks that every Android locale carries the same strings as the base locale.

The base locale is `android/app/src/main/res/values/strings.xml`. Every other
`values-*/strings.xml` must define the same names, or the script lists what
is missing and exits non-zero. Extra keys in a locale are allowed, so a
translation can land before the next base string does.

Run from the repository root:  python3 scripts/i18n-coverage-android.py
"""

import re
import sys
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main" / "res"
STRING = re.compile(r'<string name="([^"]+)"')


def names(path: Path) -> set[str]:
    return set(STRING.findall(path.read_text(encoding="utf-8")))


def main() -> int:
    base = RES / "values" / "strings.xml"
    if not base.exists():
        print(f"error: no base locale at {base}")
        return 2
    base_names = names(base)
    locales = {
        folder.name: names(folder / "strings.xml")
        for folder in sorted(RES.iterdir())
        if folder.is_dir() and folder.name.startswith("values-") and (folder / "strings.xml").exists()
    }
    # A key no locale carries, such as the app's name, is deliberately written once.
    translated = set[str]().union(*locales.values()) if locales else set()
    missing_somewhere = False
    for locale, present in locales.items():
        missing = base_names - present
        real = missing & translated
        if real:
            missing_somewhere = True
            print(f"{locale}: missing {len(real)} string(s) another locale carries:")
            for name in sorted(real):
                print(f"  {name}")
    if missing_somewhere:
        return 1
    print("locales match the base strings")
    return 0


if __name__ == "__main__":
    sys.exit(main())
