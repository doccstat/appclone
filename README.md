# App Clone

Use the same Android app with separate accounts and data. App Clone manages
Android's native clone profiles and raises their limits through an Xposed module.
It doesn't modify the original APKs.

[**Download APK**](https://github.com/doccstat/appclone/releases) ·
[Source](https://github.com/doccstat/appclone) ·
[Report a bug](https://github.com/doccstat/appclone/issues/new/choose)

## Requirements

- Android 13+ with native AOSP clone-profile support
- Root access
- LSPosed or a compatible framework supporting legacy Xposed API 82

The clone engine has been tested on Pixel / Android 17. Other Android versions
and firmware remain unverified; not every app supports cloning.

## Get started

1. Download the APK and install it in your main profile (**user 0**).
2. Enable the module for **System Framework** (`android`) and reboot.
3. Open App Clone, grant root, and tap **Add clone app**.
4. Pick an app, then choose an existing profile or create a new one.
5. Tap **▶ Open** beside the clone, or pin a home-screen shortcut from its **⋮** menu.

Several apps can share a profile. Another instance of the same app needs a
different profile.

## Manage your clones

- **Apps:** see every clone beneath its app. Use the instance's **⋮** menu to
  disable, enable, force-stop, restrict background activity or remove it.
- **Profiles:** start, stop, rename or delete a profile and manage its apps.
- **Settings:** customize shortcut labels, choose a language and set what happens
  after reboot. Reboot actions wait for main-user unlock and require root; they
  don't prevent initial profile startup.

System apps are hidden by default; reveal them with **Show system apps**.
Use **Refresh** after making changes outside App Clone.

## Before changing or deleting data

- **Stopping a profile** pauses all its apps and notifications but keeps data.
  Opening a clone starts that profile again.
- **Disabling an app** keeps its data but stops it and its notifications until
  you enable it. **Force stop** is temporary, not a permanent freeze.
- **Background restrictions** can delay notifications. Use **Restore previous**
  before clearing App Clone's data or uninstalling it; otherwise Android's
  restriction can remain without a restore record.
- **Deleting a profile erases all its app data**, including hidden system apps.
  Removing an app deletes only its data in the selected profile. Main-user,
  work and private profiles aren't managed here. Back up important data first.

Changing system apps can break other apps in that profile. Root and framework
changes can also affect boot; keep a recovery method available.

## Languages

From version **2.3.1**: English, Simplified Chinese, Traditional Chinese, Spanish,
Hindi, Bengali, Russian, Japanese and Turkish. Switch in **Settings → App language**;
choose a bundled language or **System default**. Translations are AI-authored;
[corrections are welcome](docs/i18n.md).

## Help and contributions

If cloning fails, check root access and System Framework scope. Use
**Export diagnostics** for a bug report, and remove personal information before
sharing it. App Clone has no Internet permission.

<details>
<summary>Build from source</summary>

In the source repository, use JDK 21, Android SDK platform/build-tools 36,
Python 3.11+ and the [Xposed API 82 jar](https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar).
Set the paths for your machine, then run:

```sh
export JAVA_HOME=/path/to/jdk21 ANDROID_HOME=/path/to/sdk
export XPOSED_API_JAR=/path/to/api-82.jar
export BUILD_DIR="$HOME/.cache/appclone/build"
python3 build.py --self-check
python3 build.py --lint
python3 build.py
```

Unsigned APK: `$BUILD_DIR/AppClone-unsigned.apk`.
See the [localization guide](docs/i18n.md) for translation checks and contributions.

</details>

[GPL-3.0-only](https://github.com/doccstat/appclone/blob/main/LICENSE).
Not affiliated with Google, Android or LSPosed.
