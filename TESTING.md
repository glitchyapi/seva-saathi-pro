# First-build verification

This document separates completed checks from physical-device acceptance work. It is not a certification or independent security audit.

## Completed in the build workspace

- Official SDK build: `aapt2` binary resources/manifest, Java compilation, D8 executable DEX, zip alignment, release signing.
- APK verification: v1/v2/v3 signatures and alignment verified with Android SDK tools.
- Android 11 / API 30 x86_64 emulator: the signed release APK installed successfully with `adb install -r --no-streaming`; Android reported package `org.sevasaathi.app`, versionCode 1, minSdk 26, targetSdk 35. Native process started and rendered the actual empty Android journal (not browser sample data).
- Legacy WebView 83 compatibility observed in the emulator; inset/flex-gap fallbacks were added after visual inspection. A current WebView is strongly recommended for security and support.
- Browser smoke tests: create, reload persistence, search, edit, delete, category screen, Hindi switch, banner PNG generation, version screen’s explicit browser limitation, desktop layout, and no JavaScript/CSP console errors.
- Java core checks: backup round trip with Hindi; fresh random salt/IV; wrong-password and tamper rejection; bounded KDF parameters; password length; invalid date/duration; path traversal; cleartext/unapproved-host rejection; native executable exclusion; Python-to-Java release signature verification; modified manifest rejection.
- Signed-feed tests: pinned public key, every file’s SHA-256/size/exact source bytes, duplicate/path constraints, a one-changed-file delta selection case, explicit preview distinction, no broad storage/notification-listener permissions, and no external WebView resources.
- OnlyFiles: actual public probe upload, HTML-vs-binary inspection, tokenized binary-link resolution, verified public asset/APK uploads, and GitHub fallback for the HTML asset.

Test code lives in `tests/`; public assets and readable metadata live in `docs/updates/`. Additional live-release and device checks are recorded during handover as they complete.

## Test environment caveats

The sandbox has no hardware virtualization device. The emulator runs software CPU emulation and initially showed a **System UI** non-response dialog during cold boot/first-run pressure. This is not represented as normal-phone performance or as a clean benchmark. Once booted, APK installation completed and the app rendered. No representative speed/battery claim is inferred from that emulator.

The browser preview is not an Android Keystore, permission, or installer test. Portable backup codec tests exercise actual cryptography, but do not by themselves certify every device’s Keystore implementation.

## Still needed before broad production use

- Owner installation and acceptance test on a physical phone, especially the device that previously reported the packing/parsing error.
- Android 13–16 notification runtime-permission and unknown-source/install behavior on representative devices/OEMs.
- End-to-end higher-version native APK upgrade on a physical device, including refusal/cancel/retry and journal retention.
- Interruptions during real-device downloads/activation, low-storage recovery, very large journals, power loss, background throttling, and OEM force-stop behavior.
- Independent security review, accessibility/contrast and large-font audit, legal/community branding review, and operational key-recovery rehearsal.

See INSTALL.md for the owner test checklist. Do not claim “tested on your phone” until the owner confirms it.
