#!/usr/bin/env python3
"""
Static analysis for a RAR archive - inspect WITHOUT executing anything.

Run this against any third-party game archive before unpacking. It never
extracts payload files to disk, never executes anything, and never makes
network requests.

Usage:
    python tools/analyze_archive.py /path/to/file.rar
"""

from __future__ import annotations

import argparse
import hashlib
import math
import os
import sys
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
UNRAR = REPO_ROOT / ".tools" / "unrar"

# Extensions that execute code on Windows, or that commonly deliver a payload.
EXECUTABLE_EXTS = {
    ".exe", ".dll", ".scr", ".com", ".pif", ".bat", ".cmd", ".vbs",
    ".vbe", ".js", ".jse", ".wsf", ".wsh", ".ps1", ".psm1", ".hta",
    ".cpl", ".msi", ".msp", ".reg", ".lnk", ".inf", ".sys", ".drv",
    ".ocx", ".jar", ".application", ".gadget",
}

# Names that are suspicious when they appear next to a game's real binaries.
SUSPICIOUS_NAMES = {
    "autorun.inf", "setup.exe", "install.exe", "crack.exe", "keygen.exe",
    "patch.exe", "serial.exe", "loader.exe", "activator.exe",
    "readme.exe", "readme.txt.exe", "documentation.exe", "update.exe",
    "svchost.exe", "explorer.exe", "taskmgr.exe", "regsvr32.exe",
}

MAGIC = {
    b"MZ": "PE (Windows executable)",
    b"\x7fELF": "ELF (Linux/Android executable)",
    b"\xca\xfe\xba\xbe": "Mach-O / Java class",
    b"PK\x03\x04": "ZIP archive",
    b"7z\xbc\xaf\x27\x1c": "7-Zip archive",
    b"Rar!": "RAR archive",
    b"\x1f\x8b": "gzip stream",
    b"<!DOCTYPE": "HTML document",
    b"<html": "HTML document",
    b"#!": "Script with shebang",
}


def sha256_of(path: Path, block: int = 1 << 20) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(block), b""):
            h.update(chunk)
    return h.hexdigest()


def md5_of(path: Path, block: int = 1 << 20) -> str:
    h = hashlib.md5()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(block), b""):
            h.update(chunk)
    return h.hexdigest()


def entropy(data: bytes) -> float:
    """Shannon entropy in bits/byte. >7.5 suggests compressed or encrypted data."""
    if not data:
        return 0.0
    counts = Counter(data)
    total = len(data)
    return -sum((c / total) * math.log2(c / total) for c in counts.values())


def sniff_magic(head: bytes) -> str:
    for sig, label in MAGIC.items():
        if head.startswith(sig):
            return label
    return "unrecognised"


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024
    return f"{n:.1f} GB"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("archive", type=Path)
    ap.add_argument("--max-sniff", type=int, default=40,
                    help="how many members to read magic bytes from (default 40)")
    args = ap.parse_args()

    path: Path = args.archive
    if not path.is_file():
        print(f"error: no such file: {path}", file=sys.stderr)
        return 2

    size = path.stat().st_size
    print("=" * 72)
    print("ARCHIVE IDENTITY")
    print("=" * 72)
    print(f"  path     : {path}")
    print(f"  size     : {human(size)} ({size:,} bytes)")
    print(f"  md5      : {md5_of(path)}")
    print(f"  sha256   : {sha256_of(path)}")
    with path.open("rb") as fh:
        head = fh.read(16)
    print(f"  magic    : {sniff_magic(head)}")
    print("  -> submit the sha256 to VirusTotal / MalwareBazaar for a reputation check")
    print()

    try:
        import rarfile
    except ImportError:
        print("error: rarfile not installed (pip install rarfile)", file=sys.stderr)
        return 2

    if UNRAR.exists():
        rarfile.UNRAR_TOOL = str(UNRAR)

    try:
        rarfile.tool_setup()
    except Exception as exc:  # noqa: BLE001
        print(f"error: no working unrar backend ({exc})", file=sys.stderr)
        return 2

    try:
        rf = rarfile.RarFile(path)
    except Exception as exc:  # noqa: BLE001
        print(f"error: cannot open archive: {exc}", file=sys.stderr)
        return 2

    members = rf.infolist()
    print("=" * 72)
    print(f"CONTENTS  ({len(members)} members)")
    print("=" * 72)

    ext_counts: Counter[str] = Counter()
    executables: list[rarfile.RarInfo] = []
    suspicious: list[rarfile.RarInfo] = []
    total_unpacked = 0

    for info in sorted(members, key=lambda i: i.filename.lower()):
        ext = os.path.splitext(info.filename)[1].lower()
        ext_counts[ext or "(none)"] += 1
        total_unpacked += info.file_size

        flag = ""
        if ext in EXECUTABLE_EXTS:
            executables.append(info)
            flag = "  <-- EXECUTABLE"
        if Path(info.filename).name.lower() in SUSPICIOUS_NAMES:
            suspicious.append(info)
            flag = "  <-- SUSPICIOUS NAME"

        print(f"  {human(info.file_size):>10}  {info.filename}{flag}")

    print()
    print("=" * 72)
    print("TYPE BREAKDOWN")
    print("=" * 72)
    for ext, count in ext_counts.most_common():
        print(f"  {count:>5}  {ext}")

    print()
    print("=" * 72)
    print("MAGIC-BYTE SNIFF  (detects files lying about their extension)")
    print("=" * 72)
    mismatches = 0
    for info in members[: args.max_sniff]:
        if info.is_dir() or info.file_size == 0:
            continue
        try:
            with rf.open(info) as fh:
                blob = fh.read(4096)
        except Exception as exc:  # noqa: BLE001
            print(f"  ??  {info.filename}: unreadable ({exc})")
            continue
        label = sniff_magic(blob[:16])
        ent = entropy(blob)
        ext = os.path.splitext(info.filename)[1].lower()
        lie = ""
        if ext not in EXECUTABLE_EXTS and label.startswith("PE "):
            lie = "  <-- DISGUISED EXECUTABLE"
            mismatches += 1
        if ext == ".txt" and label == "Script with shebang":
            lie = "  <-- TEXT FILE IS ACTUALLY A SCRIPT"
            mismatches += 1
        packed = "  packed/encrypted" if ent > 7.5 else ""
        print(f"  {label:<28} entropy={ent:4.2f}  {info.filename}{lie}{packed}")

    print()
    print("=" * 72)
    print("VERDICT")
    print("=" * 72)
    print(f"  compressed size : {human(size)}")
    print(f"  unpacked size   : {human(total_unpacked)}")
    if size:
        print(f"  ratio           : {total_unpacked / size:.2f}x")
    print(f"  executables     : {len(executables)}")
    for info in executables:
        print(f"      - {info.filename} ({human(info.file_size)})")
    print(f"  suspicious names: {len(suspicious)}")
    for info in suspicious:
        print(f"      - {info.filename}")
    print(f"  disguised files : {mismatches}")

    print()
    if suspicious or mismatches:
        print("  >>> HIGH RISK: do not run any extracted binary.")
        print("  >>> Treat as hostile until the sha256 clears on VirusTotal.")
        return 1
    if executables:
        print("  >>> Contains executables. Expected for a game, but verify each one")
        print("  >>> against VirusTotal before running.")
        return 1
    print("  >>> No executables or disguised payloads detected.")
    print("  >>> Still: game data from an untrusted source should run sandboxed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
