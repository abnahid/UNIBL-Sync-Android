# UNIBL Sync

UNIBL Sync is a Kotlin/Jetpack Compose Android app for University of Banja Luka
MBBS students. It provides timetable views, announcements, offline access,
class reminders, timezone support, and links to online class meetings.

## Features

- Daily, weekly, and agenda timetable views
- Group and attendance-mode filtering
- Public API synchronization with a local Room offline cache
- Announcements with read/unread tracking
- Per-class and global reminder settings
- Alarm, sound preview, vibration, and notification support
- Timezone-aware class times
- ICS calendar export and meeting links

The Android app does not contain an admin login or admin passcode. Schedule and
announcement management remains on the private website; the app consumes
published data.

## Screenshots

Screenshots will be added here when approved release screenshots are available.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Android Gradle Plugin with Gradle Kotlin DSL
- Room
- Kotlin Coroutines, Moshi, Retrofit, and OkHttp

## Requirements

- JDK 17
- Android SDK 36 with the required platform and build tools
- Git
- The included Gradle wrapper (`gradlew`, `gradlew.bat`, and
  `gradle/wrapper/gradle-wrapper.jar`)

## Configuration

Edit the root `app-config.properties` file to set the public API and website
routes. It contains public URLs only; never put credentials, tokens, passwords,
database URLs, or signing material in it.

The app uses the configured public API for published schedule and announcement
data. No Firebase project, Firebase configuration file, or Firebase credential
is required to build or run it.

## Build on Windows

```powershell
cd UNIBL-Sync-Android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build on macOS/Linux

```bash
cd UNIBL-Sync-Android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Releases

Push a tag such as `v1.0.0` to run the tagged-release workflow. It publishes a
tested debug APK to the GitHub Release because no production signing key is
stored in this repository. Signed release builds are available through the
manual workflow after the repository owner configures the required GitHub
Actions secrets.

APK downloads are available from the repository's **Releases** page; APK
binaries are not committed to Git.

When the app starts or returns to the foreground, it checks the public
`/api/v1/releases?latest=true` endpoint. If a newer release is published, the
app shows an update notification linking to the configured APK URL. This is an
optional network check and does not block offline use.

## Testing and troubleshooting

Run `:app:testDebugUnitTest` before submitting changes. If Gradle cannot find
Java, install JDK 17 and set `JAVA_HOME`. If Android SDK 36 is missing, install
it through Android Studio's SDK Manager. Never work around build failures by
committing local SDK files, credentials, or signing keys.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the change and validation workflow.

## Security

See [SECURITY.md](SECURITY.md) for responsible vulnerability reporting.
