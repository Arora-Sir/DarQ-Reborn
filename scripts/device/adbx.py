"""ADB helpers for DarQ device automation.

Covers the safety preflight, UI navigation by text from uiautomator dumps, screenshots with
luminance sampling, and reading app files through run-as. Rules follow the android-mastery
device testing playbook: never act on a device that is streaming, in a call, locked or in use.
"""

from __future__ import annotations

import io
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

STREAM_FOCUS_MARKERS = ("com.limelight.Game", "superartemis")
UI_DUMP_PATH = "/sdcard/window_dump.xml"


class PreflightError(RuntimeError):
    """The device is not safe to drive right now."""


@dataclass
class Node:
    text: str
    desc: str
    resource_id: str
    checked: bool
    bounds: tuple[int, int, int, int]

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2


class Device:

    def __init__(self, serial: str):
        self.serial = serial

    def adb(self, *args: str, binary: bool = False, check: bool = True, timeout: int = 120):
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, timeout=timeout)
        if check and result.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def sh(self, command: str, check: bool = True, timeout: int = 120) -> str:
        return self.adb("shell", command, check=check, timeout=timeout)

    # Preflight

    def focus(self) -> str:
        return self.sh("dumpsys window | grep mCurrentFocus").strip()

    @staticmethod
    def _parse_idle_age(dump: str) -> float | None:
        """Seconds since the latest user activity in a dumpsys power dump, from the age printed after the timestamp. Returns None when no line carries an age."""
        # NOTE: Android prints "(now)" for activity in the current millisecond and "(in N ms)" for a timestamp just ahead of the dump clock.
        found = re.findall(r"\bm?[Ll]astUserActivityTime=\d+ \((?:(\d+) ms ago|(now|in \d+ ms))\)", dump)
        return min((int(age or 0) / 1000 for age, _ in found), default=None)

    def idle_seconds(self) -> float:
        dump = self.sh("dumpsys power")
        age = self._parse_idle_age(dump)
        if age is not None:
            return age
        if not self.serial.startswith("emulator-"):
            raise PreflightError("Idle time is unknown because dumpsys power shows no age for lastUserActivityTime")
        stamps = [int(stamp) for stamp in re.findall(r"\bm?[Ll]astUserActivityTime=(\d+)", dump)]
        if not stamps:
            return float("inf")
        # NOTE: The timestamp excludes deep sleep while /proc/uptime includes it, so this subtraction is only trusted on emulators.
        uptime = float(self.sh("cat /proc/uptime").split()[0])
        return uptime - max(stamps) / 1000

    @staticmethod
    def _is_pid_alive(pid: int) -> bool:
        if pid <= 0:
            return False
        if sys.platform == "win32":
            import ctypes
            handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
            if not handle:
                return False
            try:
                exit_code = ctypes.c_ulong()
                if ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(exit_code)):
                    return exit_code.value == 259
                return False
            finally:
                ctypes.windll.kernel32.CloseHandle(handle)
        try:
            os.kill(pid, 0)
            return True
        except (OSError, ProcessLookupError):
            return False

    def is_locked(self) -> bool:
        """Keyguard showing, or the CURRENT user locked. Other users such as Samsung Secure Folder keep their own lock and always read locked."""
        if "isKeyguardShowing=true" in self.sh("dumpsys window | grep isKeyguardShowing", check=False):
            return True
        current = [line for line in self.sh("dumpsys trust", check=False).splitlines() if "(current)" in line]
        return any("deviceLocked=1" in line for line in current)

    def preflight(self, min_idle_seconds: int = 60) -> None:
        if self.serial.startswith("emulator-"):
            lease = self.sh("getprop debug.fleet.lease", check=False).strip()
            if lease and ":" in lease:
                proj, pid_str = lease.split(":", 1)
                try:
                    holder_pid = int(pid_str)
                    curr_pid = os.getpid()
                    parent_pid = os.getppid() if hasattr(os, "getppid") else 0
                    if holder_pid not in (curr_pid, parent_pid) and self._is_pid_alive(holder_pid):
                        raise PreflightError(f"Emulator {self.serial} is currently leased by project '{proj}' (PID {holder_pid})")
                except ValueError:
                    pass
        focus = self.focus()
        if any(marker in focus for marker in STREAM_FOCUS_MARKERS):
            raise PreflightError(f"Device is streaming, refusing input ({focus})")
        if "mCallState=0" not in self.sh("dumpsys telephony.registry | grep -m1 mCallState", check=False):
            raise PreflightError("Device is in a call")
        if self.is_locked():
            if self.serial.startswith("emulator-"):
                self.sh("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; sleep 1; input swipe 540 1900 540 600 200; sleep 1; input text 1234; input keyevent 66", check=False)
                time.sleep(2)
                if self.is_locked():
                    raise PreflightError("Emulator is locked and standard PIN 1234 failed")
            else:
                raise PreflightError("Device is locked")
        if "mWakefulness=Awake" not in self.sh("dumpsys power | grep mWakefulness=", check=False):
            raise PreflightError("Screen is off")
        idle = self.idle_seconds()
        if idle < min_idle_seconds:
            raise PreflightError(f"Device was used {idle:.0f}s ago, waiting for {min_idle_seconds}s of idle")

    def assert_not_streaming(self) -> None:
        if self.serial.startswith("emulator-"):
            lease = self.sh("getprop debug.fleet.lease", check=False).strip()
            if lease and ":" in lease:
                proj, pid_str = lease.split(":", 1)
                try:
                    holder_pid = int(pid_str)
                    curr_pid = os.getpid()
                    parent_pid = os.getppid() if hasattr(os, "getppid") else 0
                    if holder_pid not in (curr_pid, parent_pid) and self._is_pid_alive(holder_pid):
                        raise PreflightError(f"Emulator {self.serial} is currently leased by project '{proj}' (PID {holder_pid})")
                except ValueError:
                    pass
        focus = self.focus()
        if any(marker in focus for marker in STREAM_FOCUS_MARKERS):
            raise PreflightError(f"Stream window took focus mid-test ({focus})")

    # UI

    def dump_ui(self, retries: int = 16, max_dialogs: int = 30) -> list[Node]:
        """Dumps the screen. System dialogs are dismissed on their own budget, so a dialog that keeps returning never eats the dump retries."""
        dialogs = 0
        attempts = 0
        while attempts < retries:
            # A failed dump leaves the previous file behind, so it is deleted first to never read a stale screen.
            self.sh(f"rm -f {UI_DUMP_PATH}", check=False)
            self.sh(f"uiautomator dump {UI_DUMP_PATH}", check=False)
            raw = self.sh(f"cat {UI_DUMP_PATH}", check=False)
            if not raw.lstrip().startswith("<?xml"):
                attempts += 1
                time.sleep(1)
                continue
            nodes = self._parse_nodes(raw)
            if dialogs < max_dialogs and self._dismiss_system_dialog(nodes):
                dialogs += 1
                continue
            return nodes
        raise RuntimeError("uiautomator dump failed")

    def _dismiss_system_dialog(self, nodes: list[Node]) -> bool:
        """Taps away a crash or not-responding dialog. Returns True when one was handled and the screen must be dumped again."""
        texts = {node.text: node for node in nodes}
        # A crash dialog of ANOTHER app (left by a different project's run on a shared emulator) covers the screen too, Close app dismisses it.
        if any(text.endswith("keeps stopping") for text in texts) and "Close app" in texts:
            self.sh(f"input tap {texts['Close app'].center[0]} {texts['Close app'].center[1]}")
            time.sleep(2)
            return True
        not_responding = next((text for text in texts if text.endswith("isn't responding")), None)
        if not_responding is None:
            return False
        # NOTE: A loaded emulator can leave the Pixel Launcher stuck in a repeating not-responding dialog that swallows every tap, and Wait only brings it back.
        # NOTE: Closing the launcher restarts it cleanly on an emulator, while Wait stays right for any other app and for every phone because closing could kill the app under test or the owner's launcher.
        button = "Close app" if "Launcher" in not_responding and self.serial.startswith("emulator-") else "Wait"
        if button not in texts:
            return False
        self.sh(f"input tap {texts[button].center[0]} {texts[button].center[1]}")
        time.sleep(3)
        return True

    @staticmethod
    def _parse_nodes(raw: str) -> list[Node]:
        nodes = []
        for element in ET.fromstring(raw[raw.index("<?xml"):].encode()).iter("node"):
            bounds = [int(value) for value in re.findall(r"\d+", element.get("bounds", "[0,0][0,0]"))]
            nodes.append(Node(
                text=element.get("text", ""),
                desc=element.get("content-desc", ""),
                resource_id=element.get("resource-id", ""),
                checked=element.get("checked") == "true",
                bounds=(bounds[0], bounds[1], bounds[2], bounds[3]),
            ))
        return nodes

    def find(self, text: str | None = None, resource_id: str | None = None, contains: bool = False) -> list[Node]:
        matches = []
        for node in self.dump_ui():
            if text is not None:
                if contains and text.lower() not in node.text.lower():
                    continue
                if not contains and node.text != text:
                    continue
            if resource_id is not None and not node.resource_id.endswith(resource_id):
                continue
            matches.append(node)
        return matches

    def wait_for_text(self, text: str, timeout: float = 30, contains: bool = False) -> Node:
        deadline = time.time() + timeout
        while time.time() < deadline:
            found = self.find(text=text, contains=contains)
            if found:
                return found[0]
            time.sleep(1)
        raise TimeoutError(f"'{text}' did not appear within {timeout}s")

    def tap(self, x: int, y: int) -> None:
        self.assert_not_streaming()
        self.sh(f"input tap {x} {y}")

    def tap_text(self, text: str, timeout: float = 20, contains: bool = False) -> None:
        node = self.wait_for_text(text, timeout=timeout, contains=contains)
        self.tap(*node.center)

    def type_text(self, text: str) -> None:
        self.assert_not_streaming()
        self.sh(f"input text {text.replace(' ', '%s')}")

    def back(self) -> None:
        self.sh("input keyevent KEYCODE_BACK")

    # Apps

    def force_stop(self, package: str) -> None:
        self.sh(f"am force-stop {package}")

    def start(self, component: str) -> None:
        self.assert_not_streaming()
        self.sh(f"am start -W -n {component}")

    def install(self, apk: Path, user: int = 0, replace_signature_mismatch: bool = False) -> bool:
        """Installs APK strictly into specified user profile (defaults to User 0 primary). Returns True when an emulator build with another signature was replaced, which also resets its data and permission grants. Only the first install of a run may ask for that, update path checks must keep failing on a mismatch."""
        try:
            self.adb("install", "--user", str(user), "-r", str(apk), timeout=300)
            return False
        except RuntimeError as error:
            # NOTE: An earlier run can leave a release-signed build on an emulator, and Android then refuses the debug signed update.
            # NOTE: Emulators are disposable, so the old build is removed and the install repeated. Phones keep their data and the error stands.
            match = re.search(r"INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package (\S+) signatures", str(error))
            if match is None or not replace_signature_mismatch or not self.serial.startswith("emulator-"):
                raise
            print(f"[INFO] Replacing {match.group(1)} on {self.serial}: signature differs from the installed build", flush=True)
            self.uninstall(match.group(1))
            self.adb("install", "--user", str(user), "-r", str(apk), timeout=300)
            return True

    def uninstall_user(self, package: str, user: int) -> None:
        """Uninstalls package from a specific secondary profile without affecting other users."""
        self.sh(f"pm uninstall --user {user} {package}", check=False)

    def uninstall(self, package: str) -> None:
        """Completely purges package from device across all profiles."""
        self.adb("uninstall", package, check=False)

    def run_as(self, package: str, command: str, check: bool = True) -> str:
        return self.sh(f"run-as {package} {command}", check=check)

    # Screenshots

    def screenshot(self, save_to: Path | None = None, half: bool = True) -> Image.Image:
        image = Image.open(io.BytesIO(self.adb("exec-out", "screencap", "-p", binary=True))).convert("RGB")
        if save_to is not None:
            preview = image.resize((image.width // 2, image.height // 2)) if half else image
            preview.save(save_to)
        return image

    @staticmethod
    def luminance(image: Image.Image, box: tuple[float, float, float, float] = (0.2, 0.3, 0.8, 0.7)) -> float:
        """Mean luminance (0 to 255) of a region given as fractions of the image size."""
        left, top, right, bottom = box
        region = image.crop((int(image.width * left), int(image.height * top), int(image.width * right), int(image.height * bottom)))
        histogram = region.convert("L").histogram()
        total = sum(histogram)
        return sum(index * count for index, count in enumerate(histogram)) / total if total else 0.0

    # Logs

    def device_time(self) -> str:
        return self.sh("date '+%m-%d %H:%M:%S.000'").strip()

    def crashes_since(self, since: str, packages: tuple[str, ...]) -> list[str]:
        log = self.sh(f"logcat -b crash -d -T '{since}'", check=False)
        return [line.strip() for line in log.splitlines() if "Process: " in line and any(package in line for package in packages)]

    def logcat_since(self, since: str, tag: str) -> list[str]:
        return [line for line in self.sh(f"logcat -d -s {tag} -T '{since}'", check=False).splitlines() if tag in line]

    def write_app_file(self, package: str, path: str, data: bytes) -> None:
        """Writes a file inside the app sandbox through run-as, streaming the bytes over stdin."""
        result = subprocess.run(
            ["adb", "-s", self.serial, "exec-in", "run-as", package, "sh", "-c", f"cat > {path}"],
            input=data, capture_output=True, timeout=60,
        )
        if result.returncode != 0:
            raise RuntimeError(f"Writing {path} failed: {result.stderr.decode(errors='replace').strip()}")
