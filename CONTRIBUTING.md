# Contributing to Avyra

Thanks for taking time to help. Avyra is still changing quite a lot, so a focused pull request is usually easier to review than one that changes several unrelated parts of the app.

## Before starting

1. Check the issue tracker to see if someone is already working on the same thing.
2. Open an issue first for a large feature or a change to storage, playback behavior, app identity, or network providers.
3. Do not include account cookies, tokens, signing files, paid media, or private source URLs in an issue or commit.

## Local checks

Use JDK 17, Android SDK 36, NDK `27.0.12077973`, and CMake `3.22.1`.

On Windows:

```powershell
.\gradlew.bat testProdReleaseUnitTest assembleDevDebug
```

On Linux or macOS:

```bash
./gradlew testProdReleaseUnitTest assembleDevDebug
```

If your change affects native analysis or a production-only path, also run:

```powershell
.\gradlew.bat assembleProdRelease
```

A release build can be unsigned on a clean checkout. That is fine for compile verification. Do not commit a release key.

## Pull requests

- Explain what changed and why.
- Link the issue when one exists.
- Include the exact checks you ran.
- Add screenshots or a short recording for visible UI changes.
- Keep unrelated formatting and generated files out of the diff.
- Preserve copyright and third-party license headers.
- Add tests when a behavior can be tested without an Android device.

Playback and network bugs are easier to understand with the Android version, app version, track or screen involved, clear steps, and a small log excerpt with personal data removed.

## Code style

Follow the surrounding Kotlin and Compose style. Prefer small functions with clear names, avoid adding a new abstraction for one call site, and write comments for the reason behind a decision instead of repeating the code.

User-facing text should be simple and direct. Please do not add corporate marketing language or promises that depend on a third-party service staying unchanged.
