#!/usr/bin/env python3
"""Static package checks for App Clone. No Android SDK, JDK, or device required."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def test_manifest_identity():
    manifest = (ROOT / "AndroidManifest.xml").read_text()
    assert 'package="com.lixingchi.appclone"' in manifest
    assert 'android:versionCode="6"' in manifest
    assert 'android:versionName="1.5"' in manifest
    assert 'android:minSdkVersion="33"' in manifest
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
    assert "com.lixingchi.appclone.PinGroupsTest" in build
    assert "com.lixingchi.appclone.DiagnosticsTest" in build
    assert "com.lixingchi.appclone.ProfileLifecycleTest" in build
    assert "com.lixingchi.appclone.ShellTest" in build
    assert "com.lixingchi.appclone.BootPolicyTest" in build
    assert "API_SHA256" in build


def test_lifecycle_entrypoints():
    source = ROOT / "src/com/lixingchi/appclone"
    launcher = (source / "LaunchActivity.java").read_text()
    assert launcher.index("new Thread(") < launcher.index("Apps.launch(user, app)")
    assert "runOnUiThread" in launcher and "Could not open clone app" in launcher
    assert "LaunchActivity.class" in (source / "CloneDetailActivity.java").read_text()
    assert "LaunchActivity.class" in (source / "Shortcuts.java").read_text()
    main = (source / "MainActivity.java").read_text()
    assert 'clone.running ? "Stop" : "Start"' in main
    assert "Clones.setRunning(id, running)" in main
    for workflow in ("android.yml", "release.yml"):
        assert "build.py --self-check" in (ROOT / ".github/workflows" / workflow).read_text()


def test_boot_wiring():
    android = "{http://schemas.android.com/apk/res/android}"
    manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
    permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")}
    assert permissions == {"android.permission.RECEIVE_BOOT_COMPLETED"}
    receiver = manifest.find("application/receiver")
    assert receiver.get(android + "name") == ".BootReceiver"
    assert receiver.get(android + "exported") == "false"
    assert receiver.find("intent-filter/action").get(android + "name") == "android.intent.action.BOOT_COMPLETED"
    service = manifest.find("application/service")
    assert service.get(android + "name") == ".BootJob"
    assert service.get(android + "permission") == "android.permission.BIND_JOB_SERVICE"
    assert service.get(android + "exported") == "false"
    source = ROOT / "src/com/lixingchi/appclone"
    for name in ("BootReceiver.java", "BootJob.java"):
        assert "Process.myUid() / 100000 != 0" in (source / name).read_text()
    receiver_source = (source / "BootReceiver.java").read_text()
    assert "Intent.ACTION_BOOT_COMPLETED.equals" in receiver_source
    assert "scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS" in receiver_source
    assert "Shell.root" not in receiver_source and "goAsync" not in receiver_source
    assert "setPeriodic" not in receiver_source and "setPersisted" not in receiver_source
    assert "setMinimumLatency(10_000)" in receiver_source
    job = (source / "BootJob.java").read_text()
    assert job.index("new Thread(") < job.index("policy.apply(")
    assert "synchronized (RUN_LOCK)" in job
    settings = (source / "SettingsActivity.java").read_text()
    assert "BootPolicy.LABELS" in settings and "boot.save(mode)" in settings
    assert "jobs.cancel(BootReceiver.JOB_ID)" in settings


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
