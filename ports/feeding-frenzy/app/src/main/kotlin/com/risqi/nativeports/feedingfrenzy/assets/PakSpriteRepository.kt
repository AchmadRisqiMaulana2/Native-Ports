package com.risqi.nativeports.feedingfrenzy.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.risqi.nativeports.feedingfrenzy.engine.SpeciesType
import com.risqi.nativeports.feedingfrenzy.pak.AlphaMaskComposer
import com.risqi.nativeports.feedingfrenzy.pak.ExeBinaryInspector
import com.risqi.nativeports.feedingfrenzy.pak.PakArchiveReader
import com.risqi.nativeports.feedingfrenzy.pak.PakEntry
import java.io.File
import java.util.EnumMap
import java.util.Locale

/**
 * Loads, alpha-composes, slices, and caches original PopCap sprites from either:
 *  - `assets/main.pak` bundled at APK build time (`./scripts/import-original-zip.sh`), OR
 *  - a user-selected `Feeding_Frenzy_Win_Preinstalled_EN.zip` / `main.pak` via Android SAF!
 */
class PakSpriteRepository(private val context: Context) {

    data class SpriteStrip(
        val sourcePath: String,
        val frames: List<Bitmap>
    )

    private val speciesSprites = EnumMap<SpeciesType, SpriteStrip>(SpeciesType::class.java)

    @Volatile var activeArchive: PakArchiveReader? = null
        private set

    @Volatile var lastInspectionReport: String =
        "Original main.pak belum dimuat.\n" +
            "Game berjalan penuh menggunakan Native Vector Marine Renderer + Synth Audio.\n" +
            "Klik 'IMPORT ZIP / MAIN.PAK' di bawah untuk memuat Feeding_Frenzy_Win_Preinstalled_EN.zip!"
        private set

    private val persistedImportFile: File
        get() = File(context.filesDir, "imported_feeding_frenzy_archive.bin")

    fun tryAutoLoadOnStartup(): PakArchiveReader? {
        // 1. User-imported archive in internal app storage takes priority
        if (persistedImportFile.exists() && persistedImportFile.length() > 16L) {
            runCatching {
                return loadFromBytes(persistedImportFile.readBytes(), "Imported:${persistedImportFile.name}")
            }
        }
        // 2. Check bundled APK assets/main.pak
        val assetList = runCatching { context.assets.list("")?.toList().orEmpty() }.getOrDefault(emptyList())
        val bundledName = assetList.firstOrNull {
            it.equals("main.pak", ignoreCase = true) ||
                it.endsWith(".pak", ignoreCase = true) ||
                it.contains("feeding_frenzy", ignoreCase = true)
        }
        if (bundledName != null) {
            runCatching {
                val bytes = context.assets.open(bundledName).use { PakArchiveReader.readAllBytes(it) }
                return loadFromBytes(bytes, "APK-Bundled:$bundledName")
            }
        }
        return null
    }

