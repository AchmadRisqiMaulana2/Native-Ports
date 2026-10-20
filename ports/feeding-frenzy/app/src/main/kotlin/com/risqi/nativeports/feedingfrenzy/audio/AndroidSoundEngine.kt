package com.risqi.nativeports.feedingfrenzy.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.risqi.nativeports.feedingfrenzy.audio.ProceduralSfxGenerator.SfxKind
import com.risqi.nativeports.feedingfrenzy.pak.PakArchiveReader
import com.risqi.nativeports.feedingfrenzy.pak.PakEntry
import java.io.File
import java.util.EnumMap
import java.util.Locale

/**
 * Low-latency Android 9+ (API 28+) game audio engine backed by [SoundPool] + haptic feedback.
 * Starts immediately with synthesized underwater WAV SFX and hot-swaps any matching audio entries
 * discovered inside an imported PopCap `main.pak` / `Feeding_Frenzy_Win_Preinstalled_EN.zip`.
 */
class AndroidSoundEngine(private val context: Context) {

    private val soundIds = EnumMap<SfxKind, Int>(SfxKind::class.java)
    private var soundPool: SoundPool? = null
    @Volatile var muted: Boolean = false
    @Volatile var pakAudioOverridesLoaded: Int = 0
        private set

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attrs)
            .build()
        soundPool = pool

        val sfxDir = File(context.cacheDir, "synth_sfx").apply { mkdirs() }
        for (kind in SfxKind.entries) {
            val wavFile = File(sfxDir, "${kind.name.lowercase(Locale.US)}.wav")
            if (!wavFile.exists() || wavFile.length() < 64L) {
                wavFile.writeBytes(ProceduralSfxGenerator.buildWav(kind))
            }
            soundIds[kind] = pool.load(wavFile.absolutePath, 1)
        }
    }

    /**
     * Scans `sounds/` inside a loaded PopCap [PakArchiveReader] and hot-replaces synthesized SFX
     * with authentic original PopCap `.ogg` / `.wav` sound effects!
     */
    fun applyPakAudioOverrides(pak: PakArchiveReader): Int {
        val pool = soundPool ?: return 0
        val outDir = File(context.cacheDir, "pak_sfx").apply { mkdirs() }
        var replaced = 0

        val mappingHints: Map<SfxKind, List<String>> = mapOf(
            SfxKind.CHOMP_SMALL to listOf("bite", "chomp1", "chomp", "eat1", "eat"),
            SfxKind.CHOMP_BIG to listOf("gulp", "bigbite", "chomp2", "chomp3", "sharkbite"),
            SfxKind.GROW_UP to listOf("grow", "sizeup", "levelup"),
            SfxKind.FRENZY_FANFARE to listOf("frenzy", "feedingfrenzy", "doublefrenzy"),
            SfxKind.BUBBLE_POP to listOf("bubble", "pop", "powerup"),
            SfxKind.PEARL_CHIME to listOf("pearl", "oyster", "star", "bonus"),
            SfxKind.MINE_EXPLODE to listOf("explode", "explosion", "mine", "bomb"),
            SfxKind.PLAYER_HURT to listOf("die", "hurt", "shock", "jelly", "oops"),
            SfxKind.STAGE_CLEAR to listOf("stageclear", "win", "complete", "fanfare"),
            SfxKind.DASH_WHOOSH to listOf("dash", "speed", "suck", "inhale", "whoosh")
        )

        val audioEntries = pak.entries.filter {
            it.category == PakEntry.CAT_AUDIO &&
                (it.normalizedPath.endsWith(".ogg", true) ||
                    it.normalizedPath.endsWith(".wav", true) ||
                    it.normalizedPath.endsWith(".mp3", true))
        }

        for ((kind, hints) in mappingHints) {
            val match = audioEntries.firstOrNull { entry ->
                val stem = entry.fileName.substringBeforeLast('.').lowercase(Locale.US)
                hints.any { h -> stem.contains(h) }
            } ?: continue

            val ext = match.fileName.substringAfterLast('.', "ogg")
            val extracted = File(outDir, "pak_${kind.name.lowercase(Locale.US)}.$ext")
            extracted.writeBytes(pak.readEntryBytes(match))
            val newId = pool.load(extracted.absolutePath, 1)
            if (newId > 0) {
                soundIds[kind] = newId
                replaced++
            }
        }
        pakAudioOverridesLoaded = replaced
        return replaced
    }

    fun play(kind: SfxKind) {
        if (muted) return
        val pool = soundPool ?: return
        val id = soundIds[kind] ?: return
        val rate = if (kind == SfxKind.CHOMP_SMALL) (0.94f + (System.nanoTime() % 13) * 0.01f) else 1.0f
        pool.play(id, 0.85f, 0.85f, 1, 0, rate)

        when (kind) {
            SfxKind.GROW_UP, SfxKind.MINE_EXPLODE, SfxKind.PLAYER_HURT -> pulseHaptic(28L, 150)
            SfxKind.FRENZY_FANFARE -> pulseHaptic(18L, 110)
            else -> Unit
        }
    }

    @Suppress("DEPRECATION")
    private fun pulseHaptic(durationMs: Long, amplitude: Int) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            }
        } catch (_: Throwable) {
            // Ignore devices without vibrator hardware
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
