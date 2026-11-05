# Changelog

All notable changes to DarQ Reborn are listed here. Versions follow the GitHub releases at https://github.com/Arora-Sir/DarQ-Reborn/releases.

## 3.2.0

### Added
* Appearance screen with light, dark and system themes, pure black, tinted icons and accent colors (wallpaper, seven presets, single wallpaper colors and a custom color picker with palette, saturation and hue controls, a hex field and a contrast check).
* Report an issue for bugs and feature ideas with a structured preview, a redacted diagnostic log and prefilled GitHub forms, plus crash reports offered on the next launch.
* Main screen status line showing the connected mode and the number of selected apps.
* Support development row, Developer sheet, Credits sheet and an Obtainium one-tap link in Settings and the update sheet.
* Language setting in Appearance with System default, English, Hindi, Ukrainian and Simplified Chinese. The switch applies on the same screen with no flash, Backup & Restore includes it, and Android 13 and later list the languages in the system per-app language screen.
* Hindi translation, plus Ukrainian and Simplified Chinese translations of the new screens (all machine translated).

### Changed
* Every dialog and sheet blurs the screen behind it and shares one look.
* The app picker groups apps under letter headers, animates changes and offers Clear search when nothing matches.
* Links always open in the default browser and toasts no longer stack.
* The large app title keeps its position while the screen loads.
* Predictive back is supported on Android 14 and later.
* Build moved to AGP 8.13, Gradle 8.13, Kotlin 2.4 and compileSdk 36.

### Fixed
* The service status in the title bar shows under the title at large font sizes instead of being clipped.
* Suggest a feature no longer shows a greyed log checkbox that looked mandatory. The card now says no log is needed.
* Root mode no longer ends on "Service Timeout" after the app is force-stopped.
* Force dark works again on LSPosed 1.9.2, its forks and Vector. The default build ships only the legacy Xposed entry.
* The installed app list is cached, so the app picker opens quickly.
