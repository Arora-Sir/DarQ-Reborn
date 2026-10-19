# Device automation

Python helpers and suites that drive real devices and emulators over ADB to verify DarQ end to end. Run them with `uv` so Pillow is provided without a project environment.

```bash
uv run --with pillow python scripts/device/e2e_regression.py --serial 100.127.59.103:5555 --min-idle 300
```

## Safety rules

The preflight refuses to start when the device is streaming (a Super Artemis `Game` window has focus), in a call, locked, asleep or used recently. Use `--min-idle 300` on physical devices and `--min-idle 0` on emulators. Every tap re-checks the stream focus. Each suite installs the debug build over the existing install, which keeps data because both builds share the debug signing key, and then restores the user's DarQ preferences, the night mode and the installed apps whatever the outcome. The last step relaunches DarQ once so it rebinds its service and reschedules Auto Dark alarms. Physical devices are never rooted. Root and Xposed checks run only on emulators.

## Emulators

The emulators come from the shared fleet maintained in the android-mastery skill (`scripts/emulators/fleet.py`, also available from the Start menu folder "Android Emulators"). Each has a fixed serial, so suites can target it directly.

| Key | Serial | Android | Use |
| --- | --- | --- | --- |
| a10 | emulator-5560 | 10 (minSdk) | Shizuku regression |
| a13 | emulator-5562 | 13 | Shizuku regression |
| a16 | emulator-5564 | 16 Play | Shizuku regression |
| a17 | emulator-5566 | 17, 16 KB pages | Shizuku regression |
| a11root | emulator-5570 | 11 rooted, LSPosed 1.9.2 | Xposed suite (old framework, boolean setForceDark) |
| a16root | emulator-5572 | 16 rooted, Vector 2.2 | Xposed suite (modern framework, int ForceDarkType, preferences redirect) |

A Shizuku run on an emulator:

```bash
python <fleet.py> up a13
python <fleet.py> night a13 yes
python <fleet.py> shizuku a13
adb -s emulator-5562 install -r app/build/outputs/apk/debug/app-debug.apk
uv run --with pillow python scripts/device/emulator.py bootstrap emulator-5562
uv run --with pillow python scripts/device/e2e_regression.py --serial emulator-5562 --min-idle 0
```

An Xposed run on a rooted emulator compares the previous release with the candidate:

```bash
uv run --with pillow python scripts/device/e2e_xposed.py --serial emulator-5572 --framework vector --apk <previous-release.apk> --label baseline --fresh
uv run --with pillow python scripts/device/e2e_xposed.py --serial emulator-5572 --framework vector --apk app/build/outputs/apk/debug/app-debug.apk --label candidate
```

## Fixtures

`testing/forcedark-probe` is a debug-only app with a full-screen white panel. `ProbeActivity` uses a light theme that leaves `forceDarkAllowed` unset, so it turns dark only when force dark reaches it. `OptOutProbeActivity` uses a theme not flagged as light (`isLightTheme=false`), so the system skips force dark and only Aggressive Force Dark in Xposed mode can darken it. Screenshot luminance of the panel is the pass or fail oracle, and the probe logs the `debug.hwui.force_dark` value and the night mode it saw under the `DarqProbe` tag.

## Suites

`e2e_regression.py` covers Shizuku or root mode: launch and service connection, the settings snapshot and its restore after the preferences file is lost, force dark reaching a selected app and leaving a deselected one, the main settings screens, the hidden Xposed entry, Auto Dark being re-armed after an in-place update, and a crash sweep.

`e2e_xposed.py` covers the Xposed module: module and scope setup through the Vector CLI or the LSPosed database, the self-hook, the SystemProperties hook, Aggressive Force Dark on an opted-out app, clearing a stale global force dark value, the settings snapshot surviving the LSPosed preferences redirect going away, and the hook failure log.

Reports and screenshots land in `build/e2e/<timestamp>/`.
