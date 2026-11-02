#!/usr/bin/env python3
"""CI policy around Polygo; Android lint remains the resource-format authority."""
import argparse
import json
from pathlib import Path
import re
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PENDING = ("new", "stale", "untranslated", "needs_review")


def locale_folder(locale):
    if not isinstance(locale, str) or not re.fullmatch(r"[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", locale):
        raise ValueError(f"Use an explicit BCP-47 locale, not a path or Android qualifier: {locale!r}")
    if locale in ("en", "en-XA", "ar-XB"):
        raise ValueError(f"Not a production translation target: {locale}")
    parts = locale.split("-")
    if len(parts) == 1:
        qualifier = locale
    elif len(parts) == 2 and len(parts[1]) == 2:
        qualifier = parts[0] + "-r" + parts[1].upper()
    else:
        qualifier = "b+" + "+".join(parts)
    return f"values-{qualifier}"


def check(root=ROOT, generation=False, status=None):
    cfg = tomllib.loads((root / "polygo.toml").read_text())
    locales = cfg["target_locales"]
    if not isinstance(locales, list):
        raise ValueError("target_locales must be a list")
    folders = [locale_folder(locale) for locale in locales]
    if len(set(folders)) != len(folders):
        raise ValueError("Duplicate target locale/folder")
    expected = {root / "res" / folder / "strings.xml" for folder in folders}
    actual = set((root / "res").glob("values-*/strings.xml"))
    if actual - expected or (not generation and expected - actual):
        raise ValueError(f"Catalogs and target_locales disagree: extra={sorted(actual - expected)}, missing={sorted(expected - actual)}")
    for catalog in [root / "res/values/strings.xml", *actual]:
        ET.parse(catalog)  # Polygo alone does not validate XML attribute/namespace syntax.
    lock = tomllib.loads((root / "polygo.lock").read_text())
    if lock.get("version") != 2:
        raise ValueError("Require lockfile v2; legacy migration must be explicit")
    if generation:
        provider = cfg["provider"]
        if not locales or not provider.get("model"):
            raise ValueError("Choose target_locales and provider.model in polygo.toml before generation")
        if provider["kind"] != "ollama" or provider.get("base_url") != "http://127.0.0.1:11434":
            raise ValueError("This workflow provisions local Ollama only; no remote inference fallback")
        if ":" not in provider["model"] or re.search(r"\s", provider["model"]):
            raise ValueError("Use an explicit model:tag and pin its digest in CI")
        if cfg.get("memory", True) or cfg.get("context", True):
            raise ValueError("CI sends catalog text/comments only: memory and context must be false")
    if status is not None:
        reported = status["locales"]
        if set(reported) != set(locales):
            raise ValueError("Status must cover every configured locale")
        pending = {locale: {state: counts[state] for state in PENDING if counts[state]}
                   for locale, counts in reported.items()}
        pending = {locale: counts for locale, counts in pending.items() if counts}
        if pending:
            raise ValueError(f"Translations incomplete/stale/quarantined: {pending}")
    return cfg


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generation", action="store_true", help="Allow missing target files; require explicit local model settings")
    parser.add_argument("--status", type=Path, help="Require a complete Polygo status --json report")
    args = parser.parse_args()
    try:
        check(generation=args.generation, status=json.loads(args.status.read_text()) if args.status else None)
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        raise SystemExit(str(error))
