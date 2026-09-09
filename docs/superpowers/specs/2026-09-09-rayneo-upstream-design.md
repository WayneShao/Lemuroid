# RayNeo adaptation update

The user reports that the March 30 adaptation achieved an initial working state, with directory access ultimately granted through ADB. Update it to the latest upstream Git revision and address that incomplete permission flow while retaining the working dual-eye rendering and input paths.

## Scope and evidence

- Old upstream: `ea0d7781`; freshly fetched upstream: `53752bf2` (2026-05-22). The two new commits update versionCode to 252, the pinned cores submodule, and SNES controller ports.
- Preserve the original local changes in commit `bb0fd44e` and a verified external ZIP containing source and the previous APK.
- Use an external worktree for implementation and bring the completed revision and APK back into the user's original directory.
- Live read-only inspection of the connected X3 Pro confirms SDK 32, 1280x480 at density 160, no ACTION_OPEN_DOCUMENT_TREE activity, and both per-app and global all-files Settings activities. Lemuroid and `/sdcard/Games` are currently absent on that device; the earlier installation state should not be assumed.

## Design

Keep DualEyeLayout, PixelCopy game mirroring, Bluetooth gamepad use, Mercury registration, and the existing right-eye back gesture. Use the upstream-pinned cores revision, not an independently newer core checkout.

Directory selection keeps upstream SAF behavior on ordinary Android devices. If SAF is unavailable on RayNeo, present an in-app page inside the existing dual-eye wrapper. Check all-files permission before selecting or scanning the local ROM directory. Request permission only from this explicit directory-selection flow, not on every main-activity startup. Try the app-specific Settings page and then the general all-files page; handle missing or restricted settings visibly. Recheck on return/resume and provide a retry button for an external ADB grant. Keep ADB as a documented firmware fallback, not as an automatically executed app operation.

Reuse a previously selected legacy directory, defaulting to `/sdcard/Games`. With permission, create a missing directory and confirm that it can be listed. Only then persist the selection and schedule indexing. If permission or directory access fails, keep the previous selection and do not start a misleading empty scan. Restore the fallback-page state across Activity recreation. Render status and actions as regular Compose content so both eyes show the same page.

## Verification

Build the merged baseline and the final freeDynamicDebug APK with a compatible local JDK. Add focused JVM regression tests for denied access, permission granted after retry, directory creation, preservation of existing files, and a file occupying the requested directory path. Run formatting checks for changed Kotlin sources and Android lint, distinguishing inherited issues. Verify APK signature against the preserved APK, version/package metadata, DEX adaptation classes, and the pinned submodule. Record any actual-device validation separately from compilation and static artifact checks.
