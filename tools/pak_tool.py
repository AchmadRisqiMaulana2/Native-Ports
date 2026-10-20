#!/usr/bin/env python3
"""
PopCap SexyAppFramework PAK & FeedingFrenzy.exe Reverse-Engineering / Oprek Tool.

Supports:
  1. Inspecting & unpacking original `main.pak` (XOR 0xF7 stream cipher, magic 0xBAC04AC0)
     OR directly reading `Feeding_Frenzy_Win_Preinstalled_EN.zip` without manual extraction.
  2. Reconstructing transparent PNG sprites from PopCap RGB (.jpg/.png) + Alpha Mask (_*.gif / *_alpha).
  3. Inspecting `FeedingFrenzy.exe` PE32 headers, sections, & known SexyAppBase memory pointer chains
     (v5.7.18.1 / v2.9.16.1 / v1.0).
  4. Repacking modified directories back into a valid XOR-0xF7 `main.pak` for modding / oprek!

Usage:
  python3 tools/pak_tool.py inspect <path/to/Feeding_Frenzy_Win_Preinstalled_EN.zip | main.pak>
  python3 tools/pak_tool.py unpack  <input.zip|main.pak> -o unpacked_dump --compose-png
  python3 tools/pak_tool.py pack    <input_folder> -o custom_main.pak
  python3 tools/pak_tool.py exe     <FeedingFrenzy.exe | input.zip>
"""

import argparse
import io
import json
import os
import struct
import sys
import zipfile
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Optional, Tuple

PAK_MAGIC = 0xBAC04AC0
PAK_XOR_KEY = 0xF7
FILEFLAGS_END = 0x80


@dataclass
class PakRecord:
    index: int
    name: str
    normalized_path: str
    size: int
    offset: int
    filetime_raw: int
    category: str


def xor_bytes(data: bytes, key: int = PAK_XOR_KEY) -> bytes:
    if key == 0:
        return data
    table = bytes(b ^ key for b in range(256))
    return data.translate(table)


def classify_asset(norm_path: str) -> str:
    lower = norm_path.lower()
    base = os.path.basename(lower)
    if lower.endswith((".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tga")):
        if base.startswith("_") or base.endswith("_.gif") or "alpha" in base:
            return "alpha_mask"
        return "sprite_image"
    if lower.endswith((".ogg", ".wav", ".mp3", ".mo3", ".xm", ".it", ".s3m", ".mod")):
        return "audio"
    if lower.endswith((".xml", ".txt", ".cfg", ".ini", ".dat")):
        return "config_data"
    if lower.endswith((".txt", ".fnt")):
        return "font"
    return "other"


def load_pak_bytes(source_path: str) -> Tuple[bytes, Optional[bytes], str]:
    """
    Loads raw `main.pak` bytes (and optional `FeedingFrenzy.exe` bytes) from either:
      - a direct `.pak` file,
      - a directory containing `main.pak`, or
      - a `.zip` archive such as `Feeding_Frenzy_Win_Preinstalled_EN.zip`.
    """
    p = Path(source_path)
    if not p.exists():
        raise FileNotFoundError(f"File not found: {source_path}")

    if p.is_dir():
        pak_bytes = None
        exe_bytes = None
        pak_label = ""
        for root, _, files in os.walk(p):
            for fn in files:
                full = Path(root) / fn
                if fn.lower().endswith(".pak") and pak_bytes is None:
                    pak_bytes = full.read_bytes()
                    pak_label = str(full)
                elif fn.lower().endswith(".exe") and "frenzy" in fn.lower() and exe_bytes is None:
                    exe_bytes = full.read_bytes()
        if pak_bytes is None:
            raise ValueError(f"No .pak file found inside directory: {source_path}")
        return pak_bytes, exe_bytes, pak_label

    raw = p.read_bytes()
    if raw[:4] == b"PK\x03\x04":
        pak_bytes = None
        exe_bytes = None
        pak_label = ""
        with zipfile.ZipFile(io.BytesIO(raw), "r") as zf:
            for info in zf.infolist():
                if info.is_dir():
                    continue
                low = info.filename.lower()
                if low.endswith(".pak") and pak_bytes is None:
                    pak_bytes = zf.read(info.filename)
                    pak_label = f"{source_path}::{info.filename}"
                elif low.endswith(".exe") and ("frenzy" in low or exe_bytes is None):
                    exe_bytes = zf.read(info.filename)
        if pak_bytes is None:
            raise ValueError(f"No .pak archive found inside ZIP: {source_path}")
        return pak_bytes, exe_bytes, pak_label

    return raw, None, str(p)


