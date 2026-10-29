"""Create the signing certificate lineage that lets installs signed with the old key update to the new key.

Usage:
    python scripts/release/rotate_key.py --old-ks <old keystore> --old-alias androiddebugkey --new-ks <release.jks> --new-alias darq-reborn

The script prompts for both keystore passwords without echo and passes them to apksigner through standard input, so they never appear in a command line or a log.
The output file contains only public certificates and proof signatures, so it is safe to commit. Default output is scripts/release/signing-lineage.bin.
Keep both keystores backed up forever. Every later release must be signed with the new key and this lineage.
"""
from __future__ import annotations

import argparse
import getpass
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def find_apksigner() -> str | None:
    found = shutil.which("apksigner") or shutil.which("apksigner.bat")
    if found:
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk")
    tools = sorted(Path(sdk, "build-tools").glob("*"), key=lambda p: [int(x) if x.isdigit() else 0 for x in p.name.split(".")])
    for folder in reversed(tools):
        for name in ("apksigner.bat", "apksigner"):
            if (folder / name).exists():
                return str(folder / name)
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--old-ks", type=Path, required=True)
    parser.add_argument("--old-alias", default="androiddebugkey")
    parser.add_argument("--new-ks", type=Path, required=True)
    parser.add_argument("--new-alias", default="darq-reborn")
    parser.add_argument("--out", type=Path, default=ROOT / "scripts" / "release" / "signing-lineage.bin")
    args = parser.parse_args()

    apksigner = find_apksigner()
    if not apksigner:
        print("apksigner not found. Install Android SDK build-tools or set ANDROID_HOME.")
        return 1
    for keystore in (args.old_ks, args.new_ks):
        if not keystore.exists():
            print(f"{keystore} does not exist.")
            return 1
    if args.out.exists():
        print(f"{args.out} already exists. Refusing to overwrite a lineage.")
        return 1

    old_pass = getpass.getpass("Old keystore password: ")
    new_pass = getpass.getpass("New keystore password: ")
    command = [
        apksigner, "rotate", "--out", str(args.out),
        "--old-signer", "--ks", str(args.old_ks), "--ks-key-alias", args.old_alias, "--ks-pass", "stdin",
        "--new-signer", "--ks", str(args.new_ks), "--ks-key-alias", args.new_alias, "--ks-pass", "stdin",
    ]
    result = subprocess.run(command, input=f"{old_pass}\n{new_pass}\n", capture_output=True, text=True)
    if result.returncode != 0 or not args.out.exists():
        print("apksigner rotate failed:", (result.stderr or result.stdout).strip()[:400])
        return 1
    print(f"Lineage written to {args.out} ({args.out.stat().st_size} bytes). It holds only public data.")
    print("Next: commit the lineage file and run the release workflow with the new key secrets.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
