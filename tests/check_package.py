#!/usr/bin/env python3
"""Static package checks for App Clone. No Android SDK, JDK, or device required."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def test_manifest_identity():
    manifest = (ROOT / "AndroidManifest.xml").read_text()
    assert 'package="com.lixingchi.appclone"' in manifest
    assert 'android:versionCode="12"' in manifest
    assert 'android:versionName="2.2.0"' in manifest
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
    assert url in (ROOT / "src/com/lixingchi/appclone/SettingsActivity.java").read_text()


def test_build_self_check_package():
    build = (ROOT / "build.py").read_text()
    assert "testReleaseUnitTest" in build and "API_SHA256" in build
    core = (ROOT / "tests/CoreTest.java").read_text()
    for name in ("LabelsTest", "DiagnosticsTest", "ProfileLifecycleTest", "ShellTest", "BootPolicyTest"):
        assert name + ".main" in core
    assert "material:1.14.0" in (ROOT / "build.gradle").read_text()
    assert "distributionSha256Sum=" in (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()


def test_lifecycle_entrypoints():
    source = ROOT / "src/com/lixingchi/appclone"
    launcher = (source / "LaunchActivity.java").read_text()
    assert launcher.index("new Thread(") < launcher.index("Apps.launch(user, serial, app)")
    assert "CloneApplication.inventory(this).afterLaunch(user, result.ok())" in launcher
    assert "runOnUiThread" in launcher and "Could not open clone app" in launcher
    assert "LaunchActivity.class" in (source / "AppControls.java").read_text()
    assert "LaunchActivity.class" in (source / "Shortcuts.java").read_text()
    assert 'AppControls.power(activity, profile)' in (source / "ProfileControls.java").read_text()
    assert "Clones.setRunning(profile.clone.id, !running)" in (source / "AppControls.java").read_text()
    assert "ProfileStart.command(id)" in (source / "Clones.java").read_text()
    assert "ProfileStart.apkPath = getApplicationInfo().sourceDir" in (source / "CloneApplication.java").read_text()
    for workflow in ("android.yml", "release.yml"):
        workflow_text = (ROOT / ".github/workflows" / workflow).read_text()
        assert "build.py --self-check" in workflow_text
        assert "build.py --lint" in workflow_text


def test_background_and_launch_wiring():
    source = ROOT / "src/com/lixingchi/appclone"
    assert 'AppCommand.command("launch"' in (source / "Apps.java").read_text()
    assert "Clones.verify(" not in (source / "LaunchActivity.java").read_text()
    assert "BackgroundControls.open(activity, entry, profile)" in (source / "AppControls.java").read_text()
    bridge = (source / "AppCommand.java").read_text()
    assert "android:run_any_in_background" in bridge
    assert "Process.myUid() != 0" in bridge
    assert "setUidMode" not in bridge and "deviceidle" not in bridge
    for name in ("AppCommandTest.java", "BackgroundPolicyTest.java"):
        assert (ROOT / "tests" / name).is_file()


def test_root_bootstrap_wiring():
    source = ROOT / "src/com/lixingchi/appclone"
    shell = (source / "Shell.java").read_text()
    assert 'new ProcessBuilder("su", "-c", command)' in shell
    assert 'new ProcessBuilder("/system/bin/su", "-c", command)' in shell
    report = (source / "Diagnostics.java").read_text()
    assert 'android.os.Process.myUid()' in report and 'System.getenv("PATH")' in report
    assert 'shell("id")' in report


def test_inline_ui_and_rename_wiring():
    source = ROOT / "src/com/lixingchi/appclone"
    main = (source / "MainActivity.java").read_text()
    assert "InventoryList.Item.instance(entry, profile, false)" in main
    assert "setupWithViewPager(pager)" in main
    assert "setMaxLines(3)" not in main
    assert 'Ui.quiet(activity, "Manage")' not in (source / "AppControls.java").read_text()
    assert "Clones.rename(profile.clone.id, profile.serial, typed)" in (source / "ProfileControls.java").read_text()
    rename = (source / "ProfileRename.java").read_text()
    assert "Process.myUid() != 0" in rename and 'getMethod("setUserName"' in rename
    assert "startProfile" not in rename and "stop-user" not in rename


def test_boot_wiring():
    android = "{http://schemas.android.com/apk/res/android}"
    manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
    permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")}
    assert permissions == {"android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.QUERY_ALL_PACKAGES"}
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
    assert "CloneApplication.inventory(this).invalidate()" in job
    settings = (source / "SettingsActivity.java").read_text()
    assert "BootPolicy.LABELS" in settings and "boot.save(mode)" in settings
    assert "jobs.cancel(BootReceiver.JOB_ID)" in settings


def test_no_old_identity():
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts or "__pycache__" in path.parts:
            continue
        if any(part in {".gradle", "build", "dist"} for part in path.parts):
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