def parse_pak(pak_raw: bytes) -> Tuple[bytes, int, List[PakRecord]]:
    if len(pak_raw) < 9:
        raise ValueError("PAK file too small (< 9 bytes)")

    magic_plain = struct.unpack_from("<I", pak_raw, 0)[0]
    if magic_plain == PAK_MAGIC:
        decoded = pak_raw
    else:
        decoded = xor_bytes(pak_raw, PAK_XOR_KEY)
        magic_xor = struct.unpack_from("<I", decoded, 0)[0]
        if magic_xor != PAK_MAGIC:
            raise ValueError(
                f"Invalid PopCap PAK magic: raw=0x{magic_plain:08X}, xorF7=0x{magic_xor:08X} "
                f"(expected 0x{PAK_MAGIC:08X})"
            )

    version = struct.unpack_from("<I", decoded, 4)[0]
    pos = 8
    temp_entries = []
    idx = 0

    while pos < len(decoded):
        flags = decoded[pos]
        pos += 1
        if (flags & FILEFLAGS_END) != 0:
            break
        if pos >= len(decoded):
            raise ValueError("Unexpected EOF reading PAK filename length")
        name_len = decoded[pos]
        pos += 1
        name_bytes = decoded[pos : pos + name_len]
        pos += name_len
        name = name_bytes.decode("latin1")
        norm = name.replace("\\", "/").lstrip("/")
        file_size, filetime_raw = struct.unpack_from("<iQ", decoded, pos)
        pos += 12
        temp_entries.append((idx, name, norm, file_size, filetime_raw))
        idx += 1

    data_cursor = pos
    records: List[PakRecord] = []
    for i, name, norm, size, ftime in temp_entries:
        records.append(
            PakRecord(
                index=i,
                name=name,
                normalized_path=norm,
                size=size,
                offset=data_cursor,
                filetime_raw=ftime,
                category=classify_asset(norm),
            )
        )
        data_cursor += size

    return decoded, version, records


def build_pak(folder: str, xor_encrypt: bool = True) -> bytes:
    root = Path(folder)
    files: List[Tuple[str, bytes]] = []
    for dirpath, _, filenames in os.walk(root):
        for fn in sorted(filenames):
            full = Path(dirpath) / fn
            rel = str(full.relative_to(root)).replace("/", "\\")
            files.append((rel, full.read_bytes()))

    buf = bytearray()
    buf.extend(struct.pack("<II", PAK_MAGIC, 0))
    for rel, data in files:
        encoded_name = rel.encode("latin1")
        if len(encoded_name) > 255:
            raise ValueError(f"Path too long for PopCap PAK (>255): {rel}")
        buf.append(0x00)
        buf.append(len(encoded_name))
        buf.extend(encoded_name)
        buf.extend(struct.pack("<iQ", len(data), 127200000000000000))
    buf.append(FILEFLAGS_END)
    for _, data in files:
        buf.extend(data)

    raw_bytes = bytes(buf)
    return xor_bytes(raw_bytes, PAK_XOR_KEY) if xor_encrypt else raw_bytes


