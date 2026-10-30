# Changelog

All notable changes to this project are documented here. Each release on GitHub carries the signed APK and the matching source code.

## [0.2.0] - 2026-10-30

### Added
- **Store tab.** A new "Store" tab next to Library in the top bar opens the Amazon Appstore that comes with Fire OS, so you can download apps without leaving the launcher.
- On the first install, `tools/install.sh` turns off Amazon's FireTV Troubleshooting app (`pm disable-user`, it stays on the device) so it no longer clutters the app list. Undo with `adb shell pm enable com.amazon.firetv.troubleshooting`. Later runs of the script leave it alone.

### Fixed
- **Profile icon.** The profile icon in the top bar now goes all the way to Amazon's "Who's Watching?" screen. Before, it stopped on "Account & Profile Settings". If you set a PIN for switching profiles, Amazon asks for it there; the launcher never sees or stores it. The service now declares the gesture capability (`canPerformGestures`) to press Amazon's rows, so after updating, turn the accessibility service off and on once (or restart the Fire TV) so Fire OS applies it.
- **Power button.** The remote's power button puts the Fire TV in standby again. The log line that the real remote produces was not being recognised, so the key was ignored.

### Changed
- **Search tab removed.** Amazon's search screens can only be opened by Amazon's own apps (signature permission), and the voice assistant call did not show anything useful. The top bar is now Home, Library and Store. Use the voice button on the remote to search.
- The Home shows "Made by CrackedNamless - vX.Y.Z" in the bottom right corner (localized, version read from the app).
- The Home screenshot in the README shows the new top bar.

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
