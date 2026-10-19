# Seva Saathi · सेवा साथी

**A little space for the seva you do.** An independent community companion for followers of Sant Rampal Ji Maharaj—not an official or endorsed app.

- **Download Android APK:** https://github.com/glitchyapi/seva-saathi-pro/releases/latest/download/Seva-Saathi.apk
- **Website:** https://glitchyapi.github.io/seva-saathi-pro/
- **Interactive browser preview:** https://glitchyapi.github.io/seva-saathi-pro/app/
- **Release studio:** https://glitchyapi.github.io/seva-saathi-pro/admin/
- **Universal signed update feed:** https://raw.githubusercontent.com/glitchyapi/seva-saathi-pro/main/docs/updates/stable.json
- **Feed mirror:** https://glitchyapi.github.io/seva-saathi-pro/updates/stable.json

## First-build features

- Daily seva journal: add, edit, delete, category filters, search, personal weekly intention, and seven-day activity view.
- English and Hindi interface; journal entries remain in the language you wrote them.
- Bhandara announcements from a signed release feed. No invented event dates ship in the Android app.
- Offline personal invitation/banner maker with PNG export through Android’s file picker.
- Offline-first Android storage: GZIP compression **before** AES-256-GCM encryption, with the key held by Android Keystore.
- Password-encrypted `.seva` backups and explicit replacement on restore.
- Adaptive launcher icon, monochrome themed icon, Android splash resources, and reduced-motion-aware transitions.
- Version screen, verified file-level interface updates, and separately verified APK installation with Android confirmation.
- Opt-in update notifications via **best-effort six-hour scheduled checks**, plus checks on app opening. This is **not instant push**.

The reference image informed the rounded cards, muted palette, image panels, and charcoal pill navigation—not the travel subject matter. Artwork is an original AI-generated conceptual community-kitchen illustration, not a photograph of a named event. Manrope and Noto Sans Devanagari are bundled with their OFL licenses.

## What is real, and what is preview-only?

The APK is a compiled, signed Android application containing `AndroidManifest.xml`, resources, DEX bytecode, all interface assets, fonts, and artwork. It is not a renamed HTML file or a website shortcut. It requires Android 8.0+ and a working Android System WebView. Its small size is possible because it uses the system WebView and platform APIs rather than shipping a browser engine or large framework.

The browser preview deliberately starts with **labelled sample entries** and uses **unencrypted browser local storage**. Do not put sensitive information there. The Android app starts empty and uses the native encrypted vault. Native permissions, scheduled jobs, and APK installation cannot be tested in the browser preview.

This is a **first release for device acceptance testing**, not a claim of a completed independent security audit or exhaustive device compatibility certification. See [TESTING.md](TESTING.md).

## Updates and administration

1. Commit changes to `web/` for the interface, or `app/` for native code.
2. Open Release Studio. Connect a **new fine-grained token scoped only to this repository** with Contents read/write and Actions read/write.
3. Edit bilingual release notes and confirmed Bhandara announcements.
4. Choose **Interface & announcements** or **Android app** and request the release.
5. Follow the GitHub Actions run. A queued workflow is not a successful publication.
6. After successful signing/verification, the stable feed points to the release. The app verifies its pinned RSA key and the SHA-256 of every downloaded file.

The studio is a static GitHub Pages client, **not** a server with a shared admin password. GitHub authorizes writes. Its token is kept only in the tab’s memory, never in local/session storage. Anyone can read public announcements and release metadata; they cannot publish without repository authorization and the signing workflow.

### OnlyFiles + GitHub

The publishing adapter uploads **public app files only** to `https://api.onlyfiles.com/v1/upload` with `expire=0`, verifies the downloaded bytes, and stores the resulting public URL in the signed manifest. Unchanged hashes reuse previous uploads. Every OnlyFiles-backed file also has a GitHub fallback. If OnlyFiles rejects a file or verification fails, GitHub remains the primary source for that file.

OnlyFiles returns a **landing page**, not a raw APK. The Android adapter retrieves its current binary link without executing the page and verifies the resulting bytes. It never saves HTML as an APK. The tested HTML interface file needed the GitHub fallback; do not assume every provider accepts every extension. Provider availability, markup, quotas, and retention policies can change.

### Deliberate boundaries

- No `sudo`, root, silent APK installation, or on-phone APK compilation.
- Interface updates download whole **changed files**, not binary diffs inside files. They are staged as a complete set and activated together. Unchanged bytes are reused locally.
- No new native permissions/classes through the interface channel. A new native build must be signed with the same APK certificate.
- An interrupted or bad download does not replace the active interface. A launch that fails to report UI health is rolled back on the next launch.
- The minimum-version message does not lock people out of their offline journal.
- GZIP is a practical compression choice for small JSON journals—not the “best compression of all methods.” Encryption cannot guarantee safety on a compromised, rooted, or unlocked device.

## Build locally

Install JDK 17 and the official Android SDK:

```sh
sdkmanager 'platforms;android-34' 'build-tools;34.0.0'
export ANDROID_HOME=/path/to/android-sdk
export APK_KEYSTORE=/secure/path/release.jks
export APK_STORE_PASSWORD='your-keystore-password'
python3 scripts/build_apk.py
```

The script invokes official `aapt2`, `javac`, `d8`, `zipalign`, and `apksigner`, then verifies the APK. It does **not** emit an unsigned/debug APK if the release key is missing. Reuse the original keystore for every native upgrade. Never commit a keystore or signing password.

For a local preview:

```sh
python3 -m http.server 4173 --bind 0.0.0.0 --directory web
# Admin / website preview:
python3 -m http.server 4174 --bind 0.0.0.0 --directory docs
```

## Project map

```
app/src/main/java/…   Native vault, backup codec, update engine, installer, bridge
app/src/main/res/     Android icons and splash themes
web/                 Offline interface and local fonts/artwork
docs/                GitHub Pages site, admin, browser demo, signed update feed
scripts/             Reproducible APK and release publishing tools
tests/               Browser, cryptographic, manifest, and packaging checks
.github/workflows/   Owner-triggered signed release pipeline
```

[Installation](INSTALL.md) · [Architecture](ARCHITECTURE.md) · [Privacy](PRIVACY.md) · [Research and trade-offs](RESEARCH.md) · [Security](SECURITY.md)

## Owner security reminder

A broad GitHub token was supplied during setup. **Revoke that exposed token and create a fresh, short-lived, repository-scoped token.** It is not embedded in this app or repository. Repository signing secrets are separate and must be retained for future updates.
