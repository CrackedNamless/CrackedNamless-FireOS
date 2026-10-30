# Changelog

All notable changes to this project are documented here. Each release on GitHub carries the signed APK and the matching source code.

## [0.2.0] - 2026-10-30

### Added
- **Store tab.** A new "Store" tab next to Library in the top bar opens a chooser with two stores: Aptoide TV and the Amazon Appstore that comes with Fire OS. The choice opens from the Home, so you can download apps without leaving the launcher. A store that is not installed is marked "Not installed" and explains how to add it.
- `tools/install.sh` now also installs [Aptoide TV](https://tv.aptoide.com) (`cm.aptoidetv.pt`), the store behind the tab. The script asks Aptoide's public API for the current build, downloads it and compares its MD5 with the hash Aptoide publishes before installing; if the check fails nothing is installed. Run it with `--no-store` to skip the store. This also applies to the "Install it with Claude Code" prompt.

### Changed
- The Home screenshot in the README shows the new top bar, and a new screenshot shows the store chooser.
- `tools/undo.sh` leaves the store installed; remove it with `adb uninstall cm.aptoidetv.pt`.

## [0.1.1] - 2026-10-30

### Added
- The whole interface now follows the system language of the Fire TV. English is the default and Italian is included; any other language falls back to English. Every on-screen text, status and error message, the accessibility service description and the VPN session name come from `res/values*/strings.xml`, with proper plurals ("1 hour ago", "2 hours ago").
- To add a language, copy `app/src/main/res/values/strings.xml` to `values-xx/strings.xml` and translate it.

### Changed
- Relative times, weather descriptions and Bluetooth device types are built from resources instead of fixed Italian text; numbers and the temperature use the active locale.
- The default weather city is a resource (`weather_default_city`), so it can be translated, for example "Rome" and "Roma".
- The profile entry matched in Amazon's Settings now covers more languages (Profiles, Profili, Profile, Perfiles, Profils, Perfis, Profielen).
- README screenshots were retaken with the Fire TV set to English.

## [0.1.0] - 2026-10-30

First public release.

### Added
- Custom Home screen for Fire TV: frosted-glass top bar, "last app" hero card, your apps row, "continue watching" row and a full library.
- Status bar with Wi-Fi latency, Bluetooth, clock and a weather widget (Open-Meteo).
- Home button takeover through an accessibility overlay, so the Home screen shows up even though Fire OS pins Amazon's launcher.
- Fast Home: removes the 5-second wait Android applies after Home by calling `resumeAppSwitches` over a local ADB session (Home appears in about 1.2 s instead of 5.2-5.6 s).
- Local ADB tunnel: a loopback VPN (`10.113.77.0/24`) lets the app talk to the Stick's own `adbd`, which Fire OS refuses on `127.0.0.1`.
- Tools menu (long-press OK on the settings icon): bulk disable or restore 33 Amazon background services, performance preset, Prime Video background switch, free memory, per-service toggles.
- `tools/install.sh` and `tools/undo.sh` for one-command setup and rollback.
- Gradle wrapper and signed release build.
