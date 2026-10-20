# 🐟 Native-Ports — Feeding Frenzy (Android 9+ Native Java + Kotlin Port & Oprek Edition)

Native Android port (`minSdk = 28` **Android 9.0 Pie+**, `targetSdk = 34`) untuk game klasik PopCap / Sprout Games **Feeding Frenzy (2004)** yang ditulis dengan arsitektur **Hybrid Native Java + Kotlin** — lengkap dengan **PopCap `main.pak` (`XOR 0xF7`) Unpacker/Repacker**, **`FeedingFrenzy.exe` PE32 Binary Inspector**, dan **In-Game Oprek / Mod Lab**.

---

## ✨ Fitur Utama

### 1. Native Android 9.0+ Engine (`Java` + `Kotlin` Interop)
- **Java Core Engine (`app/src/main/java/...`)**:
  - `PakArchiveReader.java` & `PakEntry.java` — Dekoder native format arsip PopCap `SexyAppFramework` (`0xBAC04AC0`, stream cipher `XOR 0xF7`) + pembaca langsung file `.zip` (`Feeding_Frenzy_Win_Preinstalled_EN.zip`) maupun `main.pak` tanpa perlu ekstrak manual.
  - `AlphaMaskComposer.java` — Penggabung pixel-presisi sprite PopCap `RGB (.jpg/.png)` + `Grayscale Alpha Mask (_*.gif)` menjadi bitmap `ARGB_8888` transparan.
  - `ExeBinaryInspector.java` — Parser header PE32 `FeedingFrenzy.exe` yang mendeteksi build (`v5.7.18.1`, `v2.9.16.1`, `v1.0`) beserta pointer chain state game (`stage`, `foodBank`, `timeAttackClock`, `isPlaying`).
  - `FrenzyWorld.java`, `StageCatalog.java`, `FishEntity.java`, `BonusItem.java`, `ModOprekConfig.java` — Simulasi 60Hz lengkap **40 Stage / 5 Chapter** (*Andy the Angelfish → Leon the Lionfish → Eddie the Anglerfish → J.D. the John Dory → Orville the Orca*), 3 tingkat pertumbuhan per stage (*Small / Medium / Large*), kombo **`FEEDING FRENZY! x2..x8`**, *Food Bank*, *Oyster White & Black Pearl*, *Power-up Bubbles*, *Jellyfish*, & *Naval Mines*.
  - `ProceduralSfxGenerator.java` — Synthesizer audio underwater 44.1kHz 16-bit PCM WAV (*Chomp, Big Gulp, Frenzy Fanfare, Grow Up, Bubble Pop, Pearl Chime, Mine Boom*).
- **Kotlin Android 9+ Layer (`app/src/main/kotlin/...`)**:
  - `MainActivity.kt` — Fullscreen immersive `shortEdges` cutout support (Android 9 Pie API 28+), auto-save High Score, & Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`) untuk import `Feeding_Frenzy_Win_Preinstalled_EN.zip` / `main.pak` langsung dari HP Android!
  - `FeedingFrenzySurfaceView.kt` & `OceanRenderer.kt` — Hardware-accelerated `lockHardwareCanvas()` 60/120 FPS renderer + HUD bergaya arcade klasik PopCap.
  - `TouchController.kt` — Dual-mode touch controls (*Floating Virtual Analog Stick* atau *Direct Touch Follow*) + tombol skill **BOOST** & **INHALE**.
  - `OprekModSheet.kt` — Panel **⚡ OPREK LAB** langsung di dalam game!

### 2. Fitur ⚡ Oprek / Mod Lab
- **Preset Instan**: `Vanilla 2004`, `🔥 Sultan Oprek`, `🐋 Titan Orca Mode`
- **Toggle Live**:
  - 🛡️ **God Mode** (kebal dimakan Hiu Putih, Barracuda, Ubur-ubur, & Ranjau Laut)
  - 🦈 **Apex Eat-Anything** (ikan kecil pun bisa langsung melahap *Great White Shark*!)
  - 🔥 **Always Feeding Frenzy x8**
  - ⚡ **Infinite Boost & Zero Cooldown Inhale**
  - 🎯 **Hitbox, AI Vector & Live FPS Profiler Overlay**
- **Slider Real-Time**: *Speed Multiplier (`0.5x–3.0x`)*, *Manual Fish Size Scale*, *Food Magnet Vacuum (`0–450 px`)*, *Spawn Swarm Density*, & **Stage Teleporter (`Stage 1–40`)**.

---

## 🚀 Cara Pakai File Asli `Feeding_Frenzy_Win_Preinstalled_EN.zip`

Ada **3 cara super gampang** untuk menggabungkan aset original PopCap (`main.pak` + `FeedingFrenzy.exe`):

1. **Langsung di HP Android (Tanpa PC / Tanpa Rebuild!)**:
   - Install `FeedingFrenzy-Android9Plus-Oprek-debug.apk` di HP Android 9+.
   - Buka game → ketuk **⚡ OPREK LAB** → ketuk **📂 IMPORT `Feeding_Frenzy_Win_Preinstalled_EN.zip` / `main.pak`** → pilih file `.zip` hasil download. Sprite & SFX asli otomatis aktif!
2. **Lewat Oprek Studio Web UI (Live Preview)**:
   - Drag & drop `Feeding_Frenzy_Win_Preinstalled_EN.zip` ke kotak upload di **Live Preview** → server otomatis mendekripsi `main.pak` (`XOR 0xF7`), mem-bundle ke dalam APK, dan men-sign ulang `FeedingFrenzy-Android9Plus-Oprek-debug.apk`!
3. **Lewat Terminal CLI (`pak_tool.py` / `import-original-zip.sh`)**:
   ```bash
   # Auto-unpack, compose transparent PNG sprites, bundle main.pak & rebuild signed APK:
   ./scripts/import-original-zip.sh path/to/Feeding_Frenzy_Win_Preinstalled_EN.zip

   # Atau bongkar / modifikasi isi main.pak secara manual:
   python3 tools/pak_tool.py inspect path/to/Feeding_Frenzy_Win_Preinstalled_EN.zip
   python3 tools/pak_tool.py unpack  path/to/Feeding_Frenzy_Win_Preinstalled_EN.zip -o unpacked_dump --compose-png
   python3 tools/pak_tool.py pack    unpacked_dump -o custom_main.pak
   ```

---

## 🛠️ Build & Test Commands

```bash
# Jalankan unit & simulation test suite (Java + Kotlin di OpenJDK 21):
bash scripts/run-tests.sh

# Build, 4-byte align, sign (APK Signature Scheme v1 + v2 + v3), & verifikasi APK:
bash scripts/build-apk.sh
```
