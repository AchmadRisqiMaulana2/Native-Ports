package com.risqi.nativeports.feedingfrenzy.ui

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.risqi.nativeports.feedingfrenzy.assets.PakSpriteRepository
import com.risqi.nativeports.feedingfrenzy.audio.AndroidSoundEngine
import com.risqi.nativeports.feedingfrenzy.engine.FrenzyWorld

/**
 * Dedicated hardware-accelerated [SurfaceView] hosting the high-refresh-rate game loop thread.
 */
class FeedingFrenzySurfaceView(
    context: Context,
    val world: FrenzyWorld,
    val spriteRepository: PakSpriteRepository,
    val soundEngine: AndroidSoundEngine,
    onOpenOprekDialog: () -> Unit
) : SurfaceView(context), SurfaceHolder.Callback {

    val touchController = TouchController(world) {
        post(onOpenOprekDialog)
    }
    val renderer = OceanRenderer(world, spriteRepository, touchController)

    @Volatile private var running = false
    private var gameThread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startLoopThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // OceanRenderer dynamically computes viewport mapping on every frame
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoopThread()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchController.onTouchEvent(event)
    }

    fun resumeGame() {
        if (holder.surface.isValid) {
            startLoopThread()
        }
    }

    fun pauseGame() {
        touchController.resetControls()
        stopLoopThread()
    }

    private fun startLoopThread() {
        if (running) return
        running = true
        gameThread = Thread(::runLoop, "FeedingFrenzy-RenderSim").apply {
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    private fun stopLoopThread() {
        running = false
        val t = gameThread
        gameThread = null
        if (t != null && t !== Thread.currentThread()) {
            runCatching { t.join(450L) }
        }
    }

    private fun runLoop() {
        var lastNs = System.nanoTime()
        var fpsTimer = 0f
        var frameCount = 0

        while (running) {
            val nowNs = System.nanoTime()
            val dt = ((nowNs - lastNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
            lastNs = nowNs

            world.step(dt)

            // Dispatch queued SFX outside synchronized simulation lock
            val queued = world.drainPendingSfx()
            for (sfx in queued) {
                soundEngine.play(sfx)
            }

            if (holder.surface.isValid) {
                val canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    try {
                        renderer.render(canvas, width, height)
                    } finally {
                        runCatching { holder.unlockCanvasAndPost(canvas) }
                    }
                }
            }

            frameCount++
            fpsTimer += dt
            if (fpsTimer >= 0.5f) {
                renderer.currentFps = (frameCount / fpsTimer).toInt()
                frameCount = 0
                fpsTimer = 0f
            }

            // Yield briefly if lockHardwareCanvas didn't already block on display VSync
            val frameElapsedMs = (System.nanoTime() - nowNs) / 1_000_000L
            if (frameElapsedMs < 8L) {
                runCatching { Thread.sleep(8L - frameElapsedMs) }
            }
        }
    }
}
