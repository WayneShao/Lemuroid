# RayNeo latest upstream and skill refresh

Goal: migrate the existing port to upstream 93e321d0, retaining data/signing and covering new metadata dialogs, all reachable application windows, focus-mode temple input, messages and mirror lifecycle.

Architecture: keep one UI composition and existing game renderer. Central Activity content/window adapters plus common Dialog/sheet/message APIs own device behavior. Add testable gesture and copy-lifecycle boundaries. System IME/settings/chooser windows remain outside this app's ownership and are documented.

Tasks:
- [x] Preserve source and APK; isolated worktree; fetch both remotes and resolve upstream metadata-menu conflict without losing new actions.
- [x] Update maintained skill snapshot from the installed current version, retaining latest input/defect instructions.
- [x] Add a tested centralized temple focus-input controller: swipe, deferred single tap, double back, two-finger MENU, lifecycle cancellation and real mouse/gamepad pass-through.
- [x] Audit creation paths and update shared adapters: late/replaced Activity content, independent dialog/sheet, app message presentation, new custom-name and cover selection.
- [x] Make PixelCopy in-flight ownership/lifecycle safe while preserving the GLRetroView engine and upstream viewport settings.
- [x] Preserve display name Lemuroid, build/sign with existing debug identity, run tests/style/lint, and inspect actual APK.
- [x] Install through verified TCP ADB, record boot ID/uptime, validate application navigation and context window; owner confirms physical gamepad/touchpad restored. Extended game and editing flows remain explicitly unverified in the acceptance report.
- [x] Review final changes and prepare release APK, checksum and explicit acceptance limits. Publish the committed result to the existing fork and track the resulting GitHub Actions run separately.

Verification: pure JVM gesture/copy/storage tests; skill probe tests; ktlintCheck, lintFreeDynamicDebug, assembleFreeDynamicDebug; device screenshots and identity/version checks. Keep physical temple and Bluetooth behavior separate from injected or automated input evidence.

Scope refined by owner: app-internal usability only on an unrooted device. Preserve system IME/components; no system modification, root or reboot experiments. Stop expanding into system input-method validation.
