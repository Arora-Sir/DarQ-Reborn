"""Disposable emulator fleet for DarQ device testing.

Creates the test AVDs, boots them headless with hardware acceleration, starts Shizuku over adb,
and walks DarQ's first-run prompts so the suites can run unattended. The user's own AVDs are
never touched: every AVD here has a DarQ_ prefix, and rooted AVDs point at a copied image.

Usage:
    uv run --with pillow python scripts/device/emulator.py create DarQ_A10 --package "system-images;android-29;google_apis;x86_64"
    uv run --with pillow python scripts/device/emulator.py up DarQ_A10 --port 5560
    uv run --with pillow python scripts/device/emulator.py shizuku emulator-5560
    uv run --with pillow python scripts/device/emulator.py bootstrap emulator-5560
    uv run --with pillow python scripts/device/emulator.py down emulator-5560
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adbx import Device  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", Path.home() / "AppData/Local/Android/Sdk"))
AVD_HOME = Path.home() / ".android/avd"
JAVA_HOME = os.environ.get("JAVA_HOME", r"C:\Program Files\Eclipse Adoptium\jdk-17.0.3.7-hotspot")
SHIZUKU_APK = Path(r"D:\Study\Learnings\Projects\Android\OpenSource-Others\Shizuku-Fork\manager\build\outputs\apk\debug\shizuku-v13.7.0-thedjchi-debug.apk")
SHIZUKU_PKG = "moe.shizuku.privileged.api"
DARQ_PKG = "com.mohitarora.darqreborn"
DARQ_ACTIVITY = f"{DARQ_PKG}/com.kieronquinn.app.darq.ui.activities.DarqActivity"
MAIN_LIST_TEXT = "DarQ Per-App Force Dark"
PROMPT_BUTTONS = ("Allow all the time", "Allow", "ALLOW", "Grant", "GRANT")


def create(name: str, package: str, sysdir_override: str | None) -> None:
    if not name.startswith("DarQ_"):
        raise SystemExit("Test AVDs must use the DarQ_ prefix so user AVDs are never touched")
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    # The .bat wrapper runs through cmd, which splits unquoted semicolons, so the package id is quoted explicitly.
    avdmanager = SDK / "cmdline-tools/latest/bin/avdmanager.bat"
    subprocess.run(
        f'"{avdmanager}" create avd --force -n {name} -k "{package}" -d pixel_6',
        input=b"no\n", env=env, check=True, shell=True,
    )
    config = AVD_HOME / f"{name}.avd" / "config.ini"
    lines = config.read_text(encoding="utf-8").splitlines()
    settings = {
        "hw.keyboard": "yes",
        "hw.ramSize": "3072",
        "disk.dataPartition.size": "6G",
    }
    if sysdir_override:
        # Rooted AVDs boot from a copied image so rootAVD never patches the shared SDK image.
        settings["image.sysdir.1"] = sysdir_override
    kept = [line for line in lines if line.split("=", 1)[0] not in settings]
    kept += [f"{key}={value}" for key, value in settings.items()]
    config.write_text("\n".join(kept) + "\n", encoding="utf-8")
    print(f"Created {name} from {package}" + (f" (sysdir {sysdir_override})" if sysdir_override else ""))


def up(name: str, port: int, window: bool) -> str:
    serial = f"emulator-{port}"
    args = [str(SDK / "emulator/emulator.exe"), "-avd", name, "-port", str(port), "-no-snapshot", "-no-audio", "-no-boot-anim"]
    if not window:
        args += ["-no-window", "-gpu", "swiftshader_indirect"]
    log_path = ROOT / "build" / "e2e" / f"{name}-emulator.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    # DETACHED_PROCESS silently kills the emulator on Windows, so it runs in its own process group with a log file instead.
    flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) | getattr(subprocess, "CREATE_NO_WINDOW", 0)
    subprocess.Popen(args, stdout=open(log_path, "w"), stderr=subprocess.STDOUT, creationflags=flags)
    # A first boot with software rendering can take well over ten minutes.
    deadline = time.time() + 1800
    while time.time() < deadline:
        result = subprocess.run(["adb", "-s", serial, "shell", "getprop", "sys.boot_completed"], capture_output=True, text=True)
        if result.stdout.strip() == "1":
            break
        time.sleep(5)
    else:
        raise SystemExit(f"{name} did not finish booting")
    dev = Device(serial)
    dev.sh("input keyevent KEYCODE_WAKEUP", check=False)
    dev.sh("wm dismiss-keyguard", check=False)
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        dev.sh(f"settings put global {key} 0", check=False)
    print(serial)
    return serial


def shizuku(serial: str, apk: Path) -> None:
    dev = Device(serial)
    dev.install(apk)
    # Shizuku ships its starter as a native library, so adb can start the server without opening the app.
    apk_dir = dev.sh(f"pm path {SHIZUKU_PKG}").strip().splitlines()[0].replace("package:", "").rsplit("/", 1)[0]
    abi = dev.sh("getprop ro.product.cpu.abi").strip()
    lib_abi = {"x86_64": "x86_64", "arm64-v8a": "arm64"}.get(abi, abi)
    output = dev.sh(f"{apk_dir}/lib/{lib_abi}/libshizuku.so", check=False, timeout=60)
    time.sleep(3)
    running = "shizuku" in dev.sh("ps -A", check=False)
    print(output.strip().splitlines()[-1] if output.strip() else "starter produced no output")
    if not running:
        raise SystemExit("Shizuku server did not start")
    print("Shizuku running")


def bootstrap(serial: str, timeout: float = 180) -> None:
    """Launches DarQ and accepts first-run permission prompts until the settings list shows."""
    dev = Device(serial)
    dev.force_stop(DARQ_PKG)
    dev.start(DARQ_ACTIVITY)
    deadline = time.time() + timeout
    while time.time() < deadline:
        nodes = dev.dump_ui()
        if any(node.text == MAIN_LIST_TEXT for node in nodes):
            print("DarQ ready")
            return
        if any(node.text == "Service Timeout" for node in nodes):
            # Root mode can miss its first service connection after a reinstall. A relaunch reconnects.
            print("Service timeout, relaunching DarQ")
            dev.force_stop(DARQ_PKG)
            dev.start(DARQ_ACTIVITY)
            continue
        prompt = next((node for node in nodes if node.text in PROMPT_BUTTONS), None)
        if prompt is not None:
            print(f"Accepting prompt '{prompt.text}'")
            dev.tap(*prompt.center)
        time.sleep(2)
    raise SystemExit("DarQ did not reach its settings list")


def night(serial: str, value: str) -> None:
    """Sets system dark mode. Images that lock night mode get the stored setting and a reboot (emulators only)."""
    dev = Device(serial)
    dev.sh(f"cmd uimode night {value}", check=False)
    if dev.sh("cmd uimode night").strip().endswith(value):
        print(f"Night mode: {value}")
        return
    if not serial.startswith("emulator-"):
        raise SystemExit("Night mode is locked on a physical device, refusing to reboot it")
    stored = {"yes": 2, "no": 1, "auto": 0}[value]
    dev.sh(f"settings put secure ui_night_mode {stored}")
    dev.adb("reboot", check=False)
    deadline = time.time() + 900
    while time.time() < deadline:
        result = subprocess.run(["adb", "-s", serial, "shell", "getprop", "sys.boot_completed"], capture_output=True, text=True)
        if result.stdout.strip() == "1":
            break
        time.sleep(5)
    dev.sh("input keyevent KEYCODE_WAKEUP", check=False)
    dev.sh("wm dismiss-keyguard", check=False)
    print(f"Night mode set to {value} through the stored setting and a reboot. Shizuku must be started again.")


def down(serial: str) -> None:
    subprocess.run(["adb", "-s", serial, "emu", "kill"], capture_output=True)
    print(f"Stopped {serial}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    c = sub.add_parser("create")
    c.add_argument("name")
    c.add_argument("--package", required=True)
    c.add_argument("--sysdir", help="Relative image dir to boot instead of the package image, for rooted copies")
    u = sub.add_parser("up")
    u.add_argument("name")
    u.add_argument("--port", type=int, default=5560)
    u.add_argument("--window", action="store_true")
    s = sub.add_parser("shizuku")
    s.add_argument("serial")
    s.add_argument("--apk", type=Path, default=SHIZUKU_APK)
    b = sub.add_parser("bootstrap")
    b.add_argument("serial")
    n = sub.add_parser("night")
    n.add_argument("serial")
    n.add_argument("value", choices=("yes", "no", "auto"))
    d = sub.add_parser("down")
    d.add_argument("serial")
    args = parser.parse_args()
    if args.command == "create":
        create(args.name, args.package, args.sysdir)
    elif args.command == "up":
        up(args.name, args.port, args.window)
    elif args.command == "shizuku":
        shizuku(args.serial, args.apk)
    elif args.command == "bootstrap":
        bootstrap(args.serial)
    elif args.command == "night":
        night(args.serial, args.value)
    elif args.command == "down":
        down(args.serial)


if __name__ == "__main__":
    main()
