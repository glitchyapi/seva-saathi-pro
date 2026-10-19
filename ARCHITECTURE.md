# Architecture and update protocol

## Native shell + offline interface

A small Java Android application serves bundled or signature-verified local assets to a WebView at `https://app.sevasaathi.invalid`. This hostname is intercepted locally; no DNS or website connection is needed. Remote navigation/subresources are denied, file/content access is disabled, and a native-enforced CSP disables network connections, frames, plugins, and inline scripts. All displayed user-supplied text is escaped. Release builds disable WebView debugging.

The JavaScript bridge is intentionally limited to the journal, explicit backup/banner/file-picker actions, update controls, permission prompts, text sharing, and closing the activity. Downloaded interface code is still trusted application code: compromise of the release-signing process would be serious. The update public key is pinned inside the APK, not fetched from the mutable feed.

## Local vault

- Format: `SSV1` magic, 12-byte GCM IV, authenticated ciphertext/tag.
- Plaintext: versioned, validated JSON; maximum 2 MiB / 10,000 entries.
- Compression: GZIP before encryption.
- Cipher: AES-256-GCM, 128-bit authentication tag, fixed format AAD.
- Key: non-exportable Android Keystore key. Hardware backing depends on device support; it is **not asserted universally**.
- Writes: Android `AtomicFile` to protect the previous record set when a write is interrupted.
- Key/decryption errors are surfaced. Ordinary saves cannot silently replace an unreadable vault.
- Auto Backup is disabled. Release-state preferences contain version/check metadata, not journal entries.

This encrypts storage, not the whole running app. Plaintext must briefly exist in process memory to display/edit records. It is not a password lock, protection against a fully compromised OS, or an independent security audit.

## Portable backup

A `.seva` file is a JSON envelope containing format/version, compression identifier, 16-byte random salt, 12-byte random IV, fixed PBKDF2 iteration count, and base64 ciphertext/tag. Passwords of 12–512 characters are accepted. PBKDF2-HMAC-SHA256 runs for 600,000 iterations; AES-256-GCM authenticates compressed journal bytes with distinct backup AAD.

KDF parameters, input sizes, decompression size, schema, categories, dates, and durations are bounded/validated. Wrong-password or tamper failures do not write the journal. Restore is an explicit replacement action. Android export/import uses the Storage Access Framework; the app requests no blanket storage permission.

## Signed stable feed

Authoritative URL:

`https://raw.githubusercontent.com/glitchyapi/seva-saathi-pro/main/docs/updates/stable.json`

Pages fallback:

`https://glitchyapi.github.io/seva-saathi-pro/updates/stable.json`

Envelope:

```json
{
  "algorithm": "SHA256withRSA",
  "payload": "base64 of exact UTF-8 JSON bytes",
  "signature": "base64 RSA PKCS#1 v1.5 SHA-256 signature"
}
```

The application verifies the exact decoded payload bytes. It does not rely on cross-language JSON canonicalization. The publisher uses a 3072-bit RSA key. The unsigned `version.json` mirror is for humans, not the app’s trust source.

Payload includes `schema`, `channel`, increasing `sequence`, publication time, native `app` version/minimum/APK metadata, `ui` version/minimum-native/file list, bilingual notes, and confirmed events. Each downloadable file specifies `path` (for UI files), `bytes`, `sha256`, primary `url`, and optional `mirrors`.

A lower accepted sequence or a reused sequence with different envelope bytes is rejected. An offline device cannot know that a newer release exists until it reaches a provider: monotonic checks prevent rollback of a **previously observed** version, not a never-seen future update.

## OnlyFiles storage

Publishing sends public interface assets/APKs to the documented OnlyFiles upload API using `expire=0`, then verifies the retrieved bytes. Uploads are indexed by SHA-256 to avoid re-uploading unchanged data. Failed/rejected OnlyFiles uploads use GitHub. Every successful OnlyFiles source also gets a GitHub fallback.

The durable OnlyFiles URL identifies a landing page. Its `/dl/<temporary-token>/…` binary link is resolved at download time by parsing the download anchor, not by executing third-party HTML, scripts, or ads. Links and redirects remain HTTPS and host-allowlisted; downloaded bytes must match the signed size/hash. If the provider changes its markup, throttles, serves a challenge, or becomes unavailable, the next verified mirror is tried. No claim is made that a free hosting service guarantees permanent availability.

## File-level interface update

1. Fetch and verify the signed envelope, monotonic sequence, schema, bounds, and HTTPS origins.
2. Require `ui.minNative <= installed versionCode`.
3. For each declared path, hash the currently active/bundled bytes. Copy unchanged files locally; download only changed/missing files.
4. Bound and verify exact lengths and hashes. Reject traversal, duplicate paths, unapproved origins, native executable extensions, and oversized bundles.
5. Write all files into a private staging directory. An error discards staging without changing the active UI or vault.
6. Rename the completed staging directory, mark it ready, and activate as a single complete set on the next launch or explicit refresh.
7. Keep the prior healthy bundle. A new UI must report healthy after rendering. Failure to report health causes rollback on the next launch; that failed version is blocked until a newer release.

This is whole-file incremental delivery—not binary diffing within a changed JavaScript file, native hot-patching, or an APK rebuild on the phone. Automatic downloading occurs in the foreground when enabled. A first launch may stage an update and offer/await refresh instead of interrupting editing.

## Native updates

The native APK has a separate monotonically increasing Android versionCode. Downloading uses the same verified mirror/size/hash process. Before invoking the installer, the app also checks package ID, higher versionCode, supported minimum Android API, and matching installed signing certificate.

A private, read-only ContentProvider grants access to exactly the staged APK URI, never the vault. Android’s unknown-source approval and installer confirmation remain required. There is no root, `sudo`, or silent-install workaround.

## Notifications

Android JobScheduler performs a network-constrained, persisted six-hour best-effort check. A valid newer release can create an update notification if the user/system permits it. Opening/resuming the app also checks. Doze, battery restrictions, connectivity, force-stop state, and notification permission affect timing. A six-hour interval is not a delivery-time promise.

True push would require a configured FCM/UnifiedPush-compatible service or another delivery backend plus appropriate device lifecycle handling. That is not represented as completed in this build.

## Administration

GitHub Pages hosts a static release studio. The owner connects a short-lived repository-scoped GitHub token kept in tab memory only. GitHub authorizes `release-config.json` writes and Actions workflow dispatch. No public client holds the APK key, update-signing key, or a permanent admin credential.

The workflow serializes stable publications, builds native APKs only when requested, uploads public release artifacts, signs and verifies metadata, then commits the stable pointer and content-addressed blobs together. Only successfully completed runs constitute a publication. API/Pages caches can delay visibility; the raw feed is the primary path.
