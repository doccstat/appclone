# App Clone

Manage cloned apps first, or switch to **Profiles** to manage their containers.
App Clone uses Android's native clone profiles and an Xposed/LSPosed module to
raise the stock profile limits. APKs are shared; each profile has separate app
data.

Source and support: <https://github.com/doccstat/appclone>.

## Features

- **Apps** opens by default and shows only apps installed in clone profiles.
  Every instance appears directly beneath its app, with profile name, user ID,
  state, **Open** and a **⋮** action menu. No drill-down or expansion is needed.
- Swipe or tap between **Apps / Profiles**. Each tab keeps its own search and
  scroll position. Long lists recycle rows rather than creating every view at once.
- **Profiles** shows running/stopped profiles and their installed apps, with
  **Rename profile** and other profile-wide actions in **⋮**.
- **Add clone app** lets you choose an existing profile or explicitly create
  new ones. Different apps can share a profile; another instance of the same
  app needs another profile. No profile is selected or created automatically.
- Per-instance **Disable (freeze)**, **Enable**, **Force stop**, shortcut and
  removal actions. **Background activity** offers an explicit Android restriction
  and restoration of the saved prior mode. Disabled apps and stopped profiles remain visible.
- Material You with **Material 3 Expressive**, wallpaper-derived colours where
  supported, and system light/dark mode. Uses Google's Material Components
  **1.14.0**, not a custom imitation of its controls.

## Requirements and installation

- Android 13+ (API 33+), root, and LSPosed or another framework supporting legacy
  Xposed API 82 in `system_server`.
- The clone engine was tested on Pixel / Android 17. The redesigned UI and
  profile rename have host tests; real-device acceptance remains pending.
- Firmware must retain AOSP clone-profile support. Root and hooks do not provide
  unlimited memory or universal app compatibility.

