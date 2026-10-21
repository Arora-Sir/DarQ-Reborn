"""Runs the DarQ device matrix: physical devices plus the shared emulator fleet, one emulator at a time.

Normal emulators get Shizuku, the regression suite and the diagnostics suite. Rooted emulators get the Xposed suite with the
module enabled, then the regression suite in plain root mode with the module disabled. Physical devices
run the regression suite with a five minute idle requirement and are skipped (not forced) when busy.

Usage:
    uv run --with pillow python scripts/device/run_matrix.py
    uv run --with pillow python scripts/device/run_matrix.py --targets a10 a16root --skip-physical
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
FLEET = Path.home() / ".gemini/skills/Personal-Skills/Custom-Skills/android-mastery/scripts/emulators/fleet.py"
DEBUG_APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
PHYSICAL = {"phone": "100.127.59.103:5555", "tablet": "100.124.234.69:5555"}
NORMAL = {"a10": "emulator-5560", "a13": "emulator-5562", "a16": "emulator-5564", "a17": "emulator-5566"}
ROOTED = {"a11root": ("emulator-5570", "lsposed"), "a16root": ("emulator-5572", "vector")}


def run(*args: str) -> str:
    result = subprocess.run([sys.executable, "-I", *args], capture_output=True, text=True, cwd=ROOT)
    return result.stdout + result.stderr


def results(output: str) -> list[str]:
    return [line for line in output.splitlines() if re.match(r"\[(PASS|FAIL|SKIP)\]", line) or line.startswith("Report:")]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--targets", nargs="*", default=list(NORMAL) + list(ROOTED))
    parser.add_argument("--skip-physical", action="store_true")
    args = parser.parse_args()

    summary_path = ROOT / ".e2e" / datetime.now().strftime("%Y%m%d-%H%M%S-matrix.md")
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"# DarQ device matrix {datetime.now():%Y-%m-%d %H:%M}", ""]

    physical = []
    if not args.skip_physical:
        for name, serial in PHYSICAL.items():
            physical.append((name, subprocess.Popen(
                [sys.executable, "-I", str(HERE / "e2e_regression.py"), "--serial", serial, "--min-idle", "300"],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, cwd=ROOT)))

    for key in args.targets:
        print(f"===== {key}", flush=True)
        lines.append(f"## {key}")
        if key in NORMAL:
            serial = NORMAL[key]
            run(str(FLEET), "up", key)
            run(str(FLEET), "night", key, "yes")
            run(str(FLEET), "shizuku", key)
            subprocess.run(["adb", "-s", serial, "install", "-r", str(DEBUG_APK)], capture_output=True)
            run(str(HERE / "emulator.py"), "bootstrap", serial)
            output = run(str(HERE / "e2e_regression.py"), "--serial", serial, "--min-idle", "0")
            output += run(str(HERE / "e2e_diagnostics.py"), "--serial", serial)
        else:
            serial, framework = ROOTED[key]
            run(str(FLEET), "up", key)
            output = run(str(HERE / "e2e_xposed.py"), "--serial", serial, "--framework", framework, "--apk", str(DEBUG_APK), "--label", "candidate")
            output += run("-c", f"import sys; sys.path.insert(0, r'{HERE}'); from adbx import Device; import e2e_xposed as x; "
                                f"x.disable_module(Device('{serial}'), '{framework}')")
            run(str(HERE / "emulator.py"), "bootstrap", serial)
            output += run(str(HERE / "e2e_regression.py"), "--serial", serial, "--min-idle", "0")
        run(str(FLEET), "down", key)
        time.sleep(15)
        found = results(output)
        print("\n".join(found), flush=True)
        lines += found + [""]

    for name, process in physical:
        output = process.communicate()[0]
        print(f"===== {name}", flush=True)
        found = results(output) or [line for line in output.splitlines() if "refused" in line.lower()]
        print("\n".join(found), flush=True)
        lines += [f"## {name}"] + found + [""]

    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Summary: {summary_path}")
    return 1 if any(line.startswith("[FAIL]") for line in lines) else 0


if __name__ == "__main__":
    sys.exit(main())
