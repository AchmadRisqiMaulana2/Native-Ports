package com.risqi.nativeports.feedingfrenzy.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.risqi.nativeports.feedingfrenzy.assets.PakSpriteRepository
import com.risqi.nativeports.feedingfrenzy.engine.BonusItem
import com.risqi.nativeports.feedingfrenzy.engine.FishEntity
import com.risqi.nativeports.feedingfrenzy.engine.FrenzyWorld
import com.risqi.nativeports.feedingfrenzy.engine.SpeciesType
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hardware-accelerated 60/120Hz ocean renderer for Feeding Frenzy Native Port.
 *
 * Automatically blends between authentic PopCap `main.pak` sprite sheets (when imported) and
 * crisp animated cartoon marine graphics, plus top arcade HUD, touch controls, and Oprek telemetry.
 */
class OceanRenderer(
    private val world: FrenzyWorld,
    private val sprites: PakSpriteRepository,
    private val controller: TouchController
) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val path = Path()
    private val tempRect = RectF()

    private var cachedStageNum = -1
    private var waterGradient: LinearGradient? = null
    @Volatile var currentFps: Int = 60

    fun render(canvas: Canvas, screenW: Int, screenH: Int) {
        if (screenW <= 0 || screenH <= 0) return

        // Compute aspect-preserving scale into logical 1280x720 world with ocean bleed on ultrawide phones
        val sx = screenW.toFloat() / FrenzyWorld.WORLD_WIDTH
        val sy = screenH.toFloat() / FrenzyWorld.WORLD_HEIGHT
        val scale = min(sx, sy)
        val offsetX = (screenW - FrenzyWorld.WORLD_WIDTH * scale) * 0.5f
        val offsetY = (screenH - FrenzyWorld.WORLD_HEIGHT * scale) * 0.5f
        controller.viewportScale = scale
        controller.viewportOffsetX = offsetX
        controller.viewportOffsetY = offsetY

        // Fill full ultrawide screen with deep water color so notch/cutout edges blend seamlessly
        canvas.drawColor(world.currentStage.waterBottomColor)

        canvas.save()
        val shakeX = if (world.screenShake > 0f) (sin(world.worldTimeSeconds * 55.0) * world.screenShake * 18f).toFloat() else 0f
        val shakeY = if (world.screenShake > 0f) (cos(world.worldTimeSeconds * 48.0) * world.screenShake * 14f).toFloat() else 0f
        canvas.translate(offsetX + shakeX, offsetY + shakeY)
        canvas.scale(scale, scale)

        synchronized(world) {
            drawOceanBackdrop(canvas)
            drawSeabedAndKelp(canvas)
            drawBonusesAndHazards(canvas)
            for (npc in world.npcFish) {
                drawFish(canvas, npc)
            }
            world.player?.let { drawFish(canvas, it) }
            if (world.oprek.showHitboxesAndAiVectors) {
                drawOprekDebugOverlay(canvas)
            }
            drawPopups(canvas)
            drawTopArcadeHud(canvas)
            if (world.state == FrenzyWorld.RunState.PLAYING) {
                drawTouchControls(canvas)
            } else {
                drawModalStateScreen(canvas)
            }
        }

        canvas.restore()
    }

    private fun drawOceanBackdrop(canvas: Canvas) {
        val st = world.currentStage
        if (st.stageNumber != cachedStageNum || waterGradient == null) {
            cachedStageNum = st.stageNumber
            waterGradient = LinearGradient(
                0f, 0f, 0f, FrenzyWorld.WORLD_HEIGHT,
                st.waterTopColor, st.waterBottomColor, Shader.TileMode.CLAMP
            )
        }
        paint.style = Paint.Style.FILL
        paint.shader = waterGradient
        canvas.drawRect(0f, 0f, FrenzyWorld.WORLD_WIDTH, FrenzyWorld.WORLD_HEIGHT, paint)
        paint.shader = null

        // Sunlit water surface shimmer + caustic rays
        val t = world.worldTimeSeconds
        for (i in 0..4) {
            val baseX = 150f + i * 245f + sin(t * 0.55f + i) * 38f
            path.reset()
            path.moveTo(baseX - 36f, 52f)
            path.lineTo(baseX + 42f, 52f)
            path.lineTo(baseX + 135f, FrenzyWorld.WORLD_HEIGHT)
            path.lineTo(baseX - 95f, FrenzyWorld.WORLD_HEIGHT)
            path.close()
            paint.color = Color.argb(18, 175, 240, 255)
            canvas.drawPath(path, paint)
        }

        // Ambient rising micro-bubbles
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = Color.argb(72, 205, 248, 255)
        for (b in 0..15) {
            val bx = ((b * 97 + 40) % 1240).toFloat() + sin(t * 1.4f + b) * 14f
            val by = 690f - ((t * (28f + (b % 4) * 9f) + b * 67f) % 640f)
            canvas.drawCircle(bx, by, 3.5f + (b % 3) * 2.2f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawSeabedAndKelp(canvas: Canvas) {
        val t = world.worldTimeSeconds
        // Distant coral reef silhouette
        paint.color = Color.argb(95, 6, 20, 46)
        for (i in 0..7) {
            val cx = i * 185f
            canvas.drawOval(cx - 90f, 618f - (i % 3) * 24f, cx + 125f, 735f, paint)
        }

        // Swaying kelp fronds
        paint.strokeWidth = 9f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        for (k in 0..8) {
            val kx = 75f + k * 148f
            val sway = sin(t * 1.9f + k * 0.8f) * 22f
            paint.color = if (k % 2 == 0) 0xAA1B6B5A.toInt() else 0xAA25855A.toInt()
            path.reset()
            path.moveTo(kx, 690f)
            path.quadTo(kx + sway, 620f, kx - sway * 0.6f, 548f - (k % 3) * 26f)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL

        // Warm sandy reef floor
        paint.color = 0xFF8D6E53.toInt()
        path.reset()
        path.moveTo(0f, 668f)
        path.quadTo(320f, 650f, 640f, 668f)
        path.quadTo(960f, 684f, 1280f, 662f)
        path.lineTo(1280f, 720f)
        path.lineTo(0f, 720f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBonusesAndHazards(canvas: Canvas) {
        for (item in world.bonuses) {
            when (item.kind) {
                BonusItem.Kind.NAVAL_MINE -> {
                    // Anchor chain to seabed
                    paint.color = 0x88334155.toInt()
                    paint.strokeWidth = 3f
                    canvas.drawLine(item.x, item.y + item.radius, item.x, 675f, paint)
                    // Spikes + iron sphere + warning blink
                    paint.color = 0xFF334155.toInt()
                    for (a in 0 until 8) {
                        val ang = a * (Math.PI / 4.0) + item.phase * 0.25
                        val sx = item.x + cos(ang).toFloat() * (item.radius + 9f)
                        val sy = item.y + sin(ang).toFloat() * (item.radius + 9f)
                        canvas.drawLine(item.x, item.y, sx, sy, paint)
                        canvas.drawCircle(sx, sy, 3.5f, paint)
                    }
                    paint.color = 0xFF1E293B.toInt()
                    canvas.drawCircle(item.x, item.y, item.radius, paint)
                    paint.color = if ((item.phase.toInt() % 2) == 0) 0xFFFF3B30.toInt() else 0xFF7F1D1D.toInt()
                    canvas.drawCircle(item.x, item.y, 6.5f, paint)
                }

                BonusItem.Kind.OYSTER_PEARL_WHITE, BonusItem.Kind.OYSTER_PEARL_BLACK -> {
                    // Bottom shell
                    paint.color = 0xFF6D4C41.toInt()
                    tempRect.set(item.x - 30f, item.y - 10f, item.x + 30f, item.y + 18f)
                    canvas.drawOval(tempRect, paint)
                    if (item.oysterOpen) {
                        // Top open shell + glowing pearl inside!
                        paint.color = 0xFF8D6E63.toInt()
                        tempRect.set(item.x - 28f, item.y - 34f, item.x + 28f, item.y - 6f)
                        canvas.drawOval(tempRect, paint)
                        paint.color = if (item.kind == BonusItem.Kind.OYSTER_PEARL_BLACK) 0xFF311B92.toInt() else 0xFFFFFDE7.toInt()
                        canvas.drawCircle(item.x, item.y - 4f, 10.5f, paint)
                        paint.color = 0xAAFFFFFF.toInt()
                        canvas.drawCircle(item.x - 3f, item.y - 7f, 3.5f, paint)
                    } else {
                        paint.color = 0xFF5D4037.toInt()
                        tempRect.set(item.x - 30f, item.y - 20f, item.x + 30f, item.y + 4f)
                        canvas.drawOval(tempRect, paint)
                    }
                }

                else -> {
                    // Power-up bubble with icon label
                    paint.color = Color.argb(95, 160, 244, 255)
                    canvas.drawCircle(item.x, item.y, item.radius, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.5f
                    paint.color = Color.WHITE
                    canvas.drawCircle(item.x, item.y, item.radius, paint)
                    paint.style = Paint.Style.FILL

                    val label = when (item.kind) {
                        BonusItem.Kind.FRENZY_BUBBLE -> "FRZ"
                        BonusItem.Kind.SPEED_BUBBLE -> "SPD"
                        BonusItem.Kind.FREEZE_BUBBLE -> "ICE"
                        BonusItem.Kind.SHIELD_BUBBLE -> "SHD"
                        BonusItem.Kind.EXTRA_LIFE_BUBBLE -> "1UP"
                        else -> "★"
                    }
                    textPaint.color = 0xFFFFEA00.toInt()
                    textPaint.textSize = 13f
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(label, item.x, item.y + 4.5f, textPaint)
                }
            }
        }
    }

    private fun drawFish(canvas: Canvas, fish: FishEntity) {
        // Blink alpha when invulnerable
        if (fish.isPlayer && fish.invulnerableTimer > 0f && ((world.worldTimeSeconds * 14f).toInt() % 2 == 0)) {
            return
        }

        // Suction aura or Bubble Shield around player
        if (fish.isPlayer) {
            if (world.suctionActive || world.oprek.foodMagnetRadius > 5f) {
                val r = maxOf(world.oprek.foodMagnetRadius, if (world.suctionActive) 285f else 0f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = Color.argb(55, 114, 239, 221)
                canvas.drawCircle(fish.x, fish.y, r, paint)
                paint.style = Paint.Style.FILL
            }
            if (world.hasBubbleShield || world.oprek.godMode) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3.5f
                paint.color = if (world.oprek.godMode) 0xAAFFD54F.toInt() else 0xBB64DFDF.toInt()
                canvas.drawCircle(fish.x, fish.y, fish.radius * 1.28f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // 1. Render authentic decoded PopCap main.pak sprite frame if loaded & enabled!
        if (world.oprek.preferOriginalPakSprites) {
            val pakFrame: Bitmap? = sprites.getSpriteFrame(fish.species, fish.tailPhase)
            if (pakFrame != null) {
                canvas.save()
                canvas.translate(fish.x, fish.y)
                if (!fish.facingRight) {
                    canvas.scale(-1f, 1f)
                }
                val halfW = fish.radius * 1.35f
                val halfH = fish.radius * 1.05f
                tempRect.set(-halfW, -halfH, halfW, halfH)
                paint.color = Color.WHITE
                canvas.drawBitmap(pakFrame, null, tempRect, paint)
                canvas.restore()
                return
            }
        }

        // 2. Crisp Native Vector PopCap Marine Creature Renderer
        if (fish.species == SpeciesType.JELLYFISH) {
            drawJellyfish(canvas, fish)
            return
        }

        canvas.save()
        canvas.translate(fish.x, fish.y)
        if (!fish.facingRight) {
            canvas.scale(-1f, 1f)
        }
        val chompScale = if (fish.chompTimer > 0f) 1.16f else 1.0f
        canvas.scale(chompScale, chompScale)

        val r = fish.radius
        val wag = sin(fish.tailPhase) * (r * 0.22f)

        // Caudal tail fin with live swimming wag
        paint.color = fish.species.accentColor
        path.reset()
        path.moveTo(-r * 0.72f, 0f)
        path.lineTo(-r * 1.34f, -r * 0.62f + wag)
        path.lineTo(-r * 1.06f, wag * 0.35f)
        path.lineTo(-r * 1.34f, r * 0.62f + wag)
        path.close()
        canvas.drawPath(path, paint)

        // Dorsal & ventral fins
        path.reset()
        path.moveTo(-r * 0.32f, -r * 0.58f)
        path.lineTo(r * 0.05f, -r * 1.06f)
        path.lineTo(r * 0.38f, -r * 0.55f)
        path.close()
        canvas.drawPath(path, paint)

        // Eddie the Anglerfish glowing bioluminescent lantern!
        if (fish.species == SpeciesType.ANGLERFISH) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFF80FF72.toInt()
            path.reset()
            path.moveTo(r * 0.15f, -r * 0.65f)
            path.quadTo(r * 0.85f, -r * 1.25f, r * 1.05f, -r * 0.55f)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(r * 1.06f, -r * 0.52f, r * 0.16f, paint)
        }

        // Main glossy fish body
        paint.color = fish.species.primaryColor
        tempRect.set(-r * 0.92f, -r * 0.68f, r * 0.95f, r * 0.68f)
        canvas.drawOval(tempRect, paint)

        // Accent stripes / Orca belly patch
        paint.color = fish.species.accentColor
        if (fish.species == SpeciesType.ORCA || fish.species == SpeciesType.GREAT_WHITE_SHARK) {
            tempRect.set(-r * 0.55f, r * 0.08f, r * 0.78f, r * 0.64f)
            canvas.drawOval(tempRect, paint)
        } else {
            paint.strokeWidth = maxOf(2.5f, r * 0.11f)
            paint.style = Paint.Style.STROKE
            canvas.drawLine(-r * 0.18f, -r * 0.56f, -r * 0.28f, r * 0.56f, paint)
            canvas.drawLine(r * 0.12f, -r * 0.54f, r * 0.04f, r * 0.54f, paint)
            paint.style = Paint.Style.FILL
        }

        // Cartoon eye + pupil + specular highlight
        val eyeX = r * 0.48f
        val eyeY = -r * 0.18f
        val eyeR = maxOf(4.2f, r * 0.21f)
        paint.color = Color.WHITE
        canvas.drawCircle(eyeX, eyeY, eyeR, paint)
        paint.color = 0xFF0F172A.toInt()
        canvas.drawCircle(eyeX + eyeR * 0.24f, eyeY, eyeR * 0.54f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(eyeX + eyeR * 0.36f, eyeY - eyeR * 0.22f, eyeR * 0.20f, paint)

        // Open mouth chomp or predator teeth
        paint.color = 0xFF450A0A.toInt()
        if (fish.chompTimer > 0f || fish.species == SpeciesType.GREAT_WHITE_SHARK || fish.species == SpeciesType.BARRACUDA) {
            tempRect.set(r * 0.45f, r * 0.08f, r * 0.94f, r * 0.42f)
            canvas.drawOval(tempRect, paint)
        }

        // Subtle green crown dot above player fish for instant visibility in hectic swarms
        if (fish.isPlayer) {
            paint.color = 0xFF69F0AE.toInt()
            canvas.drawCircle(0f, -r * 1.18f, 4.5f, paint)
        }

        canvas.restore()
    }

    private fun drawJellyfish(canvas: Canvas, jelly: FishEntity) {
        val r = jelly.radius
        val pulse = 1.0f + sin(jelly.tailPhase * 1.6f) * 0.09f
        paint.color = jelly.species.primaryColor
        tempRect.set(jelly.x - r * pulse, jelly.y - r * 0.85f, jelly.x + r * pulse, jelly.y + r * 0.35f)
        canvas.drawArc(tempRect, 180f, 180f, true, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = jelly.species.accentColor
        for (t in -2..2) {
            val tx = jelly.x + t * (r * 0.34f)
            val wave = sin(jelly.tailPhase * 2f + t) * 8f
            canvas.drawLine(tx, jelly.y + 2f, tx + wave, jelly.y + r * 1.15f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawTopArcadeHud(canvas: Canvas) {
        // Classic PopCap translucent top menu bar
        paint.color = Color.argb(178, 5, 24, 52)
        canvas.drawRect(0f, 0f, FrenzyWorld.WORLD_WIDTH, 62f, paint)
        paint.color = 0x5538BDF8
        canvas.drawLine(0f, 62f, FrenzyWorld.WORLD_WIDTH, 62f, paint)

        // Left: Score, Stage, Lives
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 21f
        textPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("SCORE ${world.score}", 18f, 27f, textPaint)

        textPaint.textSize = 14f
        textPaint.color = 0xFFBAE6FD.toInt()
        val modeInfo = if (world.mode == FrenzyWorld.GameMode.TIME_ATTACK) {
            String.format(Locale.US, "⏱ %.1fs", world.timeAttackRemaining)
        } else {
            "❤ x${world.lives}  •  BANK ${world.totalFoodBank}"
        }
        canvas.drawText("${world.currentStage.stageTitle}  •  $modeInfo", 18f, 49f, textPaint)

        // Center: Classic MENU / GROWTH BAR + Edible Species Preview
        val barLeft = 455f
        val barWidth = 340f
        textPaint.textSize = 13f
        textPaint.color = Color.WHITE
        val tierLabel = arrayOf("SMALL", "MEDIUM", "LARGE")[world.growthTierIndex.coerceIn(0, 2)]
        canvas.drawText(
            "GROWTH [$tierLabel]  ${world.stageFoodEaten}/${world.currentStage.foodGoalToClear}",
            barLeft,
            22f,
            textPaint
        )

        paint.color = 0xFF0F172A.toInt()
        tempRect.set(barLeft, 30f, barLeft + barWidth, 50f)
        canvas.drawRoundRect(tempRect, 10f, 10f, paint)

        val progress = (world.stageFoodEaten.toFloat() / world.currentStage.foodGoalToClear.coerceAtLeast(1)).coerceIn(0f, 1f)
        paint.color = if (world.frenzyMultiplier >= 2) 0xFFFFB703.toInt() else 0xFF38BDF8.toInt()
        tempRect.set(barLeft, 30f, barLeft + barWidth * progress, 50f)
        canvas.drawRoundRect(tempRect, 10f, 10f, paint)

        // Frenzy Multiplier Badge
        if (world.frenzyMultiplier >= 2) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 26f
            textPaint.color = 0xFFFFEA00.toInt()
            canvas.drawText("🔥 FEEDING FRENZY x${world.frenzyMultiplier}!", 915f, 41f, textPaint)
        }

        // Top-Right `⚡ OPREK` & `⏸` buttons
        paint.color = if (world.oprek.godMode || world.oprek.eatAnythingApex) 0xFF059669.toInt() else 0xFF7C3AED.toInt()
        canvas.drawRoundRect(controller.oprekButtonRect, 12f, 12f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 15f
        textPaint.color = Color.WHITE
        canvas.drawText("⚡ OPREK LAB", controller.oprekButtonRect.centerX(), controller.oprekButtonRect.centerY() + 5.5f, textPaint)

        paint.color = 0xFF1E3A5F.toInt()
        canvas.drawRoundRect(controller.pauseButtonRect, 12f, 12f, paint)
        canvas.drawText("❚❚", controller.pauseButtonRect.centerX(), controller.pauseButtonRect.centerY() + 5.5f, textPaint)
    }

    private fun drawTouchControls(canvas: Canvas) {
        // Left Virtual Analog Stick (if joystick mode)
        if (!world.oprek.useDirectTouchControl) {
            val ox = if (controller.stickActive) controller.stickOriginX else 175f
            val oy = if (controller.stickActive) controller.stickOriginY else 565f
            val kx = if (controller.stickActive) controller.stickKnobX else ox
            val ky = if (controller.stickActive) controller.stickKnobY else oy

            paint.color = Color.argb(64, 255, 255, 255)
            canvas.drawCircle(ox, oy, 82f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.argb(120, 125, 211, 252)
            canvas.drawCircle(ox, oy, 82f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(175, 56, 189, 248)
            canvas.drawCircle(kx, ky, 34f, paint)
        }

        // Right `BOOST` + stamina arc button
        paint.color = if (controller.dashPressed) 0xCCFFB703.toInt() else 0x880284C7.toInt()
        canvas.drawCircle(controller.dashCenterX, controller.dashCenterY, controller.dashRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = 0xFFFFEA00.toInt()
        val r = controller.dashRadius + 5f
        tempRect.set(controller.dashCenterX - r, controller.dashCenterY - r, controller.dashCenterX + r, controller.dashCenterY + r)
        canvas.drawArc(tempRect, -90f, 360f * (world.dashEnergy / 100f), false, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = 16f
        canvas.drawText("BOOST", controller.dashCenterX, controller.dashCenterY + 6f, textPaint)

        // `INHALE` skill button
        val ready = world.suctionCooldown <= 0f || world.oprek.infiniteDash
        paint.color = if (ready) 0x9914B8A6.toInt() else 0x55334155.toInt()
        canvas.drawCircle(controller.suctionCenterX, controller.suctionCenterY, controller.suctionRadius, paint)
        textPaint.textSize = 14f
        val inhaleTxt = if (ready) "INHALE" else String.format(Locale.US, "%.1fs", world.suctionCooldown)
        canvas.drawText(inhaleTxt, controller.suctionCenterX, controller.suctionCenterY + 5f, textPaint)
    }

    private fun drawPopups(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 20f
        for (p in world.popups) {
            textPaint.color = p.color
            textPaint.alpha = (p.life.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawText(p.text, p.x, p.y, textPaint)
        }
        textPaint.alpha = 255
    }

    private fun drawOprekDebugOverlay(canvas: Canvas) {
        val p = world.player ?: return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.GREEN
        canvas.drawCircle(p.x, p.y, p.radius * 0.76f, paint)
        canvas.drawLine(p.x, p.y, p.x + p.vx * 0.35f, p.y + p.vy * 0.35f, paint)

        for (npc in world.npcFish) {
            val edible = world.canPlayerEat(npc)
            paint.color = if (edible) 0xAA4ADE80.toInt() else 0xAAEF4444.toInt()
            canvas.drawCircle(npc.x, npc.y, npc.radius * 0.76f, paint)
            canvas.drawLine(npc.x, npc.y, npc.x + npc.vx * 0.25f, npc.y + npc.vy * 0.25f, paint)
        }
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13f
        textPaint.color = 0xFF86EFAC.toInt()
        val pakStatus = sprites.activeArchive?.let { "PAK:${it.size()} files (${sprites.loadedSpeciesCount} sprites)" }
            ?: "PAK: Built-in Vector Mode"
        canvas.drawText(
            "FPS: $currentFps | Entities: ${world.npcFish.size} | PlayerTier: ${p.foodTier} | $pakStatus",
            18f,
            84f,
            textPaint
        )
    }

    private fun drawModalStateScreen(canvas: Canvas) {
        paint.color = Color.argb(145, 3, 15, 34)
        canvas.drawRect(0f, 62f, FrenzyWorld.WORLD_WIDTH, FrenzyWorld.WORLD_HEIGHT, paint)

        // Glass card panel
        paint.color = Color.argb(228, 8, 35, 68)
        tempRect.set(330f, 150f, 950f, 672f)
        canvas.drawRoundRect(tempRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xFF38BDF8.toInt()
        canvas.drawRoundRect(tempRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER

        when (world.state) {
            FrenzyWorld.RunState.TITLE_DEMO -> {
                textPaint.textSize = 52f
                textPaint.color = 0xFFFFB703.toInt()
                canvas.drawText("FEEDING FRENZY", 640f, 232f, textPaint)

                textPaint.textSize = 19f
                textPaint.color = 0xFF7DD3FC.toInt()
                canvas.drawText("NATIVE ANDROID 9+ PORT (JAVA + KOTLIN) • OPREK EDITION", 640f, 268f, textPaint)

                textPaint.textSize = 15f
                textPaint.color = 0xFFE2E8F0.toInt()
                val pakNote = if (sprites.activeArchive != null) {
                    "✅ Original main.pak aktif (${sprites.activeArchive!!.size()} files loaded)"
                } else {
                    "💡 Siap main langsung ATAU import Feeding_Frenzy_Win_Preinstalled_EN.zip di OPREK LAB"
                }
                canvas.drawText(pakNote, 640f, 326f, textPaint)
                canvas.drawText("High Score: ${world.highScore}  •  40 Stages (Andy → Leon → Eddie → J.D. → Orville)", 640f, 360f, textPaint)

                drawMenuButton(canvas, controller.primaryActionRect, "▶  MULAI STORY MODE", 0xFF0284C7.toInt())
                drawMenuButton(canvas, controller.secondaryActionRect, "⏱  TIME ATTACK MODE", 0xFF0D9488.toInt())
                drawMenuButton(canvas, controller.tertiaryActionRect, "⚡  OPREK LAB & IMPORT MAIN.PAK", 0xFF7C3AED.toInt())
            }

            FrenzyWorld.RunState.PAUSED -> {
                textPaint.textSize = 44f
                textPaint.color = Color.WHITE
                canvas.drawText("GAME PAUSED", 640f, 260f, textPaint)
                textPaint.textSize = 18f
                textPaint.color = 0xFFBAE6FD.toInt()
                canvas.drawText("${world.currentStage.chapterTitle} • ${world.currentStage.stageTitle}", 640f, 315f, textPaint)

                drawMenuButton(canvas, controller.primaryActionRect, "▶  LANJUT MAIN", 0xFF0284C7.toInt())
                drawMenuButton(canvas, controller.secondaryActionRect, "⚡  BUKA OPREK LAB / MOD", 0xFF7C3AED.toInt())
                drawMenuButton(canvas, controller.tertiaryActionRect, "🏠  KEMBALI KE MENU UTAMA", 0xFF334155.toInt())
            }

            FrenzyWorld.RunState.STAGE_CLEARED -> {
                textPaint.textSize = 44f
                textPaint.color = 0xFF69F0AE.toInt()
                canvas.drawText("STAGE ${world.currentStage.stageNumber} CLEARED!", 640f, 260f, textPaint)
                textPaint.textSize = 20f
                textPaint.color = 0xFFFFEA00.toInt()
                canvas.drawText("Score: ${world.score}  •  Food Bank: ${world.totalFoodBank}", 640f, 322f, textPaint)

                drawMenuButton(canvas, controller.primaryActionRect, "▶  LANJUT KE STAGE BERIKUTNYA", 0xFF059669.toInt())
            }

            FrenzyWorld.RunState.GAME_OVER -> {
                textPaint.textSize = 46f
                textPaint.color = 0xFFFF5252.toInt()
                canvas.drawText("GAME OVER", 640f, 255f, textPaint)
                textPaint.textSize = 20f
                textPaint.color = Color.WHITE
                canvas.drawText("Final Score: ${world.score}  •  High Score: ${world.highScore}", 640f, 315f, textPaint)

                drawMenuButton(canvas, controller.primaryActionRect, "↻  COBA LAGI STAGE ${world.currentStage.stageNumber}", 0xFF0284C7.toInt())
                drawMenuButton(canvas, controller.secondaryActionRect, "⚡  AKTIFKAN GOD MODE (OPREK)", 0xFF7C3AED.toInt())
                drawMenuButton(canvas, controller.tertiaryActionRect, "🏠  MENU UTAMA", 0xFF334155.toInt())
            }

            else -> Unit
        }
    }

    private fun drawMenuButton(canvas: Canvas, rect: RectF, title: String, color: Int) {
        paint.color = color
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x66FFFFFF
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 20f
        textPaint.color = Color.WHITE
        canvas.drawText(title, rect.centerX(), rect.centerY() + 7f, textPaint)
    }
}
