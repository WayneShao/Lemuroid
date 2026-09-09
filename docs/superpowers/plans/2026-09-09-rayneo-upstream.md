# RayNeo Upstream Update Implementation Plan

> **For agentic workers:** Execute the following tasks in order, with a bounded independent review of the plan and final changes.

**Goal:** Update the working RayNeo X3 Pro adaptation to upstream `53752bf2` and make the no-SAF ROM permission flow explicit and recoverable.

**Architecture:** Preserve existing rendering and input code. Keep shared folder selection as the entry point; isolate the RayNeo fallback page and a small filesystem access helper. Validate access before persisting the directory and scheduling library indexing.

**Tech Stack:** Kotlin, Android SDK 35 / device SDK 32, Compose, Gradle 8.10.2, JDK 17, JUnit.

## Tasks

- [x] Back up the 15 adaptation source/document files and previous APK plus metadata; verify ZIP entries with SHA-256.
- [x] Commit the original adaptation on a backup branch. Create an external worktree and merge freshly fetched `upstream/master` without changing the existing display strategy.
- [x] Initialize the upstream-pinned `lemuroid-cores` submodule.
- [x] Build the merged baseline using `:lemuroid-app:assembleFreeDynamicDebug` and record any inherited toolchain/build issues.
- [x] Add focused tests in `lemuroid-app/src/test/java/com/swordfish/lemuroid/app/rayneo/RayNeoStorageAccessTest.kt`; add JUnit test dependency.
- [x] Implement a tested filesystem/permission boundary in `rayneo/RayNeoStorageAccess.kt` and a dual-eye-compatible Compose status page in `rayneo/RayNeoStorageScreen.kt`.
- [x] Update `shared/settings/StorageFrameworkPickerLauncher.kt` to use that fallback only on RayNeo, resume permission checks, retain existing directories and show failures. Restore upstream non-RayNeo SAF failure handling.
- [x] Remove the unconditional all-files Settings launch from `mobile/feature/main/MainActivity.kt`.
- [x] Add English and Simplified Chinese resources for the fallback page; document ADB grant and verification with explicit serial/package arguments.
- [x] Run focused unit tests, changed-source formatting checks, final debug build and Android lint. Review the change for lifecycle and directory-selection regressions.
- [ ] Verify signature compatibility, package/version, adaptation classes, and upstream/submodule ancestry. Copy the tested APK into a named release-artifact directory in the original workspace.
- [ ] Bring the completed branch into the original workspace and write a short migration/validation record, keeping backup references and pending runtime checks explicit.
