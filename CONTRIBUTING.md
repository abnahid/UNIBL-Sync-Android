# Contributing

1. Create a focused branch from `main`.
2. Keep Android and private website/backend code separate.
3. Do not commit API credentials,
   keystores, passwords, tokens, or APK binaries.
4. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` (or the Windows
   wrapper equivalent) before opening a pull request.
5. Describe configuration, migration, and user-visible changes in the pull
   request.

Pull requests should preserve the existing package name and signing identity
unless a deliberate migration has been reviewed by the maintainers.
