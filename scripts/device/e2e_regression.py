"""DarQ regression suite for a non-root phone running DarQ in Shizuku mode.

Installs the debug build over the existing install (same signing key, so data is kept), runs the
checks, then restores the user's preferences, night mode and installed apps whatever the outcome.

Usage:
    uv run --with pillow python scripts/device/e2e_regression.py --serial 100.127.59.103:5555
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adbx import Device, PreflightError  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
PKG = "com.mohitarora.darqreborn"
ACTIVITY = f"{PKG}/com.kieronquinn.app.darq.ui.activities.DarqActivity"
PROBE_PKG = "com.darq.forcedarkprobe"
PROBE_ACTIVITY = f"{PROBE_PKG}/.ProbeActivity"
PREFS = f"shared_prefs/{PKG}_prefs.xml"
SNAPSHOT = "files/settings_snapshot.json"

MAIN_LIST_TEXT = "DarQ Per-App Force Dark"
PROBE_LABEL = "DarQ Probe"
DARK_MAX = 90
LIGHT_MIN = 160


class Report:

    def __init__(self, out_dir: Path):
        self.out_dir = out_dir
        self.rows: list[tuple[str, str, str, str]] = []

    def add(self, check_id: str, name: str, passed: bool | None, detail: str) -> None:
        status = "PASS" if passed else ("SKIP" if passed is None else "FAIL")
        self.rows.append((check_id, name, status, detail))
        print(f"[{status}] {check_id} {name}: {detail}", flush=True)

    @property
    def failed(self) -> bool:
        return any(row[2] == "FAIL" for row in self.rows)

    def write(self, header: str) -> Path:
        lines = [f"# {header}", "", "| ID | Check | Result | Detail |", "| --- | --- | --- | --- |"]
        lines += [f"| {cid} | {name} | {status} | {detail.replace('|', '/')} |" for cid, name, status, detail in self.rows]
        path = self.out_dir / "report.md"
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path


def parse_prefs(xml_text: str) -> dict[str, object]:
    values: dict[str, object] = {}
    if "<map" not in xml_text:
        return values
    for element in ET.fromstring(xml_text[xml_text.index("<map"):].encode()):
        name = element.get("name")
        if element.tag == "string":
            values[name] = element.text or ""
        elif element.tag == "boolean":
            values[name] = element.get("value") == "true"
        elif element.tag in ("int", "long"):
            values[name] = int(element.get("value"))
        else:
            values[name] = element.get("value")
    return values


def expected_probe_dark(prefs: dict[str, object], selected: bool, night: bool) -> bool:
    enabled = prefs.get("enabled", True)
    always = prefs.get("always_force_dark", False)
    schedule_blocking = prefs.get("auto_dark_schedule_mode", 0) != 0 and not prefs.get("auto_dark_managed_enabled", True)
    return bool(night and enabled and (selected or always) and not schedule_blocking)


def darq_alarm_count(dev: Device) -> int:
    return sum(1 for line in dev.sh("dumpsys alarm", check=False).splitlines() if "Alarm{" in line and PKG in line)


def with_int_pref(xml_text: str, name: str, value: int) -> str:
    entry = f'<int name="{name}" value="{value}" />'
    pattern = re.compile(rf'<int name="{re.escape(name)}" value="-?\d+"\s*/>')
    if pattern.search(xml_text):
        return pattern.sub(entry, xml_text)
    return xml_text.replace("</map>", f"    {entry}\n</map>")


SERVICE_TIMEOUTS: list[str] = []


def open_main(dev: Device, attempts: int = 3) -> None:
    """Opens DarQ's settings list. A "Service Timeout" sheet is retried and recorded, so reports still show the flake."""
    for attempt in range(1, attempts + 1):
        dev.force_stop(PKG)
        dev.start(ACTIVITY)
        deadline = time.time() + 60
        while time.time() < deadline:
            texts = [node.text for node in dev.dump_ui()]
            if MAIN_LIST_TEXT in texts:
                return
            if "Service Timeout" in texts:
                SERVICE_TIMEOUTS.append(f"{dev.serial} attempt {attempt}")
                print(f"[WARN] DarQ service timeout on {dev.serial}, attempt {attempt}", flush=True)
                break
            time.sleep(2)
    raise TimeoutError(f"DarQ settings list did not appear after {attempts} attempts")


