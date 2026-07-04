# DarQ (Modded Fork)

![DarQ](assets/darq_banner.png)

DarQ provides a per-app selectable force dark option for Android 10 and above.

> [!NOTE]
> This is a modded fork maintained by **Mohit Arora / Arora-Sir**. The original repository by [KieronQuinn](https://github.com/KieronQuinn/DarQ) is archived.
> 
> [!IMPORTANT]
> **Upgrading from the Official Release (v2.2.1 or older):** Due to a signature mismatch from the transition to this fork, you **must uninstall the official version first** before installing updates from this repository. Future updates from this fork will upgrade directly as normal.

It uses a root or [Shizuku](https://shizuku.rikka.app/) (ADB) service to apply the theme seamlessly and quickly, without needing an accessibility service.

## Requirements & Setup

DarQ requires either **Root Access** or the **Shizuku** service to be running on your device to modify system theme properties.

### Shizuku Setup (For Non-Rooted Devices)
If your device is not rooted, you must set up **Shizuku** before running DarQ:
1. Download and install **Shizuku** from the [Shizuku GitHub Repository](https://github.com/RikkaApps/Shizuku) or the [Google Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api).
2. Open Shizuku and follow the in-app guide to start the service (using Wireless Debugging on Android 11+ or ADB command line on a computer).
3. Once the Shizuku service is running, open DarQ and grant it Shizuku access when prompted.

DarQ also provides an option to apply the system dark theme (as well as selectable force dark) only after sunset and before sunrise, protecting your eyes when it's most needed. 

Please read the Frequently Asked Questions sections in the app or [here](https://github.com/Arora-Sir/DarQ/blob/master/app/src/main/assets/faq.md) for more information and some answers to questions.

[Download from GitHub Releases](https://github.com/Arora-Sir/DarQ/releases)