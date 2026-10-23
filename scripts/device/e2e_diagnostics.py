"""Checks DarQ's crash report flow on an emulator with a debug build.

The debug build has a test receiver (guarded by the DUMP permission) that crashes DarQ with a chosen message. The suite crashes it with a message naming the probe app, then checks the next launch offers the report once, the preview hashes the probe package, and the GitHub option opens the prefilled bug form.

Usage:
    uv run --with pillow python scripts/device/e2e_diagnostics.py --serial emulator-5562
"""

from __future__ import annotations

import argparse
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adbx import UI_DUMP_PATH, Device  # noqa: E402
from e2e_regression import ACTIVITY, MAIN_LIST_TEXT, PKG, Report, open_main  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
PROBE_PKG = "com.darq.forcedarkprobe"
CRASH_ACTION = "com.mohitarora.darqreborn.debug.TEST_CRASH"
CRASH_TITLE = "DarQ closed unexpectedly"
BUG_URL = "https://github.com/Arora-Sir/DarQ-Reborn/issues/new?template=bug_report.yml"


def pid_of(dev: Device, package: str) -> str:
    return dev.sh(f"pidof {package}", check=False).strip()


def wait_for_any(dev: Device, texts: tuple[str, ...], timeout: float = 60) -> str | None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        seen = [node.text for node in dev.dump_ui()]
        for text in texts:
            if text in seen:
                return text
        time.sleep(2)
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--probe-apk", type=Path, default=ROOT / "testing/forcedark-probe/build/outputs/apk/debug/forcedarkprobe-debug.apk")
    args = parser.parse_args()

    out_dir = ROOT / ".e2e" / datetime.now().strftime("%Y%m%d-%H%M%S-diagnostics")
    out_dir.mkdir(parents=True, exist_ok=True)
    report = Report(out_dir)
    dev = Device(args.serial)
    if not dev.serial.startswith("emulator-"):
        print("Refused: the diagnostics suite crashes DarQ on purpose and runs on emulators only")
        return 2

    dev.install(args.apk)
    dev.install(args.probe_apk)
    open_main(dev)
    start = dev.device_time()

    # D1: the debug receiver crashes DarQ with a message that names a user-installed app
    dev.sh(f"am broadcast -a {CRASH_ACTION} -p {PKG} --es message 'diagnostics test crash while darkening {PROBE_PKG}'", check=False)
    time.sleep(4)
    crashes = dev.crashes_since(start, (PKG,))
    report.add("D1", "Test crash takes DarQ down", bool(crashes) or not pid_of(dev, PKG), f"crash lines={len(crashes)}")
    # NOTE: A second crash within a short time makes Android show its own "keeps stopping" dialog, which would cover DarQ's prompt.
    if any(node.text == "Close app" for node in dev.dump_ui()):
        dev.tap_text("Close app")
        time.sleep(2)

    # D2: the next launch offers the saved report
    dev.start(ACTIVITY)
    seen = wait_for_any(dev, (CRASH_TITLE, MAIN_LIST_TEXT))
    if seen == MAIN_LIST_TEXT:
        seen = wait_for_any(dev, (CRASH_TITLE,), timeout=10)
    report.add("D2", "Next launch offers the crash report", seen == CRASH_TITLE, f"saw {seen!r}")
    dev.screenshot(out_dir / "d2-crash-dialog.png")

    # D3: the preview is redacted, the probe package appears only as a hash
    preview = ""
    if seen == CRASH_TITLE:
        dev.tap_text("Review report")
        dev.wait_for_text("Summary", timeout=60)
        preview = "\n".join(node.text for node in dev.dump_ui())
        dev.screenshot(out_dir / "d3-preview.png")
        # NOTE: A crash trace makes the Last crash card tall, so the sheet is scrolled until the collapsed log toggle shows.
        for _ in range(5):
            if "Show full log" in [node.text for node in dev.dump_ui()]:
                break
            dev.sh("input swipe 540 1500 540 800 400", check=False)
            time.sleep(1)
        preview += "\n" + "\n".join(node.text for node in dev.dump_ui())
    # NOTE: The preview is structured, so the header facts sit in the Summary card and the log stays collapsed behind Show full log.
    has_header = "Mode:" in preview and "Android:" in preview and "Show full log" in preview
    has_crash = "diagnostics test crash while darkening" in preview
    leaked = PROBE_PKG in preview
    report.add("D3", "Preview shows the crash with the app name hashed", has_header and has_crash and not leaked and "app#" in preview,
               f"header={has_header} crash={has_crash} leaked={leaked}")

    # D4: the GitHub option opens the prefilled bug form in a browser
    opened = False
    if preview:
        dev.tap_text("Continue to GitHub")
        time.sleep(4)
        log = dev.sh("logcat -d -s ActivityTaskManager:I", check=False)
        # NOTE: Android 12 and later hide the URL path in this log line, so the host and the VIEW action are the reliable parts.
        opened = BUG_URL in log or ("act=android.intent.action.VIEW dat=https://github.com" in log)
        dev.screenshot(out_dir / "d4-browser.png")
    report.add("D4", "GitHub option opens the prefilled bug form", opened, "START intent with the bug form URL" if opened else "no START line found")

    # D5: the crash is offered only once
    dev.force_stop(PKG)
    dev.start(ACTIVITY)
    again = wait_for_any(dev, (CRASH_TITLE, MAIN_LIST_TEXT))
    time.sleep(3)
    again = CRASH_TITLE if CRASH_TITLE in [node.text for node in dev.dump_ui()] else again
    report.add("D5", "A crash is offered only once", again == MAIN_LIST_TEXT, f"saw {again!r}")

    # D6: Report an issue offers a bug report with the log ticked by default, and continuing shows the preview
    for _ in range(4):
        dev.sh("input swipe 540 1800 540 600 300", check=False)
        time.sleep(1)
    detail = "entry missing"
    passed = False
    if any(node.text == "Report an issue" for node in dev.dump_ui()):
        dev.tap_text("Report an issue")
        dev.wait_for_text("Include diagnostic log", timeout=20)
        ticked = 'checked="true"' in dev.sh(f"cat {UI_DUMP_PATH}", check=False).split('text="Include diagnostic log"', 1)[-1].split("/>", 1)[0]
        # NOTE: Typing into the first field proves the dialog resizes for the keyboard and keeps its buttons reachable.
        dev.tap_text("What happened?")
        dev.sh("input text 'screen%sdoes%snot%sload'", check=False)
        dev.back()
        dev.tap_text("Continue")
        preview_shown = wait_for_any(dev, ("Summary",), timeout=60) is not None
        passed = ticked and preview_shown
        detail = f"log ticked={ticked}, preview={preview_shown}"
        dev.back()
    report.add("D6", "Report an issue includes the log by default", passed, detail)

    path = report.write(f"DarQ diagnostics suite on {dev.serial}")
    print(f"Report: {path}")
    return 1 if report.failed else 0


if __name__ == "__main__":
    sys.exit(main())