def set_probe_selected(dev: Device, selected: bool) -> bool:
    """Toggles the probe in DarQ's app picker through the UI and returns the switch state read back."""
    open_main(dev)
    dev.tap_text(MAIN_LIST_TEXT)
    search = None
    for _ in range(20):
        matches = dev.find(resource_id=":id/searchBox")
        if matches:
            search = matches[0]
            break
        time.sleep(1)
    if search is None:
        raise RuntimeError("App picker search box did not appear")
    dev.tap(*search.center)
    dev.type_text("DarQ")
    title = dev.wait_for_text(PROBE_LABEL, timeout=60)
    switch = row_switch(dev, title.bounds)
    if switch.checked != selected:
        dev.tap(*switch.center)
        time.sleep(1.5)
        switch = row_switch(dev, title.bounds)
    dev.back()
    return switch.checked


def row_switch(dev: Device, title_bounds: tuple[int, int, int, int]):
    title_center_y = (title_bounds[1] + title_bounds[3]) // 2
    for node in dev.find(resource_id=":id/app_switch"):
        if node.bounds[1] <= title_center_y <= node.bounds[3]:
            return node
    raise RuntimeError("Probe row switch not found")


def launch_probe(dev: Device, out_dir: Path, name: str) -> tuple[float, str, bool | None]:
    """Returns the panel luminance, the force dark property and whether the probe itself saw night mode."""
    since = dev.device_time()
    dev.force_stop(PROBE_PKG)
    dev.start(PROBE_ACTIVITY)
    time.sleep(3)
    luminance = dev.luminance(dev.screenshot(out_dir / f"{name}.png"))
    lines = dev.logcat_since(since, "DarqProbe")
    prop = re.search(r"forceDarkProp=(\S*)", lines[-1]).group(1) if lines else "no log"
    night = re.search(r"nightMode=(\d+)", lines[-1]) if lines else None
    # 32 is UI_MODE_NIGHT_YES. Some images lock night mode, so the probe's own configuration is the source of truth.
    return luminance, prop, (int(night.group(1)) == 32) if night else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default="100.127.59.103:5555")
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--probe-apk", type=Path, default=ROOT / "testing/forcedark-probe/build/outputs/apk/debug/forcedarkprobe-debug.apk")
    parser.add_argument("--final-apk", type=Path, default=ROOT / "app/build/outputs/apk/release/app-release.apk",
                        help="Installed at the end so the phone keeps a normal build. Pass an empty value to skip.")
    parser.add_argument("--min-idle", type=int, default=60)
    args = parser.parse_args()

    out_dir = ROOT / "build/e2e" / datetime.now().strftime("%Y%m%d-%H%M%S-regression")
    out_dir.mkdir(parents=True, exist_ok=True)
    report = Report(out_dir)
    dev = Device(args.serial)

    try:
        dev.preflight(min_idle_seconds=args.min_idle)
    except PreflightError as error:
        print(f"Preflight refused: {error}")
        return 2

    start = dev.device_time()
    dev.install(args.apk)
    dev.install(args.probe_apk)

    original_prefs_xml = dev.run_as(PKG, f"cat {PREFS}", check=False)
    (out_dir / "original_prefs.xml").write_text(original_prefs_xml, encoding="utf-8")
    original_prefs = parse_prefs(original_prefs_xml)
    night_line = dev.sh("cmd uimode night").strip()
    original_night = night_line.split(":")[-1].strip()
    night_changed = False
    alarm_baseline = darq_alarm_count(dev)

    try:
        # S1: the app starts, the privileged service connects and the settings list renders.
        try:
            open_main(dev)
            dev.screenshot(out_dir / "s1_main.png")
            report.add("S1", "Launch and Shizuku service connect", True, "Settings list rendered")
        except Exception as error:
            report.add("S1", "Launch and Shizuku service connect", False, str(error))
            raise

        # S2: the first 3.1.11 launch writes a snapshot that matches the preferences.
        time.sleep(2)
        snapshot = json.loads(dev.run_as(PKG, f"cat {SNAPSHOT}"))
        prefs = parse_prefs(dev.run_as(PKG, f"cat {PREFS}"))
        prefs_apps = sorted(json.loads(prefs.get("enabled_apps", "[]")))
        snapshot_apps = sorted(snapshot["settings"]["enabledApps"])
        ok = snapshot["revision"] == prefs.get("settings_revision") and prefs_apps == snapshot_apps \
            and snapshot["settings"]["enabled"] == prefs.get("enabled", True)
        report.add("S2", "Snapshot written and matches preferences", ok,
                   f"revision {snapshot['revision']} vs prefs {prefs.get('settings_revision')}, {len(snapshot_apps)} apps")
        baseline_settings = snapshot["settings"]

        # S3: losing the preferences file restores every setting from the snapshot.
        dev.force_stop(PKG)
        dev.run_as(PKG, f"rm {PREFS}")
        open_main(dev)
        time.sleep(3)
        restored = json.loads(dev.run_as(PKG, f"cat {SNAPSHOT}"))["settings"]
        differences = sorted(key for key in baseline_settings if baseline_settings[key] != restored.get(key))
        report.add("S3", "Snapshot restore after preferences loss", not differences,
                   "All settings restored" if not differences else f"Differs: {', '.join(differences)}")

        # S4 and S5: Shizuku force dark reaches a selected app and leaves it when deselected.
        if original_night != "yes":
            dev.sh("cmd uimode night yes")
            night_changed = True
            time.sleep(2)
        effective_prefs = parse_prefs(dev.run_as(PKG, f"cat {PREFS}"))

        checked = set_probe_selected(dev, True)
        luminance, prop, night = launch_probe(dev, out_dir, "s4_probe_selected")
        if luminance > LIGHT_MIN:
            # Shizuku mode sets the global property on the foreground change, which can trail the first window creation.
            luminance, prop, night = launch_probe(dev, out_dir, "s4_probe_selected_relaunch")
        if not night:
            report.add("S4", "Force dark applies to a selected app", None,
                       f"System dark mode not in effect for apps (probe night={night}), run 'emulator.py night' first")
        else:
            expect_dark = expected_probe_dark(effective_prefs, selected=True, night=True)
            ok = checked and ((luminance < DARK_MAX) if expect_dark else (luminance > LIGHT_MIN))
            report.add("S4", "Force dark applies to a selected app", ok,
                       f"switch={checked} luminance={luminance:.0f} prop={prop} expected {'dark' if expect_dark else 'light'}")

        checked = set_probe_selected(dev, False)
        luminance, prop, night = launch_probe(dev, out_dir, "s5_probe_deselected")
        expect_dark = expected_probe_dark(effective_prefs, selected=False, night=bool(night))
        ok = (not checked) and ((luminance < DARK_MAX) if expect_dark else (luminance > LIGHT_MIN))
        report.add("S5", "Force dark leaves a deselected app", ok,
                   f"switch={checked} luminance={luminance:.0f} prop={prop} night={night} expected {'dark' if expect_dark else 'light'}")

        # S6: the main settings screens still open.
        try:
            open_main(dev)
            for title in ("Advanced Options", "Auto Dark Theme"):
                dev.tap_text(title)
                time.sleep(2)
                dev.screenshot(out_dir / f"s6_{title.lower().replace(' ', '_')}.png")
                dev.back()
                dev.wait_for_text(MAIN_LIST_TEXT, timeout=15)
            report.add("S6", "Advanced and Auto Dark screens open", True, "Both screens opened and returned")
        except Exception as error:
            report.add("S6", "Advanced and Auto Dark screens open", False, str(error))

        # S7: without LSPosed the Xposed entry stays hidden.
        open_main(dev)
        report.add("S7", "No Xposed entry without LSPosed", not dev.find(text="Xposed Module"), "Entry hidden")

        # S9: an in-place update must re-arm Auto Dark without the user opening DarQ.
        # A DarQ-only schedule (target mode 1) is used, which never changes the system night mode.
        try:
            dev.force_stop(PKG)
            current = dev.run_as(PKG, f"cat {PREFS}")
            scheduled = with_int_pref(with_int_pref(current, "auto_dark_schedule_mode", 2), "auto_dark_target_mode", 1)
            dev.write_app_file(PKG, PREFS, scheduled.encode())
            # Android skips MY_PACKAGE_REPLACED for a force-stopped app, so DarQ is opened once first, like a real user's app before an update.
            dev.sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1", check=False)
            deadline = time.time() + 45
            while time.time() < deadline and "stopped=true" in dev.sh(f"dumpsys package {PKG} | grep stopped="):
                time.sleep(2)
            time.sleep(4)
            dev.sh("input keyevent KEYCODE_HOME")
            since = dev.device_time()
            dev.install(args.apk)
            deadline = time.time() + 45
            alarms, receiver = 0, []
            while time.time() < deadline and (alarms == 0 or not receiver):
                time.sleep(3)
                alarms = darq_alarm_count(dev)
                receiver = [line for line in dev.logcat_since(since, "BootReceiver") if "MY_PACKAGE_REPLACED" in line]
            report.add("S9", "Update in place re-arms Auto Dark", alarms > 0 and bool(receiver),
                       f"{alarms} DarQ alarm(s) after install, receiver log: {receiver[-1].split(': ', 1)[-1] if receiver else 'none'}")
        except Exception as error:
            report.add("S9", "Update in place re-arms Auto Dark", False, str(error))

    except PreflightError as error:
        report.add("ABORT", "Preflight during test", False, str(error))
    except Exception as error:
        report.add("ERROR", "Unexpected failure", False, repr(error))
    finally:
        # Restore the user's state whatever happened above.
        dev.force_stop(PKG)
        dev.force_stop(PROBE_PKG)
        if original_prefs_xml.strip():
            dev.write_app_file(PKG, PREFS, original_prefs_xml.encode())
        # The restored file has an older revision than the test snapshot, so the snapshot is removed and rebuilt from it on next launch.
        dev.run_as(PKG, f"rm -f {SNAPSHOT}", check=False)
        restored_xml = dev.run_as(PKG, f"cat {PREFS}", check=False)
        if night_changed:
            dev.sh(f"cmd uimode night {original_night}")
        dev.uninstall(PROBE_PKG)
        crashes = dev.crashes_since(start, (PKG, PROBE_PKG))
        report.add("S8", "No crashes during the run", not crashes, "; ".join(crashes) or "Crash buffer clean")
        report.add("F1", "Service connected without timeouts", not SERVICE_TIMEOUTS, "; ".join(SERVICE_TIMEOUTS) or "No service timeouts")
        report.add("R1", "User preferences restored", parse_prefs(restored_xml) == original_prefs, "Byte-level compare of parsed values")
        report.add("R2", "Night mode restored", dev.sh("cmd uimode night").strip() == night_line, night_line)
        if args.final_apk and Path(args.final_apk).exists():
            dev.install(Path(args.final_apk))
            report.add("R3", "Release build reinstalled", True, Path(args.final_apk).name)
        # Installs and force-stops cancel the app's alarms. Resuming the main screen reschedules Auto Dark and rebinds the service.
        try:
            open_main(dev)
            time.sleep(4)
            dev.sh("input keyevent KEYCODE_HOME")
            report.add("R4", "DarQ relaunched to reschedule Auto Dark", True, "Main screen resumed, then home")
            alarms_end = darq_alarm_count(dev)
            report.add("R5", "DarQ alarms back to the starting state", alarms_end == alarm_baseline,
                       f"{alarms_end} now, {alarm_baseline} before the run")
        except Exception as error:
            report.add("R4", "DarQ relaunched to reschedule Auto Dark", False, str(error))

    path = report.write(f"DarQ regression on {args.serial}")
    print(f"Report: {path}")
    return 1 if report.failed else 0


if __name__ == "__main__":
    sys.exit(main())
