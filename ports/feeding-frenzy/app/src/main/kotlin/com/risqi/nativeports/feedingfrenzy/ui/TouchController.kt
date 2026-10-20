package com.risqi.nativeports.feedingfrenzy.ui

import android.graphics.RectF
import android.view.MotionEvent
import com.risqi.nativeports.feedingfrenzy.engine.FrenzyWorld
import kotlin.math.hypot

/**
 * Responsive multi-touch input controller mapped in `1280x720` logical viewport coordinates.
 *
 * Supports simultaneously:
 *  - Floating Analog Joystick (or Direct Touch Target Follow) on left/main ocean area
 *  - Holdable `BOOST` button + one-tap `INHALE` skill button on bottom-right
 *  - Top-right HUD quick buttons (`OPREK LAB` & `PAUSE`)
 *  - Menu / Stage Clear / Game Over interactive buttons
 */
class TouchController(
    private val world: FrenzyWorld,
    var onOpenOprekRequested: (() -> Unit)? = null
) {

    // Top-right HUD pill buttons in 1280x720 space
    val oprekButtonRect = RectF(1042f, 12f, 1188f, 54f)
    val pauseButtonRect = RectF(1198f, 12f, 1266f, 54f)

    // Bottom-right arcade action buttons
    val dashCenterX = 1172f
    val dashCenterY = 598f
    val dashRadius = 64f

    val suctionCenterX = 1036f
    val suctionCenterY = 626f
    val suctionRadius = 50f

    // Overlay menu buttons (Title / Pause / Stage Clear / Game Over)
    val primaryActionRect = RectF(420f, 412f, 860f, 486f)
    val secondaryActionRect = RectF(420f, 504f, 860f, 572f)
    val tertiaryActionRect = RectF(420f, 588f, 860f, 650f)

    // Live analog stick visual telemetry for OceanRenderer
    @Volatile var stickActive: Boolean = false
        private set
    @Volatile var stickOriginX: Float = 210f
        private set
    @Volatile var stickOriginY: Float = 545f
        private set
    @Volatile var stickKnobX: Float = 210f
        private set
    @Volatile var stickKnobY: Float = 545f
        private set
    @Volatile var dashPressed: Boolean = false
        private set

    private var movePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var dashPointerId: Int = MotionEvent.INVALID_POINTER_ID
    private val maxStickRadius = 86f

    // Screen <-> Logical 1280x720 viewport transform updated by OceanRenderer
    @Volatile var viewportScale: Float = 1f
    @Volatile var viewportOffsetX: Float = 0f
    @Volatile var viewportOffsetY: Float = 0f

    private fun toWorldX(rawX: Float): Float =
        ((rawX - viewportOffsetX) / viewportScale).coerceIn(0f, FrenzyWorld.WORLD_WIDTH)

    private fun toWorldY(rawY: Float): Float =
        ((rawY - viewportOffsetY) / viewportScale).coerceIn(0f, FrenzyWorld.WORLD_HEIGHT)

    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val pointerId = event.getPointerId(idx)
        val wx = toWorldX(event.getX(idx))
        val wy = toWorldY(event.getY(idx))

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (oprekButtonRect.contains(wx, wy)) {
                    onOpenOprekRequested?.invoke()
                    return true
                }
                if (pauseButtonRect.contains(wx, wy)) {
                    togglePause()
                    return true
                }

                when (world.state) {
                    FrenzyWorld.RunState.TITLE_DEMO -> {
                        when {
                            primaryActionRect.contains(wx, wy) ->
                                world.startNewGame(FrenzyWorld.GameMode.STORY_ADVENTURE, 1)
                            secondaryActionRect.contains(wx, wy) ->
                                world.startNewGame(FrenzyWorld.GameMode.TIME_ATTACK, 1)
                            tertiaryActionRect.contains(wx, wy) ->
                                onOpenOprekRequested?.invoke()
                        }
                        return true
                    }
                    FrenzyWorld.RunState.PAUSED -> {
                        when {
                            primaryActionRect.contains(wx, wy) -> world.state = FrenzyWorld.RunState.PLAYING
                            secondaryActionRect.contains(wx, wy) -> onOpenOprekRequested?.invoke()
                            tertiaryActionRect.contains(wx, wy) -> world.startTitleDemo()
                        }
                        return true
                    }
                    FrenzyWorld.RunState.STAGE_CLEARED -> {
                        if (primaryActionRect.contains(wx, wy) || wx > 0f) {
                            world.advanceToNextStage()
                        }
                        return true
                    }
                    FrenzyWorld.RunState.GAME_OVER -> {
                        when {
                            primaryActionRect.contains(wx, wy) ->
                                world.startNewGame(world.mode, world.currentStage.stageNumber)
                            secondaryActionRect.contains(wx, wy) -> onOpenOprekRequested?.invoke()
                            tertiaryActionRect.contains(wx, wy) -> world.startTitleDemo()
                        }
                        return true
                    }
                    FrenzyWorld.RunState.PLAYING -> {
                        if (dist(wx, wy, dashCenterX, dashCenterY) <= dashRadius * 1.22f) {
                            dashPointerId = pointerId
                            dashPressed = true
                            pushMovementState()
                            return true
                        }
                        if (dist(wx, wy, suctionCenterX, suctionCenterY) <= suctionRadius * 1.25f) {
                            world.triggerSuctionSkill()
                            return true
                        }
                        if (movePointerId == MotionEvent.INVALID_POINTER_ID) {
                            movePointerId = pointerId
                            stickActive = true
                            stickOriginX = wx
                            stickOriginY = wy
                            stickKnobX = wx
                            stickKnobY = wy
                            pushMovementState()
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (p in 0 until event.pointerCount) {
                    val pid = event.getPointerId(p)
                    if (pid == movePointerId) {
                        val mx = toWorldX(event.getX(p))
                        val my = toWorldY(event.getY(p))
                        if (world.oprek.useDirectTouchControl) {
                            stickKnobX = mx
                            stickKnobY = my
                            world.setDirectTouchTarget(mx, my, true, dashPressed)
                        } else {
                            val dx = mx - stickOriginX
                            val dy = my - stickOriginY
                            val d = hypot(dx, dy)
                            if (d > maxStickRadius) {
                                val ratio = maxStickRadius / d
                                stickKnobX = stickOriginX + dx * ratio
                                stickKnobY = stickOriginY + dy * ratio
                            } else {
                                stickKnobX = mx
                                stickKnobY = my
                            }
                            pushMovementState()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (action == MotionEvent.ACTION_CANCEL) {
                    resetControls()
                } else {
                    if (pointerId == movePointerId) {
                        movePointerId = MotionEvent.INVALID_POINTER_ID
                        stickActive = false
                        stickKnobX = stickOriginX
                        stickKnobY = stickOriginY
                        pushMovementState()
                    }
                    if (pointerId == dashPointerId) {
                        dashPointerId = MotionEvent.INVALID_POINTER_ID
                        dashPressed = false
                        pushMovementState()
                    }
                }
            }
        }
        return true
    }

    private fun pushMovementState() {
        if (world.oprek.useDirectTouchControl) {
            world.setDirectTouchTarget(stickKnobX, stickKnobY, stickActive, dashPressed)
        } else if (!stickActive) {
            world.setJoystickInput(0f, 0f, dashPressed)
        } else {
            val nx = ((stickKnobX - stickOriginX) / maxStickRadius).coerceIn(-1f, 1f)
            val ny = ((stickKnobY - stickOriginY) / maxStickRadius).coerceIn(-1f, 1f)
            world.setJoystickInput(nx, ny, dashPressed)
        }
    }

    private fun togglePause() {
        if (world.state == FrenzyWorld.RunState.PLAYING) {
            world.state = FrenzyWorld.RunState.PAUSED
            resetControls()
        } else if (world.state == FrenzyWorld.RunState.PAUSED) {
            world.state = FrenzyWorld.RunState.PLAYING
        }
    }

    fun resetControls() {
        movePointerId = MotionEvent.INVALID_POINTER_ID
        dashPointerId = MotionEvent.INVALID_POINTER_ID
        stickActive = false
        dashPressed = false
        world.setJoystickInput(0f, 0f, false)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot(x1 - x2, y1 - y2)
}
