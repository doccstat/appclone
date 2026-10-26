# App Clone

App Clone is an Xposed/LSPosed module that raises the AOSP clone-profile limits
on AOSP-derived firmware, so many independent clone users can be created and run
at once. It also includes a small launcher app for creating clones, installing
apps into them, and pinning labelled home-screen shortcuts.

Source and support: <https://github.com/doccstat/appclone>.

## Features

- Create multiple clone profiles; start or stop each profile when needed.
- Install an app that is already installed for the main user into a clone.
- Launch an app inside a specific clone, starting the profile if it is stopped.
- Pin a home-screen shortcut for one app in one clone.
- Label shortcuts with the clone id, app name, or clone name.
- Auto-pin a shortcut when an app is added to a clone.
- Delete one profile or all profiles; the main user is untouched.
- Export a diagnostics file when clone creation fails.

## Screenshots

| Profiles | New profile | Creating clones |
| --- | --- | --- |
| ![Clone profiles and menu](https://raw.githubusercontent.com/Xposed-Modules-Repo/com.lixingchi.appclone/refs/heads/main/docs/screenshots/profiles.png) | ![New profile dialog](https://raw.githubusercontent.com/Xposed-Modules-Repo/com.lixingchi.appclone/refs/heads/main/docs/screenshots/new-profile.png) | ![Creating clone profiles](https://raw.githubusercontent.com/Xposed-Modules-Repo/com.lixingchi.appclone/refs/heads/main/docs/screenshots/creating.png) |

## Requirements

- Android 13+ (API 33+).
  Verified on Android 17 (API 37), Pixel 11 Pro Fold. Android 13–16 have the
  same AOSP clone user type and limit checks but are not yet device-tested.
- Root with LSPosed (or another legacy-Xposed-API framework) that can load a
  module into `system_server`.
- Root access for the launcher app (`su`), granted through KernelSU or another
  root manager on first use.

## Install

1. Download the APK from [Releases](https://github.com/doccstat/appclone/releases).
2. Install it for user 0.
3. Enable it in LSPosed and set its scope to `system` (`android`).
4. Reboot.
5. Open **App Clone** and grant root when prompted.

## Usage

- The list shows every clone profile and whether it is running. Use **Stop**
  to stop its apps and notifications without deleting data, or **Start** to
  resume it. Controls support non-ephemeral clone profiles belonging to user 0.
- **New profile** creates one or more clones; the name is suggested for you.
  If creation fails, the error dialog offers **Export logs**; attach the saved
  `appclone-diagnostics-*.txt` file to the issue.
- Tap a profile to pick its apps. Tap an app row to install it into the clone,
  **Open** to run it there, **Delete clone** to remove the profile and its data.
  **Open** and home-screen shortcuts automatically start a stopped profile and
  wait for it to unlock before launching the app. Existing shortcuts work too;
  no re-pinning is needed. If startup fails, an error is shown instead.
- **Delete all profiles** (⋮ menu) removes every clone; your main user is
  untouched.
- **Pin a shortcut** (⋮ menu) lists each clone app once; pick the app, then the
  clone profile if the app is in more than one. The launcher asks you to confirm.
- **Shortcut settings** (⋮ menu) controls shortcut labels:
  - *Prefix clone app names* uses `{id}: {name}` by default, so a shortcut reads
    `12: Chrome`. The format accepts `{id}`, `{name}`, and `{clone}`; the buttons
    below the field insert them. Any other text, including colons, is kept.
  - *Auto-pin new clone apps* requests a shortcut as each app is added to a
    clone.
  Labels apply to shortcuts created after the change; re-pin an existing
  shortcut to rename it.

Stopping is not a persistent boot preference: Android may start clone users
again after a reboot. New profiles still start for initialization. This app adds
no background watcher or automatic idle-stop policy. Each clone keeps its own
`/data/user/<clone-id>/` app data. Unlock the main user before starting a clone.

## Compatibility notes

- The label setting renames the module's own pinned shortcuts only. Pixel
  Launcher's app-drawer entries for clone users are labelled by the launcher
  process; renaming those would require a separate launcher hook.
- Shortcut labels are free text; line breaks are collapsed to spaces.
- Custom ROMs and OEM firmware that remove or change the AOSP clone-profile
  classes are not supported.

## Disclaimer

App Clone changes Android framework behavior through an Xposed/LSPosed module.
It requires root and is intended for personal use on devices you own. It is not
affiliated with Google, Android, LSPosed, or any device manufacturer. Use it at
your own risk and keep a backup: creating, deleting, or modifying clone users
can remove their app data, and an incompatible framework or firmware update can
prevent the device from booting. The authors are not responsible for data loss,
warranty issues, or problems caused by other modules or firmware.

## License

App Clone is licensed under the GNU General Public License, version 3 only
(`GPL-3.0-only`). See the
[license text](https://github.com/doccstat/appclone/blob/main/LICENSE). This
program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE.

## Support

Report issues with the [bug report form](https://github.com/doccstat/appclone/issues/new/choose).
It asks for the device/ROM build, module and framework versions, reproduction
steps, LSPosed/Vector logs, and the output of `pm create-user`/`dumpsys user`.
If creation fails, tap **Export logs** in the dialog and attach the saved
`appclone-diagnostics-*.txt` file. Without the exact error and framework logs,
an OEM-specific failure usually cannot be diagnosed.
