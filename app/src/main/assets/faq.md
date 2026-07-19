## Requirements & Installation

### Why does DarQ only work on Android 10 and above?

Force dark was only added as a (hidden) option on Android 10 and above. If you want to change the theme of apps prior to Android 10, you may wish to use a Substratum theme.

### Why does DarQ need root or Shizuku/ADB access?

Since the final beta of Android 10, the force dark property (`debug.hwui.force_dark`) is only writable with root or with the `shell` UID (which `adb shell` and therefore Shizuku runs with).

If you are using Shizuku and want the service to automatically run on boot, you can use the modded Shizuku client:

- [Modded Shizuku (GitHub)](https://github.com/thedjchi/Shizuku)
- [Modded Shizuku Releases](https://github.com/thedjchi/Shizuku/releases)

### Who originally created DarQ?

DarQ was originally created by [Kieron Quinn](https://github.com/KieronQuinn) ([DarQ](https://github.com/KieronQuinn/DarQ)). It is now maintained, updated, and extended by [Mohit Arora](https://github.com/Arora-Sir) ([DarQ Reborn](https://github.com/Arora-Sir/DarQ-Reborn)).

### Why does upgrading from the official v2.2.1 release throw an "invalid package" error?

Because this is a community-maintained fork, the APK is signed with a different private developer certificate than the original release. Android blocks upgrades with mismatched signatures for security reasons.

To switch to this fork:

1. Go to **Backup & Restore** in the old app and export your settings.
2. Uninstall the official version.
3. Install the latest version from this fork.
4. Restore your settings from the backup.

All future updates from this fork will install seamlessly as normal upgrades.

### Why does upgrading from an older DarQ Reborn version (pre-v3.0) show an "incompatible package" error?

Starting from v3.0, DarQ Reborn's package name changed from `com.kieronquinn.app.darq` to `com.mohitarora.darqreborn` to cleanly separate this fork from the archived upstream project and enable listing on FOSS repositories (F-Droid, IzzyOnDroid). Android treats these as two separate apps, so a one-time manual switch is needed:

1. Export your settings using **Backup & Restore** inside the old app.
2. Uninstall the old version.
3. Install the new version.
4. Import your backup from Step 1.

All future updates from this point onwards will install as normal upgrades — no reinstall needed.

### How can I keep DarQ up to date? Is DarQ available on the Play Store?

DarQ is not on the Play Store, as it uses hidden APIs, which Google does not like. DarQ is instead available on GitHub, and will automatically check for updates when launched. If you would like to check manually, use the GitHub link on the main page of DarQ, and follow the "Releases" link.

## Background Performance & Monitoring

### How does DarQ monitor running apps?

DarQ uses a hidden API only accessible to privileged code (`IActivityManager.registerProcessObserver`), which allows it to know when an app is opened or closed without an Accessibility Service.

### Why does DarQ stop working in the background unless I launch it manually? (Aggressive OEM Battery Savers)

Some Android skins (especially OxygenOS/ColorOS by OnePlus/OPPO, and HyperOS/MIUI by Xiaomi) aggressively terminate cached background applications to save battery and RAM, even if set to **"Unrestricted"** battery usage.

When the main DarQ app process is killed, the Binder connection to the Shizuku helper service is severed, which automatically unregisters the process observer that monitors when apps are opened.

To fix this:

1. Go to **Advanced Options** inside DarQ.
2. Enable **"Keep service running in background"**. This starts a low-priority foreground service with a persistent notification to protect the process from being terminated.
3. If you want a clean status bar without a permanent notification icon, click **"Manage Notification"** to open system settings and hide/disable the persistent notification channel.

### Why does DarQ show a "Shizuku not running" notification on device boot, even though Shizuku starts up shortly after?

By default, Android launches all startup apps simultaneously when the device finishes booting. Since Shizuku has to initialize its native background daemon, it often takes several seconds to a minute to start. DarQ used to immediately fail if Shizuku wasn't ready.

To resolve this, we added a **"Wait for Shizuku on boot"** setting in Advanced Options (enabled by default). When active, DarQ will wait up to 3 minutes for Shizuku to start up before showing any warnings, ensuring a silent and successful connection in the background.

Additionally, DarQ now automatically detects and reconnects to Shizuku whenever Shizuku starts or restarts in the background, so you no longer need to open the DarQ app manually to trigger it.

## Force Dark Behavior & Troubleshooting

### Why does DarQ/force dark need the system dark theme to be enabled?

For some reason, in the final beta of Android 10, a requirement was added to force dark to make it only work when the system dark theme is enabled. A workaround has not been found (and may not even exist) for this, so it is required for DarQ to work too.

### Force Dark makes an app look weird! Can you fix it?

Short answer: No.

Long answer: Force dark is able to invert light colours to dark, and dark text and icons to light, but is not perfect. Gradients are often broken (such as in Facebook and Facebook Messenger), and sometimes regular images get caught in the crossfire and are inverted too, making them look incorrect. As force dark is literally just a property for DarQ to change, there's no way to configure or tweak its sensitivity and so apps can't be fixed. In a way, that's why DarQ even exists - so you can enable force dark on the apps that _do_ work, and leave it disabled for those that don't, or that already have a dark theme.

**Please do not pester app developers to fix apps with force dark enabled. Force dark is a not normally meant as a user-facing tool, it is meant for developers. If enough developers complain to Google about users pestering for fixes with force dark enabled, it may be removed from a future release of Android, preventing DarQ from working at all.**

### Force Dark doesn't work at all on an app! Can you fix it?

Sometimes an app launches too quickly for force dark to be applied. You may have luck closing and reopening the app, or opening and closing recent apps.

### Why does Force Dark not work on some apps?

These apps are web wrappers (websites running inside a WebView component) rather than native Android applications. System-level Force Dark only affects native Android UI components. WebViews render web pages independently and do not automatically respect the system's force dark flag.

### Which apps look good when force dark is enabled?

During testing; LinkedIn, Facebook and Google Opinion Rewards were found to be usable with force dark enabled. Plenty more will work too, it's up to you to experiment and see what works.

### Can Force Dark be made to work on all apps without Xposed?

No. Apps are able to disable force dark in code, so Xposed is the only way to prevent that.

### How should I configure the LSPosed / Xposed scope for DarQ?

For the Xposed module to function correctly, you must configure both LSPosed and DarQ:

1. **LSPosed Manager**: Add the target apps to DarQ's module scope. This gives the module permission to hook into those app processes.
2. **DarQ App**: Select the same apps in DarQ's app picker to enable Force Dark on them. Alternatively, enable **"Always use Force Dark"** in **Advanced Options** to automatically apply Force Dark to every app in the LSPosed scope without any manual selection.
3. **System Framework**: Do **not** select "System Framework" or "System UI" in the LSPosed scope. DarQ only needs to hook individual app processes - adding the system framework is unnecessary and can cause instability or system UI issues on some devices.

### Why does DarQ have its own app picker if LSPosed scope already controls which apps are selected?

They serve two different purposes.

- **LSPosed scope** is a security boundary managed by LSPosed itself. It controls which apps the module is *allowed* to inject into. Think of it as a permission list.
- **DarQ's app picker** is a user preference layer on top of that. It controls which of those permitted apps should *actually* have Force Dark applied.

For example, you might add 20 apps to the LSPosed scope but only want Force Dark actively applied to 5 of them. The DarQ picker gives you that extra granularity without having to change the LSPosed scope every time.

If you prefer not to manage two lists, enable **"Always use Force Dark"** in **Advanced Options**. This makes DarQ automatically apply Force Dark to every app in the LSPosed scope, so you only ever need to manage one list - the LSPosed scope.

### Should I add System Framework or System UI to the LSPosed scope for DarQ?

No. Only add the individual apps you want Force Dark to work on (for example, Amazon, LinkedIn, etc.). DarQ's Xposed module works by hooking inside each target app's own process to prevent that app from disabling Force Dark. It does not need access to the system framework at all.

Adding System Framework or System UI to the scope is unnecessary and may cause system UI glitches or instability on some devices.

### Why does the status bar invert (have black icons) when force dark is enabled? Can it be fixed?

This appears to be a bug in Android, and as force dark is a developer option, may not be fixed. It may be possible to fix it with DarQ, and this is being investigated. In the meantime, you can mitigate this issue by enabling Force Dark on a per-app basis rather than enabling it globally for all apps at once.

### Can you change the force dark colour in an app (eg. to full black)?

No. There is no customisation with force dark, so you cannot change the colours.

## Troubleshooting & Bug Reporting

### The app is crashing on launch or force-closing. How can I capture logs to report the issue?

If you encounter crashes or issues, sharing a logcat log is extremely helpful for diagnostics. Here is how to capture it on-device:

1. Install an open-source log viewer like **LogFox** (available on [GitHub](https://github.com/F0x1d/LogFox)).
2. Open LogFox and grant it Shizuku access.
3. Start recording logs, launch DarQ Reborn to trigger the crash, and copy the captured logs from the notification pop-up.
4. Share the copied crash log in your bug report or GitHub issue.
