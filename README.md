# JumpStart

A fast, minimal Android launcher built with Kotlin and Jetpack Compose.

JumpStart puts a universal search bar front and center: type to find and launch apps,
look up words, do math, convert units, or find contacts. Edge gestures and a
shortcut accessibility service provide quick access without leaving the current app.

## Features

- **Universal search** — apps, contacts, dictionary definitions, calculator, and unit conversions
- **Edge actions** — swipe edges to launch apps or open the search overlay
- **Offline dictionary** — bundled WordNet 3.0 database (see `tools/build_dictionary.py`)
- **Built for low-end devices** — aggressive R8/resource shrinking and a baseline profile for fast startup

## Building

Requires Android SDK 34 and JDK 17.

```sh
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK (signed with the debug keystore)
./gradlew test              # unit tests
```

`local.properties` must contain your local `sdk.dir`; it is intentionally not tracked.

## Releases

Pushing a version tag triggers [`.github/workflows/release.yml`](.github/workflows/release.yml),
which builds the release APK on GitHub Actions and attaches it to a GitHub Release:

```sh
# update versionName in app/build.gradle.kts first
git tag v1.0.9
git push origin v1.0.9
```

CI checks that the tag matches `versionName` before building. APKs are not stored in git.

CI signs release APKs with a private keystore held in repository secrets
(`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`), so every published release has the same signature and
in-app upgrades install cleanly. Local builds still use the debug keystore.
APKs built locally will not accept a CI-built update over the top — uninstall once
to move to the CI-signed line.

## Over-the-air updates

JumpStart reads the latest GitHub Release at launch (once per process) and offers a
download/install prompt when `tag_name` is a higher version than the installed one.
Settings → **Updates** has a manual "Check for updates" button and shows the
installed version. The APK is downloaded from the release asset, written to the app's
cache, and installed through a `FileProvider` content URI (required on Android 7+).
If "install unknown apps" is not yet granted, the user is sent to that setting and
the install resumes on return.

`REQUEST_INSTALL_PACKAGES` is declared in the manifest. No notification permission
is needed: the prompt is in-app.

## License

The bundled dictionary data is derived from Princeton WordNet 3.0 — see
`app/src/main/assets/WORDNET_LICENSE.txt`.
