"""Create the DarQ Reborn release keystore once, outside the repository.

Usage:
    python scripts/release/generate_keystore.py --out-dir <folder outside the repo>

The script prompts for a password, runs keytool with the password passed through environment variables, writes release.jks and release.jks.base64 into the output folder, and prints only the public certificate SHA-256.
Passwords and key material are never printed and never written into the repository.
Back the folder up in two places. Losing the key means users can never update in place again.
"""
from __future__ import annotations

import argparse
import base64
import getpass
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out-dir", type=Path, required=True, help="Folder outside the repository")
    parser.add_argument("--alias", default="darq-reborn")
    parser.add_argument("--dname", default="CN=DarQ Reborn, O=Mohit Arora, C=IN")
    args = parser.parse_args()

    out_dir = args.out_dir.resolve()
    if out_dir == ROOT or ROOT in out_dir.parents:
        print("Refusing to write signing material inside the repository.")
        return 1
    keytool = shutil.which("keytool")
    if not keytool:
        print("keytool not found. Install a JDK or add its bin folder to PATH.")
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)
    keystore = out_dir / "release.jks"
    if keystore.exists():
        print(f"{keystore} already exists. Refusing to overwrite a release key.")
        return 1

    password = getpass.getpass("Keystore and key password (min 12 chars): ")
    if len(password) < 12 or password != getpass.getpass("Repeat password: "):
        print("Password too short or not matching.")
        return 1

    env = {**os.environ, "DARQ_KS_PASS": password}
    gen = subprocess.run(
        [
            keytool, "-genkeypair", "-v",
            "-keystore", str(keystore),
            "-alias", args.alias,
            "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
            "-dname", args.dname,
            "-storepass:env", "DARQ_KS_PASS",
            "-keypass:env", "DARQ_KS_PASS",
        ],
        env=env, capture_output=True, text=True,
    )
    if gen.returncode != 0:
        print("keytool failed:", gen.stderr.strip()[:300])
        return 1

    (out_dir / "release.jks.base64").write_text(base64.b64encode(keystore.read_bytes()).decode("ascii"), encoding="ascii")

    listing = subprocess.run(
        [keytool, "-list", "-v", "-keystore", str(keystore), "-alias", args.alias, "-storepass:env", "DARQ_KS_PASS"],
        env=env, capture_output=True, text=True,
    )
    fingerprint = re.search(r"SHA256:\s*([0-9A-F:]+)", listing.stdout)
    print(f"Keystore written to {out_dir}")
    print(f"Alias: {args.alias}")
    print(f"Certificate SHA-256: {fingerprint.group(1) if fingerprint else '(unreadable)'}")
    print("Next: back up release.jks and release.jks.base64, then run scripts/release/rotate_key.py. Do not commit the folder.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