    fun importFromUri(uri: Uri): Result<PakArchiveReader> {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use {
                PakArchiveReader.readAllBytes(it)
            } ?: error("Gagal membaca stream file dari penyimpanan Android")

            val reader = loadFromBytes(rawBytes, uri.lastPathSegment ?: "SAF-Import")
            persistedImportFile.writeBytes(rawBytes)
            reader
        }
    }

    fun loadFromBytes(rawBytes: ByteArray, label: String): PakArchiveReader {
        val reader = PakArchiveReader.openBytes(rawBytes, label)
        activeArchive = reader
        decodeSpeciesSprites(reader)
        lastInspectionReport = buildDiagnosticReport(reader, speciesSprites.size)
        return reader
    }

    fun getSpriteFrame(species: SpeciesType, tailPhase: Float): Bitmap? {
        val strip = speciesSprites[species] ?: return null
        if (strip.frames.isEmpty()) return null
        val idx = ((tailPhase * 2.2f).toInt().coerceAtLeast(0)) % strip.frames.size
        return strip.frames[idx]
    }

    val loadedSpeciesCount: Int
        get() = speciesSprites.size

    private fun decodeSpeciesSprites(reader: PakArchiveReader) {
        speciesSprites.clear()
        val imageEntries = reader.entries.filter { it.category == PakEntry.CAT_SPRITE }

        for (species in SpeciesType.entries) {
            val match = findBestSwimSprite(imageEntries, species) ?: continue
            val bitmap = decodePopCapBitmap(reader, match) ?: continue
            val sliced = sliceHorizontalStrip(bitmap)
            speciesSprites[species] = SpriteStrip(match.normalizedPath, sliced)
        }
    }

    private fun findBestSwimSprite(entries: List<PakEntry>, species: SpeciesType): PakEntry? {
        for (hint in species.pakStemHints) {
            // Prefer swim animation strips first, then any matching non-UI sprite
            val swimHit = entries.firstOrNull { e ->
                val low = e.normalizedPath.lowercase(Locale.US)
                low.contains(hint) && low.contains("swim") && !low.contains("icon")
            }
            if (swimHit != null) return swimHit

            val genericHit = entries.firstOrNull { e ->
                val low = e.normalizedPath.lowercase(Locale.US)
                low.contains(hint) && !low.contains("menu") && !low.contains("button")
            }
            if (genericHit != null) return genericHit
        }
        return null
    }

    private fun decodePopCapBitmap(reader: PakArchiveReader, spriteEntry: PakEntry): Bitmap? {
        val rgbBytes = reader.readEntryBytes(spriteEntry)
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val rgbBmp = BitmapFactory.decodeByteArray(rgbBytes, 0, rgbBytes.size, opts) ?: return null
        val w = rgbBmp.width
        val h = rgbBmp.height
        if (w <= 0 || h <= 0) return null

        val rgbPixels = IntArray(w * h)
        rgbBmp.getPixels(rgbPixels, 0, w, 0, 0, w, h)

        val alphaEntry = reader.findAlphaMaskFor(spriteEntry)
        val composedPixels = if (alphaEntry != null) {
            val alphaBytes = reader.readEntryBytes(alphaEntry)
            val alphaBmp = BitmapFactory.decodeByteArray(alphaBytes, 0, alphaBytes.size, opts)
            if (alphaBmp != null) {
                val aw = alphaBmp.width
                val ah = alphaBmp.height
                val alphaPixels = IntArray(aw * ah)
                alphaBmp.getPixels(alphaPixels, 0, aw, 0, 0, aw, ah)
                alphaBmp.recycle()
                AlphaMaskComposer.composeArgb(rgbPixels, w, h, alphaPixels, aw, ah)
            } else {
                rgbPixels
            }
        } else if (rgbBmp.hasAlpha()) {
            rgbPixels
        } else {
            // Fallback colorkey for unmasked black-background sprites
            AlphaMaskComposer.applyColorKey(rgbPixels, 0x000000, 10)
        }

        rgbBmp.recycle()
        return Bitmap.createBitmap(composedPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sliceHorizontalStrip(sheet: Bitmap): List<Bitmap> {
        val w = sheet.width
        val h = sheet.height
        if (h <= 0 || w < h * 2) {
            return listOf(sheet)
        }
        val approxCols = (w / h).coerceIn(1, 16)
        val frameW = w / approxCols
        if (frameW < 8) return listOf(sheet)

        val out = ArrayList<Bitmap>(approxCols)
        for (c in 0 until approxCols) {
            out.add(Bitmap.createBitmap(sheet, c * frameW, 0, frameW, h))
        }
        return out
    }

    private fun buildDiagnosticReport(reader: PakArchiveReader, mappedSprites: Int): String {
        val counts = reader.countByCategory()
        val sb = StringBuilder()
        sb.append("PAK AKTIF: ").append(reader.sourceLabel).append('\n')
        sb.append("Total Entries: ").append(reader.size())
            .append(" | XOR 0xF7: ").append(if (reader.wasXorEncrypted()) "YA" else "PLAIN")
            .append(" | Mapped Species Sprites: ").append(mappedSprites).append('\n')
        sb.append("Kategori: ").append(counts).append('\n')

        if (reader.hasCompanionExe()) {
            val exeReport = ExeBinaryInspector.inspect(reader.companionExeBytes)
            sb.append("\n[EXE PE32 Inspector — ").append(reader.companionExeName).append("]\n")
            sb.append(exeReport.toSummaryText())
        }

        sb.append("\nContoh Entry di main.pak:\n")
        reader.entries.take(12).forEach { e ->
            sb.append(" • ").append(e.normalizedPath).append(" (").append(e.size).append(" B)\n")
        }
        return sb.toString()
    }
}
