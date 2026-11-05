# Privacy Policy for DarQ Reborn

Effective 2026-10-26. Developer: Mohit Arora (@Arora-Sir).

## Tier
Network by Function. DarQ Reborn uses the network only to check GitHub for new releases and to download an update you approve. There are no accounts, analytics, ads or developer servers.

## Data the app stores
DarQ keeps its settings (selected apps, schedule, theme choices) in app storage and in a revisioned snapshot file in the same private folder, so a lost preferences file can be restored. It keeps a cached list of installed apps to open the app picker quickly. Crash reports and the handled error log are written to private app storage and are offered to you once. A downloaded update APK is kept in the app cache until installed. Clearing the app storage or uninstalling DarQ deletes all of it.

## Permissions
* `INTERNET` checks GitHub releases and downloads updates you approve.
* `REQUEST_INSTALL_PACKAGES` lets DarQ hand a downloaded update to the system installer.
* `QUERY_ALL_PACKAGES` lists installed apps in the app picker. The list stays on the device.
* `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` compute sunrise and sunset for the Auto Dark schedule. They are optional and the position is never sent anywhere.
* `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` keep the service and the Auto Dark schedule running.
* The Shizuku permissions connect DarQ to the Shizuku service. Root mode uses root access that you grant.
* `READ_EXTERNAL_STORAGE` lets you pick a backup file to restore.

## Reports and diagnostics
Report an issue shows the exact text before anything leaves the device. Installed apps are replaced by short codes, and coordinates and email addresses are removed. Nothing is sent until you tap a send option. GitHub opens in your default browser with a prefilled form, and the full log is copied to your clipboard so you can paste it.

## Backups
Backup and Restore writes a settings file to a location you choose. Android backups exclude crash reports, the app list cache and diagnostic preferences.

## Deletion
Clear storage in Android app settings or uninstall DarQ. There is nothing to delete on a server.

## Contact
https://mohitarora.me and https://github.com/Arora-Sir/DarQ-Reborn/issues/new/choose
