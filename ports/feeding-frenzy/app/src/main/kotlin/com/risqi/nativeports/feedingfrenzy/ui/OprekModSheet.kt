package com.risqi.nativeports.feedingfrenzy.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.risqi.nativeports.feedingfrenzy.assets.PakSpriteRepository
import com.risqi.nativeports.feedingfrenzy.audio.AndroidSoundEngine
import com.risqi.nativeports.feedingfrenzy.engine.FrenzyWorld
import com.risqi.nativeports.feedingfrenzy.engine.StageCatalog
import java.util.Locale

/**
 * Interactive "OPREK LAB & PAK INSPECTOR" modal panel for live runtime modding, physics tweaking,
 * stage teleporting (Stages 1..40), and importing original `Feeding_Frenzy_Win_Preinstalled_EN.zip` / `main.pak`.
 */
object OprekModSheet {

    fun show(
        activity: Activity,
        world: FrenzyWorld,
        sprites: PakSpriteRepository,
        sound: AndroidSoundEngine,
        onPickArchiveFile: () -> Unit
    ) {
        val oprek = world.oprek
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val rootScroll = ScrollView(activity).apply {
            setBackgroundColor(0xFF071A30.toInt())
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        rootScroll.addView(box)

        // Header
        box.addView(TextView(activity).apply {
            text = "⚡ FEEDING FRENZY — OPREK LAB & PAK INSPECTOR"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFB703.toInt())
        })
        box.addView(TextView(activity).apply {
            text = "Ubah physics, cheat, stage 1–40, atau muat file asli Feeding_Frenzy_Win_Preinstalled_EN.zip secara live."
            textSize = 12.5f
            setTextColor(0xFFBAE6FD.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })

        // Preset row
        val presetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun makeActionButton(label: String, bgColor: Int, onClick: () -> Unit): Button {
            return Button(activity).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(bgColor)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(3), dp(4), dp(3), dp(6))
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        var dialogRef: AlertDialog? = null

        presetRow.addView(makeActionButton("Vanilla 2004", 0xFF334155.toInt()) {
            oprek.resetToVanilla()
            dialogRef?.dismiss()
            show(activity, world, sprites, sound, onPickArchiveFile)
        })
        presetRow.addView(makeActionButton("🔥 Sultan Oprek", 0xFF059669.toInt()) {
            oprek.applySultanPreset()
            dialogRef?.dismiss()
            show(activity, world, sprites, sound, onPickArchiveFile)
        })
        presetRow.addView(makeActionButton("🐋 Titan Orca", 0xFF7C3AED.toInt()) {
            oprek.applyTitanOrcaPreset()
            dialogRef?.dismiss()
            show(activity, world, sprites, sound, onPickArchiveFile)
        })
        box.addView(presetRow)

        // Quick Instant Actions Row
        val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(makeActionButton("⬆ Instant Grow (+Size)", 0xFF0284C7.toInt()) {
            world.forceInstantGrowOprek()
        })
        actionRow.addView(makeActionButton("❤ +3 Extra Lives", 0xFF0D9488.toInt()) {
            synchronized(world) { world.lives = (world.lives + 3).coerceAtMost(99) }
        })
        actionRow.addView(makeActionButton(if (sound.muted) "🔇 Sound: OFF" else "🔊 Sound: ON", 0xFF475569.toInt()) {
            sound.muted = !sound.muted
            dialogRef?.dismiss()
            show(activity, world, sprites, sound, onPickArchiveFile)
        })
        box.addView(actionRow)

        fun addToggle(title: String, initial: Boolean, onChanged: (Boolean) -> Unit) {
            box.addView(CheckBox(activity).apply {
                text = title
                isChecked = initial
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnCheckedChangeListener { _, checked -> onChanged(checked) }
            })
        }

        addToggle("🛡️ God Mode (Kebal dimakan ikan besar, ubur-ubur & ranjau)", oprek.godMode) {
            oprek.godMode = it
        }
        addToggle("🦈 Apex Eat-Anything (Bisa langsung lahap Hiu Putih & Barracuda!)", oprek.eatAnythingApex) {
            oprek.eatAnythingApex = it
        }
        addToggle("🔥 Always Feeding Frenzy x8 Multiplier", oprek.alwaysFrenzy) {
            oprek.alwaysFrenzy = it
        }
        addToggle("⚡ Infinite Boost & Zero Cooldown Inhale", oprek.infiniteDash) {
            oprek.infiniteDash = it
        }
        addToggle("👆 Direct Touch Follow (Matikan untuk Virtual Analog Stick)", oprek.useDirectTouchControl) {
            oprek.useDirectTouchControl = it
        }
        addToggle("🎯 Tampilkan Hitbox, AI Vector & FPS Profiler", oprek.showHitboxesAndAiVectors) {
            oprek.showHitboxesAndAiVectors = it
        }
        addToggle("🖼️ Gunakan Sprite Asli main.pak (jika sudah di-import)", oprek.preferOriginalPakSprites) {
            oprek.preferOriginalPakSprites = it
        }

        fun addSlider(
            labelPrefix: String,
            currentVal: Float,
            minVal: Float,
            maxVal: Float,
            unit: String,
            onValue: (Float) -> Unit
        ) {
            val labelView = TextView(activity).apply {
                text = String.format(Locale.US, "%s: %.2f%s", labelPrefix, currentVal, unit)
                setTextColor(0xFF93C5FD.toInt())
                textSize = 13f
                setPadding(0, dp(8), 0, dp(2))
            }
            val bar = SeekBar(activity).apply {
                max = 100
                progress = (((currentVal - minVal) / (maxVal - minVal)) * 100f).toInt().coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = minVal + (p / 100f) * (maxVal - minVal)
                        labelView.text = String.format(Locale.US, "%s: %.2f%s", labelPrefix, v, unit)
                        onValue(v)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            box.addView(labelView)
            box.addView(bar)
        }

        addSlider("Kecepatan Renang (Speed Multiplier)", oprek.speedMultiplier, 0.5f, 3.0f, "x") {
            oprek.speedMultiplier = it
        }
        addSlider("Ukuran Ikan Manual (0.00 = Auto Growth)", oprek.scaleOverride, 0.0f, 3.2f, "x") {
            oprek.scaleOverride = if (it < 0.15f) 0f else it
        }
        addSlider("Radius Food Magnet Vacuum", oprek.foodMagnetRadius, 0f, 450f, " px") {
            oprek.foodMagnetRadius = it
        }
        addSlider("Kepadatan Spawn Ikan Laut", oprek.spawnRateMultiplier, 0.5f, 2.8f, "x") {
            oprek.spawnRateMultiplier = it
        }

        // Stage 1..40 Teleporter Slider
        var targetStage = world.currentStage.stageNumber
        val stageLabel = TextView(activity).apply {
            text = "🌊 Lompat Stage: ${StageCatalog.getStage(targetStage).chapterTitle} — Stage $targetStage"
            setTextColor(0xFFFDE047.toInt())
            textSize = 13.5f
            setPadding(0, dp(12), 0, dp(4))
        }
        val stageSeek = SeekBar(activity).apply {
            max = StageCatalog.TOTAL_STAGES - 1
            progress = targetStage - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    targetStage = p + 1
                    val def = StageCatalog.getStage(targetStage)
                    stageLabel.text = "🌊 Lompat Stage: ${def.chapterTitle} — ${def.stageTitle} (${def.heroSpecies.displayName})"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(stageLabel)
        box.addView(stageSeek)
        box.addView(Button(activity).apply {
            text = "🚀 TELEPORT KE STAGE TERPILIH SEKARANG"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF0284C7.toInt())
            setOnClickListener {
                world.jumpToStage(targetStage)
                dialogRef?.dismiss()
            }
        })

        // Original PopCap ZIP / main.pak Importer & PE32 Inspector Section
        box.addView(TextView(activity).apply {
            text = "\n📦 POPCAP ORIGINAL ASSET & PE32 BINARY INSPECTOR"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF69F0AE.toInt())
        })
        box.addView(Button(activity).apply {
            text = "📂 IMPORT Feeding_Frenzy_Win_Preinstalled_EN.zip / main.pak"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFFD97706.toInt())
            setOnClickListener {
                dialogRef?.dismiss()
                onPickArchiveFile()
            }
        })
        box.addView(TextView(activity).apply {
            text = sprites.lastInspectionReport
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFCBD5E1.toInt())
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })

        dialogRef = AlertDialog.Builder(activity)
            .setView(rootScroll)
            .setPositiveButton("TUTUP & MAIN") { d, _ -> d.dismiss() }
            .create()
        dialogRef.show()
    }
}