def inspect_exe_bytes(exe_bytes: bytes) -> Dict[str, object]:
    info: Dict[str, object] = {
        "file_size_bytes": len(exe_bytes),
        "is_pe": False,
        "detected_build": "unknown",
        "sections": [],
        "known_memory_chains": {},
    }
    if len(exe_bytes) < 0x100 or exe_bytes[:2] != b"MZ":
        return info

    pe_offset = struct.unpack_from("<I", exe_bytes, 0x3C)[0]
    if pe_offset + 24 > len(exe_bytes) or exe_bytes[pe_offset : pe_offset + 4] != b"PE\x00\x00":
        return info

    machine, num_sections, timestamp, _, _, opt_hdr_size, _ = struct.unpack_from(
        "<HHIIIHH", exe_bytes, pe_offset + 4
    )
    opt_offset = pe_offset + 24
    image_size = struct.unpack_from("<I", exe_bytes, opt_offset + 56)[0]
    image_base = struct.unpack_from("<I", exe_bytes, opt_offset + 28)[0]

    sections = []
    sec_table = opt_offset + opt_hdr_size
    for i in range(num_sections):
        off = sec_table + i * 40
        if off + 40 > len(exe_bytes):
            break
        raw_name = exe_bytes[off : off + 8].rstrip(b"\x00").decode("latin1", errors="replace")
        vsize, vaddr, rsize, roff = struct.unpack_from("<IIII", exe_bytes, off + 8)
        sections.append(
            {
                "name": raw_name,
                "virtual_address": f"0x{vaddr:08X}",
                "virtual_size": vsize,
                "raw_size": rsize,
                "raw_offset": f"0x{roff:08X}",
            }
        )

    # Match known FeedingFrenzy.exe builds by PE SizeOfImage
    if image_size >= 876000:
        build = "v5.7.18.1 (PopCap / GameHouse Retail)"
        chains = {
            "stageLoading": "FeedingFrenzy.exe+0xC10B4 -> +0x118 -> +0x178 -> +0x198",
            "isPlaying": "FeedingFrenzy.exe+0xC10B4 -> +0x118 -> +0x30 -> +0x10 -> +0xC -> +0x120 -> +0x4 -> +0x1D4",
            "stage": "FeedingFrenzy.exe+0xC0880 -> +0x1E4",
            "foodBank": "FeedingFrenzy.exe+0xC0880 -> +0x1A8",
            "timeAttackClock": "FeedingFrenzy.exe+0xC0880 -> +0x58 -> +0x10",
        }
    elif image_size >= 872000:
        build = "v2.9.16.1 (PopCap Deluxe)"
        chains = {
            "stageLoading": "FeedingFrenzy.exe+0xBEE58 -> +0x118 -> +0x178 -> +0x198",
            "isPlaying": "FeedingFrenzy.exe+0xBEE58 -> +0x118 -> +0x30 -> +0x10 -> +0xC -> +0x120 -> +0x4 -> +0x1D4",
            "stage": "FeedingFrenzy.exe+0xBE980 -> +0x1C0",
            "foodBank": "FeedingFrenzy.exe+0xBE980 -> +0x184",
            "timeAttackClock": "FeedingFrenzy.exe+0xBE980 -> +0x34 -> +0x10",
        }
    elif image_size >= 843000:
        build = "v1.0 (Sprout / Early PopCap Preinstalled)"
        chains = {
            "stageLoading": "feedingfrenzy.exe+0xB937C -> +0x118 -> +0x178 -> +0x198",
            "stage": "feedingfrenzy.exe+0xB894C -> +0x128",
            "foodBank": "feedingfrenzy.exe+0xB894C -> +0xEC",
            "timeAttackClock": "feedingfrenzy.exe+0xB894C -> +0x24 -> +0x10",
        }
    else:
        build = f"Custom/Unknown PE32 (SizeOfImage={image_size})"
        chains = {}

    info.update(
        {
            "is_pe": True,
            "machine": f"0x{machine:04X}",
            "image_base": f"0x{image_base:08X}",
            "size_of_image": image_size,
            "timestamp_unix": timestamp,
            "detected_build": build,
            "sections": sections,
            "known_memory_chains": chains,
        }
    )
    return info


