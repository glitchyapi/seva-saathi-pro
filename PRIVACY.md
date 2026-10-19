# Privacy notes

Seva Saathi is an independent, offline-first community journal. It has no account system, analytics SDK, advertising SDK, or journal-sync service.

## Android app

Your seva entries, notes, name, and preferences remain in an encrypted file in the app’s private Android storage. The encryption key is held by Android Keystore. The device’s hardware/security implementation determines how that key is protected. The app can decrypt records to display them while you use it; encryption is not an app lock or a guarantee against device compromise.

The app contacts GitHub to check public release metadata and contacts OnlyFiles/GitHub when downloading public app updates. Those services receive ordinary network metadata such as your IP address, user agent, and request timestamps under their own policies. **No journal records, profile names, or backup passwords are sent with these requests.** The app never displays OnlyFiles advertising/landing pages in its WebView; it retrieves the binary link natively and verifies the file.

Notifications require your permission where Android supports it. The app does not read other apps’ notifications. No contacts, location, camera, microphone, broad-storage, or notification-listener permission is requested.

## Explicit exports

- Encrypted backups are written only to a location you choose. Their strong password is needed to restore them; there is no recovery service.
- Bhandara banner PNGs and shared announcement text are not encrypted, because they are intended to be shared. Avoid including information you do not want others to see.
- Your chosen document provider or share-target app has its own privacy policy, including whether it uploads files.

Uninstalling or clearing app data removes the local journal and its device key. Export a portable encrypted backup first if you want to keep records.

## Browser preview

The clearly labelled browser preview uses unencrypted local storage, starts with sample entries, and is not the Android vault. Treat it as a design/functionality demo; do not enter sensitive personal data. “Start fresh” clears the sample/local preview journal. Browser-generated portable backups still use password-based encryption where the browser supports the required APIs.

## Release studio

The studio edits public announcements and release metadata, not users’ journals. A connected GitHub token stays in tab memory, not local/session storage. It is sent only to GitHub API endpoints. Refreshing/disconnecting removes it from the page’s active state. Browser extensions, compromised devices, or malicious modifications to the studio can still be security risks; use a trusted device and repository-scoped, short-lived credentials.

Hosting providers may log requests. Public GitHub repository content, releases, and announcement drafts are visible to others. Never publish signing keys, tokens, personal journal data, or private backup files there.
