# Acquiring Feeding Frenzy 2

## Why the MediaFire link can't be used

Two independent, unrelated blockers:

1. **Network.** This sandbox reaches only `github.com`, `registry.npmjs.org` and
   `pypi.org`. Everything else is refused at the egress firewall. Verified:

   | Host | Result |
   |---|---|
   | `mediafire.com` (and `www.`, `download.`, `download2.`, `api.`) | `000` |
   | `store.steampowered.com` | `000` |
   | `ea.com` | `000` |
   | `archive.org` | `000` |
   | `github.com`, `pypi.org` | `200` |

   Three independent TLS stacks (`curl`, `wget`, Python/OpenSSL) fail
   identically. No proxy is configured, so there is no alternate route. This
   is a platform setting, not a misconfiguration.

2. **The file itself.** The MediaFire page reports *"File Access Blocked — this
   file has been blocked for security reasons"* behind a malware advisory. It
   is a 2009 repack of commercial software from a warez site. It does not get
   downloaded as a workaround for a security warning.

## Legitimate sources

| Store | Price (verified 2026-10-21) | Notes |
|---|---|---|
| **Steam** — app `3390`, *Feeding Frenzy 2 Deluxe* | **Rp 45.999** (IDR, no discount active) | Cheapest; DRM-light, runs under Proton on Linux |
| EA App | US$4.99 | Same build, EA's own storefront |
| Xbox / Microsoft Store | US$4.99 | Console + Xbox One/Series back-compat |

Steam has repeatedly discounted this to roughly **US$0.99–1.24** during sales.

None of these can be fetched unattended: all three require an authenticated
purchase tied to an account. That is the reason a direct download isn't
possible here — not a technical limitation that another retry could solve.

## Working around the network limit

Buy and install the game on your own machine, then **attach the installed game
folder to this chat**. Attachments land in the workspace directly and bypass
the sandbox network entirely.

Steam install path (Windows):

```
C:\Program Files (x86)\Steam\steamapps\common\Feeding Frenzy 2 Deluxe
```

Steam Play / Proton (Linux):

```
<SteamLibrary>/steamapps/common/Feeding Frenzy 2 Deluxe
```

Do **not** attach the `.exe` alone — the port needs the asset payload, which is
the bulk of the install:

```
assets/     sprites, levels, audio
main.pak    PopCap framework data archive
```

## Port feasibility: Android

There is **no official Android release**. Wikipedia lists Windows, Xbox 360
(XBLA) and PlayStation 3 (PSN) only. The "Feeding Frenzy 2 on Android" videos
circulating online link to *Let Me Eat 2* (`com.gg.lme2`), a clone, not a port.
So an Android target is genuinely unserved.

### What the binary looks like

From the open-source
[`barretg/feeding-frenzy-2-archipelago`](https://github.com/barretg/feeding-frenzy-2-archipelago)
reference (ISCs licensed), which hooks the live game with MinHook:

- **Win32 PE, 32-bit.** Image base `0x400000`; hook sites are *hardcoded
  absolute addresses* — `0x47A250`, `0x48E2E0`, `0x40346D`, `0x42A98C`,
  `0x4204A0`, `0x100072E0`.
- **ASLR is disabled or fixed.** Those absolute addresses resolve on every
  launch, which is only possible with a non-relocating image. This is a big
  simplification: no per-build address scanning is needed.
- The reference project runs the game under **Proton** on Linux and hooks
  roughly a dozen subsystems — player fish, boss HP, boundaries, level guard,
  power-ups, frenzy, deathlink.
- Built on the **PopCap Games Framework** (per PCGamingWiki), which is a stable,
  well-understood 2D engine with a documented `main.pak` container.

### Consequence for the port

Because no source is available, the realistic routes are:

1. **Wrap the existing PE** — run the 32-bit Windows build through
   Wine/Box86 (Winlator-style) on Android and hook it as the reference project
   does on desktop. Fastest to something playable; preserves original assets.
2. **Reimplementation** — rewrite the engine in SDL2 and load the original
   `main.pak` assets. The framework's data formats are simple; this gives a
   genuinely native, low-overhead app, but it is a large effort.

Route 1 is the sane first milestone. Route 2 is the thing that actually
deserves the name *Native-Ports*.

Both need the game assets, which is why acquisition is the current blocker.
