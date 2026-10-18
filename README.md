# App Clone

App Clone is an Xposed/LSPosed module that raises the AOSP clone-profile limits
on AOSP-derived firmware, so many independent clone users can be created and run
at once — similar to Moto's app clone. It also ships a small launcher app for
creating clones, installing apps into them, and pinning labelled shortcuts.

The firmware already ships the clone-profile user type
(`android.os.usertype.profile.CLONE`) and the framework APIs; it is switched off
and its limits are small. This module only raises the limits.

Source and support: <https://github.com/doccstat/appclone>.

## What it does

Hooks in `system_server`:

- `com.android.server.pm.UserTypeDetails.getMaxAllowed` and
  `getMaxAllowedPerParent` return `21472` for the CLONE user type (stock: a few
  in total, one per parent).
- `android.os.UserManager.getMaxSupportedUsers` returns `21472` (stock: 1).
- `com.android.server.am.UserController.setInitialConfig` forces the
  running-user budget to `21472` (stock: 3), so clones stay started and
  auto-start at boot.

## Requirements

- Android 16+ (API 36+). Verified on Android 17 (API 37), Pixel 11 Pro Fold.
- Root with LSPosed (or another legacy-Xposed-API framework) that can load a
  module into `system_server`.
- Root access for the launcher app (`su`), granted through KernelSU (or another
  root manager) on first use.

## Download

Signed builds are published on the
[releases page](https://github.com/doccstat/appclone/releases). Each release
includes the APK and its SHA-256 checksum.

## Build

```shell
ANDROID_HOME=<sdk> JAVA_HOME=<jdk> XPOSED_API_JAR=<api-82.jar> python3 build.py
```

The compile-only API 82 JAR is available from
<https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar> with SHA-256
`f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`.

Set `BUILD_DIR` to choose the output directory (default `~/.cache/appclone`).
Optionally set `SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_PASSWORD`, and `SIGNING_KEY_ALIAS` to sign a development build.

`python3 build.py --self-check` compiles `tests/` and runs the shortcut-label
formatter check; it does not produce an APK. `python3 build.py --release`
requires the signing variables plus `SIGNING_CERT_SHA256`, refuses to generate a
missing key, and verifies the APK certificate against the pin.

## Install

1. Install the APK for user 0.
2. Enable it in LSPosed and set its scope to `system` (`android`).
3. Reboot.

## Usage

Launch the **App Clone** app (the same APK is the module and a small launcher
app) and grant it root in KernelSU on first use:

- The list shows every clone profile and whether it is running.
- **New profile** creates one or more clones; the name is suggested for you.
- Tap a profile to pick its apps. Tap an app row to install it into the clone,
  **Open** to run it there, **Delete clone** to remove the profile and its data.
- **Delete all profiles** (⋮ menu) removes every clone; your main user is
  untouched.
- **Pin a shortcut** (⋮ menu) opens a picker of every app in every clone and
  pins a home-screen shortcut for the chosen entry through
  `ShortcutManager.requestPinShortcut`. The shortcut uses the app's normal
  clone-badged icon, so it sits next to ordinary app icons and opens that app
  directly in that clone. The launcher shows its own add confirmation, and the
  shortcut keeps working after reboots.
- **Shortcut settings** (⋮ menu) controls how those shortcuts are named and
  whether new ones are created automatically:
  - *Prefix clone app names* makes the home-screen label `12: Chrome` by
    default (`{id}: {name}`). The format field accepts `{id}`, `{name}` and
    `{clone}`; the buttons below it insert those tokens. Any other text,
    including colons, is kept. A format without `{name}` still gets the app name
    appended, so a shortcut never loses the app it opens.
  - *Auto-pin new clone apps* asks the launcher for a shortcut as each app is
    toggled on inside a clone. The launcher still shows its normal confirmation.
  The format applies to shortcuts created after the change; re-pin an existing
  shortcut to rename it.
- The **Source code** link at the bottom of the app and in Settings opens
  <https://github.com/doccstat/appclone>.

The app drives the same commands by hand if you prefer:

```shell
pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE Clone1
pm install-existing --user <clone-id> com.example.app
```

Clone users start automatically at boot once created. Launching an app in a
clone runs it against its own `/data/user/<clone-id>/` data.

## Compatibility notes

- The prefix setting renames the module's own pinned shortcuts only. Pixel
  Launcher's app-drawer entries for clone users are labelled inside the
  launcher process; renaming those would need a second hook in
  `com.google.android.apps.nexuslauncher`, which this module does not install.
- Shortcut labels are free text, so colons and other characters are allowed;
  line breaks are collapsed to spaces.
- Android 17 computes `getMaxAllowed() - currentOfType` directly and never
  checks the AOSP `-1` unlimited sentinel, so the module returns a large
  positive number instead.
- `UserController.setInitialConfig` has four arguments on Android 16 and six on
  Android 17; the module hooks every declared overload.

## Support

Report issues at <https://github.com/doccstat/appclone/issues> with the device
model, Android version, module version, and reproduction steps. This project is
not affiliated with Google, Android, or LSPosed.
