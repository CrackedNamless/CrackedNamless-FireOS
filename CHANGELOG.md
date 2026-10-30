# Changelog

All notable changes to this project are documented here. Each release on GitHub carries the signed APK and the matching source code.

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
