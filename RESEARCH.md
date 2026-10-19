# Research and implementation decisions

Research focused on practical Android constraints, safe update delivery, private local records, and the supplied UI reference—not on claiming unsupported capabilities or inventing religious/event information.

## 1. A real APK need not be large

An APK is an Android package with a binary manifest, resources, executable DEX, and signatures. This project uses the platform WebView rather than shipping Chromium, Flutter, React Native, or a large support-library stack. Bundling local fonts, artwork, and UI while using platform APIs produces a small, real APK. Correctness is checked with `apksigner`, `zipalign`, manifest inspection, and browser/device testing, not by looking at file size alone.

**Decision:** Java platform shell; min API 26 / target API 34; official aapt2/D8 packaging; no unsigned fallback artifact. See [Android application signing](https://developer.android.com/studio/publish/app-signing) and [command-line build tools](https://developer.android.com/tools).

## 2. Silent native rebuild/install is not an ordinary-app feature

Android’s package installation APIs distinguish installer/user-action requirements. An ordinary sideloaded community app should not assume device-owner or privileged installer permissions. New native code, manifest permissions, and compiled resources belong in a signed APK; executable native code is not smuggled into an interface bundle.

**Decision:** signed file-level HTML/CSS/JS updates for the interface; higher-version signed APKs for native changes; preserve Android’s installer confirmation. No root, `sudo`, or on-device APK rebuild. Primary reference: [PackageInstaller.SessionParams](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).

## 3. Encrypt with a real device key; compress first

Android Keystore keeps key material non-exportable through the app API. Hardware binding is device-dependent and should not be promised for every device. AES-GCM provides encryption plus authentication when used with fresh IVs. Compressing ciphertext is ineffective; compress the structured journal before encryption. GZIP is widely implemented and suitable for compact JSON, but is not universally the best compressor.

**Decision:** AndroidKeyStore AES-256-GCM with random IVs, separate format AAD, bounded GZIP decompression, AtomicFile writes, explicit decrypt errors, no plaintext/cloud journal copy. Portable backups use a separate password-derived AES-GCM key, random salt/IV, and fixed bounded PBKDF2-HMAC-SHA256 work. Primary reference: [Android Keystore system](https://developer.android.com/privacy-and-security/keystore?hl=en).

## 4. WebView bridges require a narrow trust boundary

Android documents the risks of JavaScript/native bridges when untrusted pages or frames can call into privileged app code.

**Decision:** deny remote WebView requests/navigation, disable file/content access, use a locally intercepted HTTPS origin, enforce CSP in native response headers, escape user text, and load only embedded or cryptographically verified UI. Network operations occur in native code with signed metadata and an HTTPS allowlist. Never load the OnlyFiles website into the bridge-enabled WebView. Reference: [Android guidance on insecure WebView native bridges](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges?hl=en).

## 5. Permission requests should match the actual function

Android 13+ uses a runtime permission for most notifications. This is not the same as giving an app access to read all device notifications. Android’s Storage Access Framework allows a user to select individual export/import documents without all-files access.

**Decision:** contextual POST_NOTIFICATIONS request; system document picker for backups/banners; narrow APK URI grant for the installer. No MANAGE_EXTERNAL_STORAGE, notification-listener, contacts, or location permission. References: [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission?hl=en), [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider?hl=en).

## 6. A static JSON host does not provide push messaging

A public JSON feed can tell an app what version exists when the app asks. It cannot independently wake every device. Android persistent/periodic work is scheduled and can be deferred. Android documents a minimum periodic work interval of 15 minutes; it is not a real-time delivery guarantee. [2](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

**Decision:** app-open checks plus network-constrained six-hour JobScheduler checks and permission-controlled local update alerts. Clearly label these as best-effort, not instant push. For true push, add a configured FCM/UnifiedPush-compatible delivery service in a future native release. Additional primary reference: [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler).

## 7. OnlyFiles has an API—but its returned URL is not raw bytes

The initially interpreted domain `heonlyfiles.com` did not resolve. Further research identified the intended **OnlyFiles** service at `onlyfiles.com`. Its official documentation specifies `POST https://api.onlyfiles.com/v1/upload`, a 100 MB file limit, and `expire=0` for non-expiring storage under its policies. A successful response contains a file landing-page URL. Primary documentation: [OnlyFiles API](https://onlyfiles.com/api).

An upload of a non-sensitive, short-lived probe succeeded. Fetching the returned URL produced HTML, not the uploaded file. The page contained a tokenized `/dl/…` binary link. Guessing `/dl/{id}/{name}` or an undocumented API download path did not return the file.

**Decision:** upload only public app assets/APKs; verify actual returned bytes; resolve a fresh binary link natively without executing third-party HTML; check expected size/hash; retain a GitHub fallback per file. If OnlyFiles rejects a type or its adapter fails, publish the working GitHub source instead. This avoids mistaking a download page for an installable APK. The initial HTML asset used the fallback. No claim is made about permanent service uptime or unlimited quotas.

## 8. Static administration needs real authorization and separate signing

GitHub Pages is static hosting, not an authenticated application backend. A password hard-coded into a page would not secure publishing.

**Decision:** GitHub authorizes configuration writes/workflow dispatch using a new repository-scoped owner token held only in tab memory. Signing keys stay in Actions secrets. A workflow publishes a signed increasing-sequence manifest and immutable content-addressed files, with a raw URL primary and Pages mirror. Reference: [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages).

## 9. Reference-led UI without false authority

The supplied reference uses off-white surfaces, rounded image-led cards, restrained colors, compact data cards, and dark floating pill navigation.

**Decision:** apply those interaction/spacing ideas to Today, My sevas, Bhandara, and My space. Create an original leaf/hand companion mark, adaptive/monochrome Android assets, Hindi/English labels, and restrained transitions. Mark generated imagery as conceptual. Ship no unconfirmed Bhandara dates, fabricated quotations, guru likeness, official endorsement, or religious hierarchy. Personal weekly intentions are private reflections, not community rankings.

## Before wider release

Complete the physical-device checklist in INSTALL.md; test Android 13–16 permission/installer behavior and OEM battery policies; perform a security/accessibility review; stress-test larger journals and low-storage conditions; rehearse key recovery and the next native-version upgrade. A polished first APK is not proof that every production concern has been resolved.


## 8. First-build verification (this release)

The v1.0.0 APK was built with JDK 17 + Android SDK platform 34 / build-tools 34.0.0, signed with a fresh 2,048-bit RSA release keystore, zip-aligned, and verified with `apksigner` (v1/v2/v3 signature schemes). Package: `org.sevasaathi.app`, versionCode 1, versionName 1.0.0, minSdk 26, targetSdk 34. Final size: 510,246 bytes (~498 KB), SHA-256 recorded in `artifacts/build-info.json` and the signed feed.

Update-signing uses a separate freshly generated RSA-3072 key; only its public half is pinned inside the APK. Private keys and the keystore live outside the repository in `~/seva-keys/` and inside GitHub Actions encrypted secrets. No key material is committed.

OnlyFiles was exercised in this release: the APK and eight interface assets uploaded and byte-verified successfully through the adapter; `index.html` was rejected by the provider, so the GitHub source remained its verified primary URL. This confirms the hybrid design: provider outages or policy rejections never break the update path.

Browser smoke tests passed for create/edit/delete/persist/search, Hindi switching, banner PNG generation, and desktop layout with zero console errors. Fifteen JVM core tests passed, including Python→Java signed-release verification, tamper rejection, backup round-trip, and path/URL safety.
