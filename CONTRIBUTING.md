# Contributing to NH Files

Thanks for considering a contribution — bug reports, fixes, and small focused PRs are all welcome.

## Before you start
- Open an issue first for anything larger than a small fix, so the approach can be agreed on before you spend time on it.
- Keep PRs focused on one change; unrelated fixes make review slower for everyone.

## Development setup
1. Fork and clone the repo.
2. Open the folder in Android Studio (or Code on the Go) and let Gradle sync.
3. Run `./gradlew testDebugUnitTest` before opening a PR — the file-operations and ZIP-manager tests in particular should still pass (see `app/src/test/`).
4. `./gradlew assembleDebug` should produce an installable APK with no signing setup needed; a release build needs your own keystore (see the README's "Building a signed release APK" section) and isn't required for a PR.

## Code style
- Kotlin, Jetpack Compose, Material 3, MVVM — follow the existing structure under `app/src/main/java/com/nhdeveloper/nhfiles/` (`data/` for repositories and file/ZIP/APK logic, `ui/` for screens and the MainViewModel).
- No new network calls or ads — this app is offline-first by design.
- New file operations must stay data-safe: write-then-rename instead of in-place overwrite, confirm destructive actions, never delete without the user asking.

## Reporting bugs
Include your Android version, the exact steps, and — if it's a crash — the stack trace from `adb logcat` if you can get one. A screen recording helps a lot for UI issues.

## License
By contributing, you agree your contribution is licensed under this repo's MIT license (see `LICENSE`).