def compose_alpha_sprites(unpacked_dir: Path) -> int:
    try:
        from PIL import Image
    except ImportError:
        return 0

    composed = 0
    for root, _, files in os.walk(unpacked_dir):
        file_map = {f.lower(): f for f in files}
        for fn in files:
            low = fn.lower()
            if low.startswith("_") or not low.endswith((".jpg", ".jpeg", ".png")):
                continue
            stem = os.path.splitext(low)[0]
            alpha_candidates = [
                f"_{stem}.gif",
                f"_{stem}.png",
                f"_{stem}.jpg",
                f"{stem}_.gif",
                f"{stem}_alpha.gif",
                f"{stem}_alpha.png",
            ]
            alpha_fn = next((file_map[c] for c in alpha_candidates if c in file_map), None)
            if not alpha_fn:
                continue
            rgb_path = Path(root) / fn
            alpha_path = Path(root) / alpha_fn
            out_path = Path(root) / f"{os.path.splitext(fn)[0]}.composed.png"
            with Image.open(rgb_path) as rgb_img, Image.open(alpha_path) as alpha_img:
                rgb = rgb_img.convert("RGB")
                alpha = alpha_img.convert("L")
                if alpha.size != rgb.size:
                    alpha = alpha.resize(rgb.size)
                rgba = rgb.copy()
                rgba.putalpha(alpha)
                rgba.save(out_path, "PNG")
                composed += 1
    return composed


def cmd_inspect(args: argparse.Namespace) -> int:
    pak_raw, exe_bytes, label = load_pak_bytes(args.source)
    _, version, records = parse_pak(pak_raw)
    summary: Dict[str, int] = {}
    for rec in records:
        summary[rec.category] = summary.get(rec.category, 0) + 1

    report = {
        "source": label,
        "pak_size_bytes": len(pak_raw),
        "pak_version": version,
        "entry_count": len(records),
        "categories": summary,
        "exe_info": inspect_exe_bytes(exe_bytes) if exe_bytes else None,
        "sample_entries": [asdict(r) for r in records[:30]],
    }
    print(json.dumps(report, indent=2))
    return 0


def cmd_unpack(args: argparse.Namespace) -> int:
    pak_raw, exe_bytes, label = load_pak_bytes(args.source)
    decoded, _, records = parse_pak(pak_raw)
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    for rec in records:
        dest = out_dir / rec.normalized_path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(decoded[rec.offset : rec.offset + rec.size])

    composed = compose_alpha_sprites(out_dir) if args.compose_png else 0
    if exe_bytes:
        (out_dir / "_exe_inspection.json").write_text(
            json.dumps(inspect_exe_bytes(exe_bytes), indent=2), encoding="utf-8"
        )
    print(f"Unpacked {len(records)} files from {label} -> {out_dir} (composed RGBA PNGs: {composed})")
    return 0


def cmd_pack(args: argparse.Namespace) -> int:
    packed = build_pak(args.folder, xor_encrypt=not args.plain)
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(packed)
    print(f"Packed {args.folder} -> {out_path} ({len(packed)} bytes, xor_0xF7={not args.plain})")
    return 0


def cmd_exe(args: argparse.Namespace) -> int:
    p = Path(args.source)
    data = p.read_bytes()
    if data[:4] == b"PK\x03\x04":
        _, exe_bytes, _ = load_pak_bytes(args.source)
        if not exe_bytes:
            raise ValueError(f"No .exe found in {args.source}")
        data = exe_bytes
    print(json.dumps(inspect_exe_bytes(data), indent=2))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="PopCap Feeding Frenzy PAK & EXE Oprek Tool")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_ins = sub.add_parser("inspect", help="Inspect main.pak or Feeding_Frenzy_Win_Preinstalled_EN.zip")
    p_ins.add_argument("source")
    p_ins.set_defaults(func=cmd_inspect)

    p_unp = sub.add_parser("unpack", help="Extract main.pak assets & optionally compose RGBA PNGs")
    p_unp.add_argument("source")
    p_unp.add_argument("-o", "--output", default="unpacked_dump")
    p_unp.add_argument("--compose-png", action="store_true")
    p_unp.set_defaults(func=cmd_unpack)

    p_pak = sub.add_parser("pack", help="Repack modded asset folder into XOR-0xF7 main.pak")
    p_pak.add_argument("folder")
    p_pak.add_argument("-o", "--output", default="custom_main.pak")
    p_pak.add_argument("--plain", action="store_true", help="Skip 0xF7 XOR encryption")
    p_pak.set_defaults(func=cmd_pack)

    p_exe = sub.add_parser("exe", help="Inspect FeedingFrenzy.exe PE32 structure & memory offsets")
    p_exe.add_argument("source")
    p_exe.set_defaults(func=cmd_exe)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
