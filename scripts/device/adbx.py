"""ADB helpers for DarQ device automation.

Covers the safety preflight, UI navigation by text from uiautomator dumps, screenshots with
luminance sampling, and reading app files through run-as. Rules follow the android-mastery
device testing playbook: never act on a device that is streaming, in a call, locked or in use.
"""

from __future__ import annotations

import io
import re
import subprocess
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

    def idle_seconds(self) -> float:
        uptime = float(self.sh("cat /proc/uptime").split()[0])
        match = re.search(r"lastUserActivityTime=(\d+)", self.sh("dumpsys power"))
        return uptime - int(match.group(1)) / 1000 if match else float("inf")

    def preflight(self, min_idle_seconds: int = 60) -> None:
        focus = self.focus()
        if any(marker in focus for marker in STREAM_FOCUS_MARKERS):
            raise PreflightError(f"Device is streaming, refusing input ({focus})")
        if "mCallState=0" not in self.sh("dumpsys telephony.registry | grep -m1 mCallState", check=False):
            raise PreflightError("Device is in a call")
        if "isKeyguardShowing=true" in self.sh("dumpsys window | grep isKeyguardShowing", check=False):
            raise PreflightError("Device is locked")
        if "mWakefulness=Awake" not in self.sh("dumpsys power | grep mWakefulness=", check=False):
            raise PreflightError("Screen is off")
        idle = self.idle_seconds()
        if idle < min_idle_seconds:
            raise PreflightError(f"Device was used {idle:.0f}s ago, waiting for {min_idle_seconds}s of idle")

    def assert_not_streaming(self) -> None:
        focus = self.focus()
        if any(marker in focus for marker in STREAM_FOCUS_MARKERS):
            raise PreflightError(f"Stream window took focus mid-test ({focus})")

    # UI

    def dump_ui(self, retries: int = 8) -> list[Node]:
        for _ in range(retries):
            # A failed dump leaves the previous file behind, so it is deleted first to never read a stale screen.
            self.sh(f"rm -f {UI_DUMP_PATH}", check=False)
            self.sh(f"uiautomator dump {UI_DUMP_PATH}", check=False)
            raw = self.sh(f"cat {UI_DUMP_PATH}", check=False)
            if raw.lstrip().startswith("<?xml"):
                nodes = self._parse_nodes(raw)
                # A loaded emulator can raise a system "isn't responding" dialog over everything. Waiting it out is safe.
                if any(node.text.endswith("isn't responding") for node in nodes):
                    wait = next((node for node in nodes if node.text == "Wait"), None)
                    if wait is not None:
                        self.sh(f"input tap {wait.center[0]} {wait.center[1]}")
                        time.sleep(3)
                        continue
                return nodes
            time.sleep(1)
        raise RuntimeError("uiautomator dump failed")

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

    def install(self, apk: Path) -> None:
        self.adb("install", "-r", str(apk), timeout=300)

    def uninstall(self, package: str) -> None:
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
