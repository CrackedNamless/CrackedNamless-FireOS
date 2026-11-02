# Changelog

All notable changes to this project are documented here. Each release on GitHub carries the signed APK and the matching source code.

## [0.3.0] - 2026-11-02

### Added
- **Folders in the Library.** Hold OK on an app and choose "Move to folder…": pick an existing folder, a ready-made one (Streaming, Games, Music, Tools, Kids) or type a new name. Folders appear first in the Library as a card with a collage of their apps; open one with OK and return with Back. Hold OK on a folder to rename or delete it (its apps go back to the Library). Apps in a folder leave the Home row, so the row keeps only the apps you use most.
- **Uninstall from the Library.** Hold OK on an app and choose Uninstall: Fire OS asks for confirmation. System apps are not removed; the menu points to Tools to disable them instead. "App info" opens the system page of the app.
- **Full-card app art.** Every card is filled by the app's wide banner when it has one; apps with only an icon get the icon centred over a gradient in the icon's own colour. Cards are always 16:9 and have rounded corners.
- **Tools organised in sections.** The Tools menu is now split into "Speed and memory", "Amazon background services" and "Individual services", with clearer headings and a darker, quieter layout.
- **Ready out of the box.** The first time the launcher opens, it turns on Fast Home, Performance and Disable all unneeded services by itself (you only choose "Always allow" for USB debugging once). It does this once, so settings you switch off later stay off. `tools/install.sh` also pre-approves the local VPN so no consent screen appears.
- **Automatic updates.** The launcher checks the latest GitHub Release every few hours and downloads a newer APK in the background. A small card appears in the bottom right corner, over whatever app is on screen: press OK to update now (installs and restarts only the launcher, a few seconds) or Back to postpone (any other navigation key also postpones it; with no answer it closes after 20 seconds and installs when the Fire TV goes to standby). If a video or a show is playing, it is paused first; after the update the app you were in comes back and playback resumes at the same point. After an update the accessibility service is switched on again by the app.

### Fixed
- **Random standby.** The Fire TV could go to standby by itself, even seconds after waking up. The power-key watcher reads the system log, and when that log reader restarted, its own error message ("read: unexpected EOF!") was taken for a press of the power button. Only real power-key and Home lines are accepted now.

### Changed
- **Faster.** App icons and banners load in the background and are kept in memory, the grids no longer re-measure their cards, and the app list is cached. Cold start of the Home measured on a Fire TV Stick Lite went from about 1.4 s to about 0.5 s.
- The Library grid shows 6 columns with a title and a hint above it.
- The Tools menu and the first-run setup share one list of services and performance settings (`Tweaks`).

## [0.2.0] - 2026-10-30

### Added
- **Store tab.** A new "Store" tab next to Library in the top bar opens the Amazon Appstore that comes with Fire OS, so you can download apps without leaving the launcher.
- **Screensaver always off.** The launcher turns Amazon's screensaver off and keeps it off: every 30 seconds it sets `screensaver_enabled` back to 0 if Amazon turned it on again. `tools/install.sh` also grants the `WRITE_SECURE_SETTINGS` permission this needs and disables Amazon's screensaver app (`pm disable-user`, it stays on the device). `tools/undo.sh` brings both back.
- **Real profile icon.** The top-left icon shows the avatar of the active Amazon profile. Amazon gives apps no way to read it, so when "Who's Watching?" is open the launcher reads the profile names through accessibility, takes one screenshot over its own local ADB link (only if the VPN and ADB permissions were already granted) and crops each avatar. The focused profile when the screen opens is the active one, and choosing another profile switches the icon. Until you open the profile screen once, the generic icon stays.
- On the first install, `tools/install.sh` turns off Amazon's FireTV Troubleshooting app (`pm disable-user`, it stays on the device) so it no longer clutters the app list. Undo with `adb shell pm enable com.amazon.firetv.troubleshooting`. Later runs of the script leave it alone.

### Fixed
- **Profile icon.** The profile icon in the top bar now goes all the way to Amazon's "Who's Watching?" screen. Before, it stopped on "Account & Profile Settings". It also matches the "Account & Profile Settings" tile, and when Amazon asks for the PIN it waits for you to type it (or stops if you cancel). The launcher never sees or stores the PIN. The service now declares the gesture capability (`canPerformGestures`) to press Amazon's rows, so after updating, turn the accessibility service off and on once (or restart the Fire TV) so Fire OS applies it.
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
