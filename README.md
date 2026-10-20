# Native-Ports

Native reimplementations / ports of games that were never released for the
target platform.

## Current target

**Feeding Frenzy 2** (PopCap, 2006) → **Android**.

Status: *awaiting source assets.*

## Why the assets aren't in this repo

The game is commercial software. No game data, binaries, or repacked archives
are committed here, and the `.gitignore` enforces that. To build, you need a
legally obtained copy of the game in `downloads/`.

The upstream port project should be treated as an engine + data loader: this
repository holds *code*, and the user supplies the *content* they own.

## Toolchain setup

The only thing needed to inspect an archive is a working `unrar`. If the
system has none, build one from the source vendored inside the
[`unrar-cffi`](https://pypi.org/project/unrar-cffi/) sdist — it needs only a
C++ compiler, not the Python headers:

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install rarfile py7zr

pip download unrar-cffi --no-deps -d /tmp/uc
mkdir -p /tmp/unrarsrc && tar xzf /tmp/uc/unrar-cffi-*.tar.gz -C /tmp/unrarsrc
cd /tmp/unrarsrc/unrar-cffi-*/unrarsrc && make -j"$(nproc)"

mkdir -p "$OLDPWD/.tools" && cp unrar "$OLDPWD/.tools/"
```

`.tools/` and `.venv/` are gitignored — both are reproducible from the steps
above.

## Inspecting an untrusted archive

Never double-click a game repack from a file host. Inspect it first; this
script hashes, lists, and magic-byte-sniffs every member without extracting
or executing any of them:

```bash
python3 tools/analyze_archive.py downloads/game.rar
```

It flags executables, scripts, disguised payloads (a `.txt` that is really a
PE binary), suspicious names like `crack.exe` / `autorun.inf`, and
high-entropy members that look packed or encrypted.

Exit codes: `0` clean, `1` contains executables or payload indicators,
`2` the archive could not be read.

Always cross-check the printed SHA-256 against VirusTotal before running
anything.

## Layout

```
tools/analyze_archive.py   static archive triage (no extraction, no execution)
downloads/                 user-supplied game assets   (gitignored)
extracted/                 unpacked game data           (gitignored)
android/                   the port itself              (to come)
```

## License

Apache 2.0 — see [LICENSE](LICENSE). This covers the code in this repository
only; it grants no rights to any game's assets or trademarks.
