"""DarQ Xposed suite for a rooted fleet emulator (Magisk plus LSPosed 1.9.2 or Vector 2.2).

Runs the legacy Xposed module checks for one DarQ build. Run it first with the 3.1.10 release as a
baseline and then with the candidate build installed over it, so every fix is shown against the old
behaviour and the upgrade path (LSPosed preferences redirect plus snapshot) is exercised too.

Usage:
    uv run --with pillow python scripts/device/e2e_xposed.py --serial emulator-5572 --framework vector --apk <3.1.10.apk> --label baseline
    uv run --with pillow python scripts/device/e2e_xposed.py --serial emulator-5572 --framework vector --apk app/build/outputs/apk/debug/app-debug.apk --label candidate
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adbx import Device  # noqa: E402
from emulator import bootstrap  # noqa: E402
from e2e_regression import (  # noqa: E402
    DARK_MAX, LIGHT_MIN, PKG, PROBE_PKG, SERVICE_TIMEOUTS, Report, open_main, set_probe_selected,
)

ROOT = Path(__file__).resolve().parents[2]
FLEET = Path.home() / ".gemini/skills/Personal-Skills/Custom-Skills/android-mastery/scripts/emulators/fleet.py"
SU = "/debug_ramdisk/su -c"
VECTOR_CLI = "/data/adb/modules/zygisk_vector/cli"
LSPOSED_DB = "/data/adb/lspd/config/modules_config.db"
FORCE_DARK_PROP = "debug.hwui.force_dark"
FLEET_KEYS = {"emulator-5570": "a11root", "emulator-5572": "a16root"}


def su(dev: Device, command: str, check: bool = False) -> str:
    return dev.sh(f"{SU} \"{command}\"", check=check)


def app_uid(dev: Device, package: str) -> int:
    # pm list -U prints "package:<name> uid:<uid>" on every Android version, unlike dumpsys field names.
    match = re.search(rf"package:{re.escape(package)} uid:(\d+)", dev.sh(f"pm list packages -U {package}"))
    return int(match.group(1))


def fleet(*args: str) -> None:
    subprocess.run([sys.executable, "-I", str(FLEET), *args], check=True)


def grant_root(dev: Device, package: str) -> None:
    # DarQ uses its libsu root service on rooted devices, so Magisk is told up front to allow it instead of prompting.
    su(dev, f"/debug_ramdisk/magisk --sqlite 'REPLACE INTO policies (uid,policy,until,logging,notification) VALUES({app_uid(dev, package)},2,0,1,0)'")


def enable_module(dev: Device, framework: str, scope: list[str]) -> None:
    if framework == "vector":
        su(dev, f"{VECTOR_CLI} modules enable {PKG}")
        su(dev, f"{VECTOR_CLI} scope add {PKG} {' '.join(scope)}")
        return
    # LSPosed 1.9.2 has no CLI. Its daemon reads this database at boot, so the change is applied by a reboot.
    wait_module_row(dev)
    statements = [f"UPDATE modules SET enabled=1 WHERE module_pkg_name='{PKG}'"]
    statements += [f"INSERT OR IGNORE INTO scope (mid, app_pkg_name, user_id) SELECT mid, '{app}', 0 FROM modules WHERE module_pkg_name='{PKG}'" for app in scope]
    su(dev, f"sqlite3 {LSPOSED_DB} \\\"{'; '.join(statements)}\\\"")
    restart(dev)


def disable_module(dev: Device, framework: str) -> None:
    if framework == "vector":
        su(dev, f"{VECTOR_CLI} modules disable {PKG}")
    else:
        su(dev, f"sqlite3 {LSPOSED_DB} \\\"UPDATE modules SET enabled=0 WHERE module_pkg_name='{PKG}'\\\"")
        restart(dev)


def module_state(dev: Device, framework: str) -> str:
    if framework == "vector":
        return su(dev, f"{VECTOR_CLI} modules ls").strip()
    return su(dev, f"sqlite3 {LSPOSED_DB} \\\"SELECT module_pkg_name, enabled FROM modules; SELECT app_pkg_name FROM scope\\\"").strip()


def restart(dev: Device) -> None:
    # A graceful guest reboot after sync. Killing the emulator right after an install can lose the package database entry.
    su(dev, "sync")
    dev.adb("reboot", check=False)
    time.sleep(15)
    deadline = time.time() + 900
    while time.time() < deadline:
        result = subprocess.run(["adb", "-s", dev.serial, "shell", "getprop", "sys.boot_completed"], capture_output=True, text=True)
        if result.stdout.strip() == "1":
            break
        time.sleep(5)
    dev.sh("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard", check=False)
    time.sleep(20)


def wait_module_row(dev: Device, timeout: int = 60) -> None:
    """LSPosed registers a module asynchronously after its package is installed or replaced."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if PKG in su(dev, f"sqlite3 {LSPOSED_DB} \\\"SELECT module_pkg_name FROM modules\\\""):
            return
        time.sleep(3)


