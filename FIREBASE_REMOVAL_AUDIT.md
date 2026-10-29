# Firebase removal audit

## Removed integrations

| Previous integration | Previous responsibility | Replacement |
| --- | --- | --- |
| Firebase Firestore SDK and `FirestoreSyncManager` | Downloaded classes and announcements into Room | `PublicApiClient` calls the verified public `/api/v1/classes` and `/api/v1/announcements` endpoints. |
| Google Services Gradle plugin and `google-services.json` | Generated Firebase project configuration | Removed; the Android app now has no Firebase dependency. |
| Firebase AI and App Check dependencies | AI Studio/Firebase-specific client integration | Removed; no active Android call site used them. |
| Firebase CI secret injection | Supplied `google-services.json` during builds | Removed; CI builds from a clean checkout. |

## Offline behavior

Room remains the source used by the Compose UI. `PublicDataSyncWorker` downloads
complete public endpoint pages before replacing the relevant Room cache in one
transaction. A failed request leaves valid cached schedules and announcements in
place. WorkManager schedules a connected initial refresh and a periodic refresh.
Because the classes response is a complete paginated listing, removed classes
are removed on a successful refresh and their now-orphaned reminder preferences
are cleared in the same transaction.

## Build compatibility correction

KSP is upgraded from `2.3.5` to `2.3.6`. The project uses AGP 9, which requires
the KSP 2.3 line for built-in Kotlin support; `2.3.6` fixes the known CI
initialization error in `2.3.5` while retaining Kotlin `2.2.10` support.

## Authentication limitation

Better Auth in `apps/web` is configured for staff-only accounts. Its configured
session is cookie based, while the custom `/api/v1/auth/login` endpoint also
sets an httpOnly cookie and does not return a native token. There is no existing
Android-compatible bearer/JWT endpoint or student sign-in contract to integrate.
The public data migrated here does not require authentication. Add an explicit,
reviewed mobile token/session contract to the backend before adding Android
staff or student login.

## Push notifications

Firebase Cloud Messaging was not present in this Android project. Removing
Firebase does not provide background push delivery. The app retains local
notifications and checks the public release endpoint when it runs.
