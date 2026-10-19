# Security and maintainer checklist

This first build is not independently audited. Report a potential vulnerability privately to the repository owner; do not attach a token, signing key, unredacted journal, or backup password to a public issue.

## Owner actions

- Revoke the broad token exposed during project setup. Replace it with a short-lived, fine-grained token limited to this repository.
- Keep `APK_KEYSTORE_B64`, `APK_STORE_PASSWORD`, and `UPDATE_PRIVATE_KEY` in GitHub Actions secrets, never in repository files or browser code.
- Retain secure offline backups of the original APK signing key and release-signing key. Losing the APK key prevents normal same-package native upgrades. Changing the update key requires a deliberate signed-native key-rotation plan.
- Restrict who can push to `main`, modify workflows, or dispatch releases. A maintainer able to change trusted release code may be able to misuse repository secrets. Consider protected branches/environments and required reviews before wider distribution.
- Review/pin workflow dependencies and actions before accepting untrusted contributions. Do not expose release secrets to pull-request code.
- Test on representative Android versions and OEMs before mass distribution. Maintain a spare test installation with a non-sensitive journal and known encrypted backup.

## Current safeguards

Authenticated AES-GCM local encryption; bounded authenticated password backups; atomic vault writes; non-exportable Keystore key; no broad storage or notification-listener access; no WebView networking; signed/pinned update manifest; SHA-256 size/hash verification; HTTPS host allowlist; explicit OnlyFiles binary resolution; monotonic observed sequence; bounded staging; rollback of unacknowledged UI launches; separate native/UI channels; APK package/version/certificate verification; narrow read-only installer URI; user-confirmed installation.

## Limitations to review before production scale

- No formal penetration test, privacy/legal review, large dataset benchmark, accessibility audit, or exhaustive Android/OEM test matrix yet.
- No app-level biometric/PIN gate. Use the device screen lock; an unlocked app can show its journal.
- Auto updates trust code signed by the release operator. Keep that signing pipeline secure.
- UI health confirmation catches failure to reach the rendering checkpoint, not every future logic bug. Test new releases and maintain backups.
- On-device encryption protects stored bytes; it cannot guarantee safety from a rooted/compromised OS, hostile browser extensions in the preview/admin, or invasive physical attacks.
- No always-on/instant push service. Android controls background scheduling and can stop jobs after force-stop or under restrictions.
- Free provider limits/caching/availability can change. OnlyFiles link parsing is tested against the current page markup and falls back to GitHub; maintain this adapter.
- A single bounded JSON vault is appropriate for the first-build scale but rewrites the compressed journal on each edit. Evaluate a transactional encrypted database and migration plan for substantially larger datasets.
