# Contributing to Unscroll

Thanks for helping! Please read this before opening a pull request.

## Ground rules
1. **No network, ever.** Don't add the `INTERNET` permission, or any dependency that needs it, contains analytics or ads, or is non-free. CI will reject it.
2. **Original work only.** Don't copy code, text, icons, illustrations, screenshots or layouts from other apps, including the closed-source apps that inspired our feature list. Only use dependencies and assets under GPL-3.0-compatible FLOSS licenses (MIT, BSD, Apache-2.0, ISC, Zlib, MPL-2.0, LGPL, GPL-3.0, OFL-1.1 for fonts, Unlicense, CC0) and list bundled ones in [NOTICE](NOTICE). No proprietary SDKs, Google Play Services or Firebase.
3. **Be kind.** We follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Developer Certificate of Origin (required)
Every commit must be signed off. This certifies that you wrote the code or have the right to submit it under GPL-3.0 ([developercertificate.org](https://developercertificate.org/)):
```bash
git commit -s -m "feat(limits): add open-count limit"
```
This adds `Signed-off-by: Your Name <you@example.com>`. CI blocks PRs that contain unsigned commits.

## Setup
- Flutter (the version pinned in `.github/workflows/ci.yml`), JDK 17, Android SDK
- `flutter pub get`
- Run: `flutter run`

## Before opening a PR
```bash
dart format .
flutter analyze
flutter test
cd android && ./gradlew testDebugUnitTest lint
```
- Add tests for new logic. Rule-engine changes need Kotlin unit tests that cover edge cases (midnight, clock changes, reboot).
- Update [docs/FEATURES.md](docs/FEATURES.md) when a feature changes.
- Fill in the PR template checklist.

## Commit style
[Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `ci:`, `chore:`.

## Security
Report vulnerabilities privately; see [SECURITY.md](SECURITY.md).