1. Download the APK from [Releases](https://github.com/doccstat/appclone/releases).
2. Install/update it for **user 0**. Existing profiles and app data stay in place.
3. Enable the module for **System Framework** (`system` / `android`) and reboot
   when activating or replacing framework hooks.
4. Open App Clone and grant root.

If the root shell cannot start, confirm root is active and App Clone is allowed
in your root manager, then reopen the app and tap **Retry**. LSPosed scope alone
does not grant root. App Clone tries `su` through the app's PATH, then
`/system/bin/su` only if the executable was not found; it never retries an
operation that already started. **Export diagnostics** includes the app UID,
PATH and a root `id` check for troubleshooting. No data clearing is needed.

## Apps and profiles

The **Apps** tab groups by package, not launcher activity. Counts exclude the
main user's copy. All of an app's profiles are listed inline, including disabled
apps and stopped profiles. The app heading's **⋮** offers **Add another clone**
and optional app details. Each instance has its own **⋮** menu. **Open** starts a
stopped profile, waits for unlocked storage and opens that instance; it never
silently opens the main user's copy or enables a disabled app. For secured
profiles, it asks Android to reuse the cached shared credential. If none is
available, lock and unlock the main user with its PIN, pattern or password, then
retry. App Clone never collects or stores your lock-screen credential.

Open and shortcuts use one bounded, short-lived root process for fresh profile,
serial, package and readiness checks plus the target-user launch. Already-unlocked
profiles skip startup and authentication. This reduces root invocations, not a
promise to retain app processes or a measured launch-speed improvement.

System apps are hidden initially to avoid filling the list with automatically
provisioned dependencies. **Show system apps** in the menu is a remembered,
shared filter for both views and the add picker; hiding a row never changes the
app itself. Apps without a launcher can be managed but cannot necessarily open.
Navigation and returning to the app reuse the last inventory. App Clone actions
invalidate it; use **Refresh** in any list screen for changes made elsewhere.
Refresh keeps the previous rows and scroll position, combines repeated requests,
and shows failures with **Retry** rather than clearing the list. Cached rows never
replace fresh safety checks before profile/app actions. Tap an error summary for
full details. Search and status scroll with the list; tabs remain accessible.
The add button yields space while the keyboard is open and returns when it closes.

**Add clone app** offers installed main-user apps and an explicit destination.
Creating profiles supports a count of 1–500 and optional `-1, -2, …` numbering.
Names are kept exactly as entered; numbering is off initially. Creation starts
the profiles. A partially successful batch keeps the profiles already created
and reports the failure rather than deleting data to roll back.

The **Profiles** tab retains Start/Stop, app management, creation and deletion.
Tap a profile to view its apps, or use **⋮** for profile-wide actions.
**Rename profile** is also available in an instance's **⋮** menu and in profile
details. It changes the name for **all apps sharing that profile**, not the app's
own name. Names are saved exactly as entered; empty, multiline, NUL-containing
or over-100-character names are rejected rather than trimmed or shortened
(emoji may count as two characters). IDs, app data and running state are unchanged.
Existing shortcut labels are left alone; re-pin them if you want updated labels.

Stopping a non-ephemeral clone keeps data but pauses **all** its apps and
notifications. Its other apps resume too when an app starts that profile.

## App controls

Every action identifies the app and the target profile before changing it.
Owner, work and private profiles are excluded.

| Action | Effect in the selected clone only |
| --- | --- |
| Disable app (freeze) | Uses Android package disablement; data stays, but the app and its notifications stop until explicitly enabled. Other profile services keep running. |
| Enable app | Explicitly sets the app enabled. Does not start a stopped profile. |
| Use manifest default state | Removes the enabled-state override. This is not restoration of an arbitrary earlier override. |
| Force stop app | Stops it now without deleting data or disabling it. Notifications may stop until it is opened again; this is not a permanent freeze. |
| Background activity | Shows the current package background mode. Keep current, explicitly Restrict, or Restore previous; restrictions can delay notifications and jobs without stopping the profile. |
| Pin shortcut | Pins this app/profile pair using the configured label. |
| Remove from this profile | Deletes only that app's data in the selected profile. Does not delete the profile or its other apps. |

Changing system dependencies can break other apps in that profile. There is no
automatic freezing, screen-off watcher, idle-stop service, global GMS change or
bulk debloating. These controls use explicit Android per-user commands/APIs; Hail
installation, device-owner enrollment and additional root modules are not needed.
Suspension is deliberately not presented as a battery-saving freeze: it does not
prevent background execution.

### Background activity

From Apps or either detail view, open the instance's **⋮** → **Background activity**.
Opening or cancelling the dialog changes nothing. **Restrict** sets only that
clone package's `RUN_ANY_IN_BACKGROUND` AppOp to `ignore`. It is not Android's
complete Battery settings preset, a force-stop or a cached-process freezer.
It does not change standby buckets, Doze exemptions, Google services, package
installation/enablement or reboot policy. Android still controls process memory
and profile lifetime; the setting does not guarantee delivery or battery savings.

Before applying, App Clone saves the exact prior package mode in private app
preferences, bound to the profile serial, package, UID and installation timestamp.
**Restore previous** checks current identity and mode before restoring; a detected
external change or app replacement is reported rather than overwritten. Failed or
interrupted writes retain the restore record. Shared/system-UID apps and UID-wide
overrides are not independently managed here. Unknown APIs/modes fail visibly.
Only supported package modes are restored, not AppOps access history or other
battery settings. An external write of the same mode cannot be distinguished.

**Forget saved setting** has a separate confirmation: it removes only the local
restore record, leaving Android unchanged. Restore is then unavailable. Restore
before clearing App Clone's data or uninstalling it: Android's restriction can
remain after the local restore record is lost. Records survive normal app updates
and restarts. Existing manual **Force stop app** remains a separate choice; it
keeps the profile unlocked but the next app launch is cold.

### Shortcuts and removal

New shortcuts record the profile serial so a recycled user ID cannot silently
retarget them. Old shortcuts continue to work but lack that check; re-pin them
for serial protection. A disabled app must be enabled before using its shortcut.

**Delete profile** and **Delete all profiles** delete all data in their stated
scope, including system apps hidden by the filter. App removal and profile
removal are separate confirmations. No empty profile is automatically deleted.

## Settings

- **After reboot:** System default (initial), Start all profiles, or Stop all
  profiles. The selected action runs once after owner unlock, at least 10 seconds
  after the boot broadcast; Android may defer it. It does not prevent initial
  profile startup. Root must already be available/granted. Temporary root that
  disappears on cold boot cannot automatically satisfy this requirement.
- **Shortcut labels:** optional prefix/format with `{id}`, `{name}`, `{clone}`.
  Applies to newly pinned shortcuts; re-pin to change an existing label.
- **Auto-pin new clone apps:** requests a shortcut after installation; the
  launcher still confirms. No automatic unfreeze or refreeze is performed.

Reboot actions are not a permanent lock: shortcuts/manual starts still work.
Force-stop/background restrictions can prevent the boot job; failures are
recorded in Settings rather than retried continuously. An interrupted job resumes
pending profiles; a process kill before its checkpoint can repeat the last
operation. Choosing another policy cancels queued work, not an in-flight command.

## Build and checks

Use JDK 21, Android SDK platform/build-tools 36, Python 3 and the checksum-pinned
[Xposed API 82 jar](https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar).
The Gradle 8.13 wrapper verifies its distribution checksum; AGP is 8.11.1.

```sh
export ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk21
export XPOSED_API_JAR=/path/to/api-82.jar
export BUILD_DIR="$HOME/.cache/appclone/build"
python3 tests/check_package.py
python3 -m unittest discover -s tests/build_tests -v
python3 build.py --self-check
python3 build.py --lint
python3 build.py
```

`--self-check` runs JVM contracts and Robolectric screen smoke tests without
root/device access. `build.py` keeps Gradle caches/output outside source and
produces an unsigned release APK. On ARM64 Linux, `ANDROID_AAPT2` may point to a
compatible aapt2 executable/wrapper. Release signing requires an existing key
and the explicitly pinned certificate; the build never generates a key.

App management needs `QUERY_ALL_PACKAGES` to include non-launcher and disabled
packages. Inventory stays on-device; the app requests no Internet permission.
**Export diagnostics** saves user/package-manager diagnostics only when asked;
review the report for personal information before sharing it in an
[issue](https://github.com/doccstat/appclone/issues/new/choose).

## License and disclaimer

[GPL-3.0-only](https://github.com/doccstat/appclone/blob/main/LICENSE).
Not affiliated with Google, Android or LSPosed. Root/framework changes can break
boot or app behavior, and deletion removes app data. Use at your own risk.
