# Lemuroid for RayNeo X3 Pro

This fork maintains the RayNeo adaptation on the `rayneo` branch. The upstream project remains Swordfish90/Lemuroid; merge its `master` into the adaptation branch when updating. The current upstream base is `93e321d0` with its pinned cores submodule `fee2e824`. Upstream versionCode is 252 and versionName is 1.17.0. The maintained build is `1.17.0-rayneo.2`; its launcher name is **Lemuroid**. Installable versions are tracked in [GitHub Releases](https://github.com/WayneShao/Lemuroid/releases).

## Adaptation boundaries

Maintenance is limited to this application's own code, windows and input. The current glasses have no root access. The owner reports that replacing or changing the system IME has already caused device reboots and similar constraints exist in other firmware components. Keep the installed system IME and components as they are; system adaptation, root, global display changes and reboot experiments are outside this port. An app text field may use the existing IME, but that does not establish stereo support for the IME window.

- The tested device profile is X3 Pro / ARGF20, Android API 32, 1280×480 at density 160, with a 640×480 logical region for each eye.
- The initial wrapper is installed before the window attaches so moving a Compose root does not cancel its window recomposer. Already attached replacement roots recreate their composition; their temporary `remember` state is not retained across that exceptional reparent.
- `DualEyeLayout` draws one normal View/Compose tree to both eyes. `DualEyeActivityWrapper` installs it for ordinary activities; the game activity keeps its own two-Surface output.
- `DualEyeDialog` provides the same single-composition approach inside a separate Dialog window. `LemuroidAlertDialog` is the common entry for help, settings lists, input binding, confirmation, native error messages and the RayNeo game context menu. Ordinary Android devices retain Material dialogs/sheets.
- Game output keeps the previously working `GLRetroView` + `RayNeoMirrorView` / PixelCopy path. This is a distinct rendering path, with additional copy/presentation cost; no measured frame-rate or latency guarantee is claimed.
- Bluetooth gamepads and real mice use upstream Lemuroid input. The right temple (`cyttsp5_mt`) uses native two-axis focus: swipe to move, single tap to confirm after the double-tap interval, double tap to go back, and two-finger tap to send MENU. Game cards and the running game connect MENU to their context/menu actions. Left-temple and CapSense events delivered to this app are consumed before child controls; firmware-owned system gestures remain outside this adapter.
- Each window owns its input state. A pending confirmation is discarded when the window, focused control, layout target or list item changes. Focus is established on entry and recovered after dynamic navigation; an already-consumed direction key is never replayed as another movement.
- Shared adapters cover late Activity content, dialogs, modal sheets, app messages, the game loading view and text action toolbars. The new upstream custom-name dialog uses the same stereo modal entry. On firmware without an image chooser, cover art uses a separate in-app file page; it does not change the ROM directory.
- Static native storage/image pages use software drawing locally due the recorded X3 Pro HWUI defect. Compose and the game retain their existing hardware rendering. Dirty regions are expanded for both drawing modes.
- PixelCopy owns one in-flight buffer at a time, waits for callbacks before recycling it, drops stale results and stops while paused or detached. This is lifecycle hardening, not a claim of zero-copy or frame-perfect output.

The Activity container does not cover arbitrary independent Popup/third-party/system windows. Adding a new modal entry requires checking its actual window and display ownership. Compose text selection/cursor drag handles are still independent Popup windows; they are an open editing-overlay limitation even though the text action toolbar is in-tree. The system IME, Settings and external chooser belong to other apps. The adapter also does not change the app's Android display configuration to a real 640×480 display.

The follow-up [logical display feasibility study](docs/rayneo/logical-display-feasibility.md) evaluates a real single-eye VirtualDisplay with GPU presentation to both eyes. It is a design investigation, not a completed runtime backend.

## First installation and ROM access

Build and install the freeDynamicDebug variant. Its package is `com.swordfish.lemuroid.debug`, signed with the repository's existing debug certificate. It is a debug build, not a production release.

On the home page, choose the ROM directory. Normal Android devices use the system document picker. On RayNeo firmware without DocumentsUI, the app opens a dual-eye storage page:

1. Reuse the previous local directory, or default to `/sdcard/Games`.
2. Check actual all-files access. The grant button first opens per-app Settings and falls back to the global all-files Settings page if needed.
3. Recheck on return and via **Check again**. Denial keeps the recovery page; no directory selection is saved or indexing started.
4. After access is granted, create a missing directory and confirm it can be listed. Only then save the selection and enqueue indexing once. Settings displays the selected local path.

The firmware's Settings page is outside Lemuroid's dual-eye window. Where it is difficult to operate, retain the ADB fallback. After selecting the current device and verifying the package/user, for Android user 0:

```powershell
adb -s SERIAL shell appops set --user 0 com.swordfish.lemuroid.debug MANAGE_EXTERNAL_STORAGE allow
adb -s SERIAL shell appops get --user 0 com.swordfish.lemuroid.debug MANAGE_EXTERNAL_STORAGE
```

Return to the app and choose **Check again**. An appop value alone is not proof of directory readability; the app performs that check. On other Android users or variants, use their actual user and package. Reflashing resets installation, permission and directory assumptions.

## Build and verification

See the [2026-10-09 acceptance report](docs/rayneo/2026-10-09-validation.md) for this version, including confirmed input recovery and features whose device acceptance remains open.

Use JDK 17, the Android SDK, and initialized submodules. Set `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or a local `sdk.dir` as appropriate for the host; local machine paths are not committed.

```powershell
git submodule update --init --recursive
.\gradlew.bat :lemuroid-app:ktlintCheck :lemuroid-app:testFreeDynamicDebugUnitTest :lemuroid-app:lintFreeDynamicDebug :lemuroid-app:assembleFreeDynamicDebug --no-configuration-cache
python -B skills/porting-rayneo-android-apps/scripts/test_probe_device.py
```

The `RayNeo build` GitHub workflow runs these checks for `rayneo` changes and uploads the debug APK. Local output is `lemuroid-app/build/outputs/apk/freeDynamic/debug/lemuroid-app-free-dynamic-debug.apk`.

Device acceptance should cover home/settings, help and long lists, game context menu, binding focus, dialog dismissal, permission denial/return/grant, empty versus unreadable directories, one known game, back to its menu, and resume. Screenshots establish rendered layout; physical gestures and eye synchronization require separate observation.

## Reuse and upstream maintenance

The maintained skill is [porting-rayneo-android-apps](skills/porting-rayneo-android-apps/SKILL.md). Copy that folder into the agent's skills directory for use with other source projects. It is self-contained and includes a read-only device probe. `DualEyeLayout` and `DualEyeDialog` are the small rendering examples to adapt; keep app-specific navigation, ViewModels, game state and directory indexing outside them.

```powershell
git fetch upstream master
git switch rayneo
git merge upstream/master
git submodule update --init --recursive
```

The upstream publishing workflow is restricted to the upstream repository so a RayNeo tag does not invoke its unavailable signing secrets. For a maintained release, verify the exact APK, update the `rayneo` branch, tag that commit, and attach the APK plus SHA-256 file and an acceptance report to GitHub Releases. Retain the existing signing identity for updates. Build success, installation, injected-input evidence and physical temple/optical acceptance are separate statuses.

Run verification before pushing. Do not replace the pinned cores revision with the core repository's moving head. The March playbook remains a historical reference; use the current skill and source for present implementation boundaries.
