"""Offline tests for the idle time parsing in adbx. No device or emulator is needed.

Usage:
    uv run --with pillow python -m unittest discover -s scripts/device -p "test_*.py"
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adbx import Device, PreflightError  # noqa: E402

PHONE = "FAKEPHONE0001"
EMULATOR = "emulator-5562"

# NOTE: These lines follow a Samsung dump, where the timestamp excludes hours of deep sleep that /proc/uptime counts.
PHONE_DUMP = """
  mLastWakeTime=244251730 (20105110 ms ago)
 mLastUserActivityTime(excludingAttention)=244253033
    lastUserActivityTime=236278494 (271759 ms ago)
    lastUserActivityTimeNoChangeLights=244247757 (1000 ms ago)
"""


class FakeDevice(Device):

    def __init__(self, serial: str, dump: str, uptime: str = "637079.90 1753669.33"):
        super().__init__(serial)
        self.outputs = {"dumpsys power": dump, "cat /proc/uptime": uptime}

    def sh(self, command: str, check: bool = True, timeout: int = 120) -> str:
        return self.outputs[command]


class IdleSecondsTest(unittest.TestCase):

    def test_phone_uses_the_printed_age(self):
        self.assertAlmostEqual(FakeDevice(PHONE, PHONE_DUMP).idle_seconds(), 271.759)

    def test_smallest_age_wins(self):
        dump = "  lastUserActivityTime=100 (900000 ms ago)\n  lastUserActivityTime=800 (5000 ms ago)\n"
        self.assertEqual(FakeDevice(PHONE, dump).idle_seconds(), 5.0)

    def test_field_with_m_prefix(self):
        self.assertEqual(FakeDevice(PHONE, "  mLastUserActivityTime=5000 (42000 ms ago)\n").idle_seconds(), 42.0)

    def test_now_and_future_timestamps_mean_zero_idle(self):
        for suffix in ("(now)", "(in 3 ms)"):
            dump = f"  lastUserActivityTime=100 (900000 ms ago)\n  lastUserActivityTime=800 {suffix}\n"
            self.assertEqual(FakeDevice(PHONE, dump).idle_seconds(), 0.0, suffix)

    def test_phone_without_an_age_is_refused(self):
        for dump in ("  lastUserActivityTime=236278494\n", "mWakefulness=Awake\n", ""):
            with self.assertRaises(PreflightError, msg=repr(dump)):
                FakeDevice(PHONE, dump).idle_seconds()

    def test_emulator_without_an_age_uses_the_latest_timestamp(self):
        dump = "  mLastUserActivityTime=40000\n  mLastUserActivityTime=90000\n"
        self.assertEqual(FakeDevice(EMULATOR, dump, uptime="100.00 50.00").idle_seconds(), 10.0)

    def test_emulator_without_any_activity_line_counts_as_idle(self):
        self.assertEqual(FakeDevice(EMULATOR, "mWakefulness=Awake\n").idle_seconds(), float("inf"))


if __name__ == "__main__":
    unittest.main()