def launch(dev: Device, activity: str, out_dir: Path, name: str) -> float:
    dev.force_stop(PROBE_PKG)
    dev.start(f"{PROBE_PKG}/.{activity}")
    time.sleep(3)
    return dev.luminance(dev.screenshot(out_dir / f"{name}.png"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--framework", choices=("vector", "lsposed"), required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--probe-apk", type=Path, default=ROOT / "testing/forcedark-probe/build/outputs/apk/debug/forcedarkprobe-debug.apk")
    parser.add_argument("--fresh", action="store_true", help="Uninstall DarQ first (use for the baseline run)")
    args = parser.parse_args()

    out_dir = ROOT / "build/e2e" / datetime.now().strftime(f"%Y%m%d-%H%M%S-xposed-{args.framework}-{args.label}")
    out_dir.mkdir(parents=True, exist_ok=True)
    report = Report(out_dir)
    dev = Device(args.serial)
    since = dev.device_time()
    sdk = int(dev.sh("getprop ro.build.version.sdk").strip())

    if args.fresh:
        dev.uninstall(PKG)
    dev.install(args.probe_apk)
    dev.install(args.apk)
    grant_root(dev, PKG)
    dev.sh("cmd uimode night yes", check=False)
    enable_module(dev, args.framework, [PKG, PROBE_PKG])
    report.add("X0", "Module enabled with DarQ and the probe in scope", PKG in module_state(dev, args.framework),
               module_state(dev, args.framework).replace("\n", " / ")[:200])

    try:
        # X1: the legacy self-hook reports the module as active, so DarQ shows its Xposed entry.
        bootstrap(dev.serial)
        open_main(dev)
        time.sleep(2)
        report.add("X1", "DarQ sees the Xposed module as active", bool(dev.find(text="Xposed Module")), "Xposed Module entry visible")

        checked = set_probe_selected(dev, True)

        # X2: the SystemProperties hook turns force dark on for a selected app.
        luminance = launch(dev, "ProbeActivity", out_dir, "x2_probe")
        report.add("X2", "Selected app goes dark through the SystemProperties hook", checked and luminance < DARK_MAX,
                   f"switch={checked} luminance={luminance:.0f}")

        # X3: Aggressive Force Dark overrides an app that opts out in its theme (int path on Android 15+, boolean below).
        luminance = launch(dev, "OptOutProbeActivity", out_dir, "x3_optout")
        path = "int ForceDarkType" if sdk >= 35 else "boolean"
        report.add("X3", f"Aggressive dark darkens an opted-out app ({path} path)", luminance < DARK_MAX, f"SDK {sdk}, luminance={luminance:.0f}")

        # X4: a force dark value left by Shizuku or root mode must be cleared once Xposed mode is active.
        # The bracket keeps pkill from matching (and killing) the su shell whose own command line contains the pattern.
        su(dev, f"setprop {FORCE_DARK_PROP} true; pkill -f {PKG}:roo[t]")
        dev.force_stop(PKG)
        open_main(dev)
        time.sleep(5)
        value = dev.sh(f"getprop {FORCE_DARK_PROP}").strip()
        report.add("X4", "Stale global force dark cleared in Xposed mode", value == "false", f"{FORCE_DARK_PROP}={value or 'unset'}")

        # X5: with the preferences redirected by LSPosed, the snapshot sits in DarQ's own files and restores everything when the redirect goes away.
        snapshot = su(dev, f"cat /data/data/{PKG}/files/settings_snapshot.json")
        if '"revision"' not in snapshot:
            report.add("X5", "Snapshot survives the LSPosed redirect going away", None, "No snapshot in this build (expected for the baseline)")
        else:
            redirected = su(dev, f"find /data/misc -path '*{PKG}*' -name '*_prefs.xml' 2>/dev/null").strip()
            disable_module(dev, args.framework)
            dev.force_stop(PKG)
            open_main(dev)
            time.sleep(4)
            local = su(dev, f"cat /data/data/{PKG}/shared_prefs/{PKG}_prefs.xml")
            restored = PROBE_PKG in local
            report.add("X5", "Snapshot survives the LSPosed redirect going away", restored,
                       f"redirected file: {redirected.splitlines()[0] if redirected else 'none'}, probe still selected after redirect removed: {restored}")
            enable_module(dev, args.framework, [PKG, PROBE_PKG])

        # X6: no hook failures logged by DarQ inside hooked processes.
        failures = [line for line in dev.sh(f"logcat -d -s DarQXposed -T '{since}'", check=False).splitlines() if "Failed" in line]
        report.add("X6", "No hook failures logged", not failures, "; ".join(failures)[:300] or "DarQXposed log clean")
        report.add("F1", "Service connected without timeouts", not SERVICE_TIMEOUTS, "; ".join(SERVICE_TIMEOUTS) or "No service timeouts")
        crashes = dev.crashes_since(since, (PKG, PROBE_PKG))
        report.add("X7", "No crashes", not crashes, "; ".join(crashes) or "Crash buffer clean")
    except (Exception, SystemExit) as error:
        report.add("ERROR", "Unexpected failure", False, repr(error))

    path = report.write(f"DarQ Xposed suite, {args.framework} on {args.serial}, {args.label}")
    print(f"Report: {path}")
    return 1 if report.failed else 0


if __name__ == "__main__":
    sys.exit(main())
