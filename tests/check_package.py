#!/usr/bin/env python3
"""Static package checks for App Clone. No Android SDK, JDK, or device required."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def test_manifest_identity():
    manifest = (ROOT / "AndroidManifest.xml").read_text()
    assert 'package="com.lixingchi.appclone"' in manifest
    assert 'android:versionCode="1"' in manifest
    assert 'android:versionName="1.0"' in manifest
    assert 'android:label="App Clone"' in manifest
    assert 'android:theme="@style/Theme.AppClone"' in manifest


def test_entry_class_exists():
    entry = (ROOT / "assets/xposed_init").read_text().strip()
    assert entry == "com.lixingchi.appclone.MainHook", entry
    source = ROOT / "src" / Path(*entry.split(".")).with_suffix(".java")
    assert source.is_file(), source
    assert "class MainHook" in source.read_text()


def test_hook_covers_clone_fallbacks():
    source = (ROOT / "src/com/lixingchi/appclone/MainHook.java").read_text()
    assert "canAddMoreUsersOfType" in source
    assert "canAddMoreProfilesToUser" in source


def test_scope_is_system():
    scope = (ROOT / "res" / "values" / "scope.xml").read_text()
    assert "<item>android</item>" in scope, scope


def test_source_link_is_public():
    url = "https://github.com/doccstat/appclone"
    assert url in (ROOT / "README.md").read_text()
    assert url in (ROOT / "src/com/lixingchi/appclone/MainActivity.java").read_text()
    assert url in (ROOT / "src/com/lixingchi/appclone/SettingsActivity.java").read_text()


def test_build_self_check_package():
    build = (ROOT / "build.py").read_text()
    assert "com.lixingchi.appclone.LabelsTest" in build
    assert "API_SHA256" in build


def test_no_old_identity():
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts or "__pycache__" in path.parts:
            continue
        if path.suffix in {".png", ".jar", ".keystore", ".apk"}:
            continue
        if path.name == "check_package.py":
            continue
        text = path.read_text(errors="ignore")
        assert "pixelappclone" not in text, path
        assert "PixelAppClone" not in text, path


if __name__ == "__main__":
    for name, test in sorted(globals().items()):
        if name.startswith("test_"):
            test()
            print("ok", name)
