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

## License

The bundled dictionary data is derived from Princeton WordNet 3.0 — see
`app/src/main/assets/WORDNET_LICENSE.txt`.
