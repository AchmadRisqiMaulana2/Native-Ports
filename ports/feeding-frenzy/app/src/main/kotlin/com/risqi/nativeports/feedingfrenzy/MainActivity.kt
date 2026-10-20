package com.risqi.nativeports.feedingfrenzy

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.risqi.nativeports.feedingfrenzy.assets.PakSpriteRepository
import com.risqi.nativeports.feedingfrenzy.audio.AndroidSoundEngine
import com.risqi.nativeports.feedingfrenzy.engine.FrenzyWorld
import com.risqi.nativeports.feedingfrenzy.ui.FeedingFrenzySurfaceView
import com.risqi.nativeports.feedingfrenzy.ui.OprekModSheet

/**
 * Native Android 9.0+ (API 28+) entry point for Feeding Frenzy (Java + Kotlin Port).
 *
 * Configures immersive edge-to-edge display across punch-hole cutouts (`shortEdges`), restores
 * persisted high score, auto-loads any bundled/imported `main.pak`, and handles Storage Access
 * Framework (`ACTION_OPEN_DOCUMENT`) imports for `Feeding_Frenzy_Win_Preinstalled_EN.zip`.
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_IMPORT_PAK_OR_ZIP = 2004
        private const val PREFS_NAME = "feeding_frenzy_native_port"
        private const val KEY_HIGH_SCORE = "high_score"
    }

    private lateinit var world: FrenzyWorld
    private lateinit var soundEngine: AndroidSoundEngine
    private lateinit var spriteRepository: PakSpriteRepository
    private lateinit var gameSurfaceView: FeedingFrenzySurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        configureFullscreenCutoutWindow()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        world = FrenzyWorld().apply {
            highScore = prefs.getInt(KEY_HIGH_SCORE, 0)
        }
        soundEngine = AndroidSoundEngine(this)
        spriteRepository = PakSpriteRepository(this)

        // Background auto-detection of bundled assets/main.pak or previously imported ZIP/PAK
        Thread({
            val archive = spriteRepository.tryAutoLoadOnStartup()
            if (archive != null) {
                val replacedAudio = soundEngine.applyPakAudioOverrides(archive)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Original PopCap PAK loaded (${archive.size()} files, $replacedAudio SFX)!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, "PakStartupLoader").start()

        gameSurfaceView = FeedingFrenzySurfaceView(
            context = this,
            world = world,
            spriteRepository = spriteRepository,
            soundEngine = soundEngine,
            onOpenOprekDialog = ::openOprekDialog
        )
        setContentView(gameSurfaceView)
    }

    private fun openOprekDialog() {
        if (world.state == FrenzyWorld.RunState.PLAYING) {
            world.state = FrenzyWorld.RunState.PAUSED
        }
        OprekModSheet.show(
            activity = this,
            world = world,
            sprites = spriteRepository,
            sound = soundEngine,
            onPickArchiveFile = ::launchArchiveFilePicker
        )
    }

    private fun launchArchiveFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_IMPORT_PAK_OR_ZIP)
    }

    @Deprecated("Handled directly on Activity without AndroidX fragment overhead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMPORT_PAK_OR_ZIP && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            Thread({
                val result = spriteRepository.importFromUri(uri)
                result.onSuccess { reader ->
                    val audioCount = soundEngine.applyPakAudioOverrides(reader)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Berhasil muat ${reader.size()} aset (${spriteRepository.loadedSpeciesCount} sprite, $audioCount audio)!",
                            Toast.LENGTH_LONG
                        ).show()
                        openOprekDialog()
                    }
                }.onFailure { err ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Gagal baca PAK/ZIP: ${err.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }, "PakSafImporter").start()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveStickyFlags()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveStickyFlags()
        gameSurfaceView.resumeGame()
    }

    override fun onPause() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_HIGH_SCORE, world.highScore)
            .apply()
        gameSurfaceView.pauseGame()
        super.onPause()
    }

    override fun onDestroy() {
        soundEngine.release()
        super.onDestroy()
    }

    @Deprecated("Classic back key pauses active game before exiting")
    override fun onBackPressed() {
        if (world.state == FrenzyWorld.RunState.PLAYING) {
            world.state = FrenzyWorld.RunState.PAUSED
        } else if (world.state != FrenzyWorld.RunState.TITLE_DEMO) {
            world.startTitleDemo()
        } else {
            super.onBackPressed()
        }
    }

    private fun configureFullscreenCutoutWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        applyImmersiveStickyFlags()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveStickyFlags() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }
}
