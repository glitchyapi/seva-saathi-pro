# Install and test the first Android build

## Download the actual APK

Use the release asset, not GitHub’s “Download ZIP” or a file-host preview page:

https://github.com/glitchyapi/seva-saathi-pro/releases/latest/download/Seva-Saathi.apk

The first build is approximately **399 kB / 389 KiB**. A small APK can be valid; size alone is not an installation test. This one contains Android binary manifest/resources, DEX code, offline fonts, images, and UI assets. Its exact byte count and SHA-256 are in the verified release metadata and alongside the workspace APK.

1. Use an Android phone running **Android 8.0 (API 26) or later**.
2. Keep **Android System WebView** (or the device’s active WebView provider) updated. The app bundles no browser engine.
3. Download the file completely. Its extension must be `.apk`, not `.html`, `.zip`, `.apk.html`, or `.aab`.
4. Open the file from Downloads. If Android asks, allow installation **from the browser/file manager you are using**. This is different from granting access to all your storage.
5. Confirm installation in Android’s installer. You may turn off that source’s install permission afterwards.
6. Open Seva Saathi. The Android journal is initially empty; sample sevas appear only in the labelled browser preview.

Do not bypass a malware warning without investigating it. This project does not ask for root access, a notification-listener service, contacts, or all-files access.

## If you still get a parsing/install error

- Redownload the asset through the link above. A host’s HTML landing page saved with an `.apk` extension is not installable.
- Check your Android version and available space. Package extraction needs more free space than the compressed APK size.
- Compare the file’s SHA-256 with the signed feed or release checksum. The first four APK bytes are normally `PK` followed by ZIP header bytes, **not** `<html` or `<!DOCTYPE`.
- If a different app with package ID `org.sevasaathi.app` is already installed with another certificate, Android cannot upgrade it using this project’s key. **Back up existing data before considering uninstalling anything.** Uninstalling this app removes its encrypted journal and device key.
- A work-profile/device-management policy, parental restriction, or OEM installer may block sideloading. Use the device’s legitimate admin controls; the app does not bypass them.
- Send the exact error screenshot, phone model, Android version, and downloaded file size. Do not send a GitHub token or private journal backup password.

## First-device acceptance checklist

1. Turn on airplane mode, open the app, add a seva, close it, and reopen. The record should remain.
2. Edit its title/duration, search for it, switch category filters, and delete a test record.
3. Switch English ↔ Hindi in My space. Your own entry text should not be translated automatically.
4. Export an encrypted `.seva` backup using a strong password. Try a wrong password; the journal should remain unchanged. Restore with the correct password only after confirming replacement.
5. Generate a Bhandara banner with confirmed details and save its PNG through the system file picker.
6. Reconnect to the internet. Open **My space → App & updates → Check for updates**. The signed feed should verify, or a clear offline/error message should leave the journal usable.
7. On Android 13+, grant or deny notification permission. Neither choice should block journal access.
8. Test UI activation and the next higher-version APK on a spare device before broad distribution. Accept the Android installer prompt; verify that records survive the upgrade.

## Backups matter

Local keys are held by Android Keystore and cannot be exported as a portable backup. Uninstalling or clearing app data removes the journal/key. Use **Create encrypted backup** before switching phones or uninstalling. There is no forgotten-password recovery for the portable backup.
