package com.risqi.nativeports.feedingfrenzy

import com.risqi.nativeports.feedingfrenzy.audio.ProceduralSfxGenerator
import com.risqi.nativeports.feedingfrenzy.audio.ProceduralSfxGenerator.SfxKind
import com.risqi.nativeports.feedingfrenzy.engine.FishEntity
import com.risqi.nativeports.feedingfrenzy.engine.FrenzyWorld
import com.risqi.nativeports.feedingfrenzy.engine.SpeciesType
import com.risqi.nativeports.feedingfrenzy.engine.StageCatalog
import com.risqi.nativeports.feedingfrenzy.pak.AlphaMaskComposer
import com.risqi.nativeports.feedingfrenzy.pak.ExeBinaryInspector
import com.risqi.nativeports.feedingfrenzy.pak.PakArchiveReader
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * End-to-end JVM verification suite exercising all Java + Kotlin subsystems of Feeding Frenzy Native Port.
 */
object NativePortVerificationSuite {

    private var assertionsPassed = 0

    private fun check(condition: Boolean, message: String) {
        if (!condition) {
            throw AssertionError("FAILED: $message")
        }
        assertionsPassed++
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Feeding Frenzy Native Port (Android 9+ Java+Kotlin) Verification Suite ===")
        testPakXorRoundTripAndZipBundle()
        testAlphaMaskComposition()
        testProceduralWavSynthesis()
        testFortyStagesAndCoreGameplayAndOprek()
        println("✅ ALL TESTS PASSED ($assertionsPassed assertions verified)!")
    }

    private fun buildSyntheticPeExe(sizeOfImage: Int): ByteArray {
        val buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0, 'M'.code.toByte())
        buf.put(1, 'Z'.code.toByte())
        val peOff = 0x80
        buf.putInt(0x3C, peOff)
        buf.put(peOff, 'P'.code.toByte())
        buf.put(peOff + 1, 'E'.code.toByte())
        buf.putShort(peOff + 4, 0x014C.toShort()) // IMAGE_FILE_MACHINE_I386
        buf.putShort(peOff + 6, 2.toShort())      // 2 sections
        buf.putInt(peOff + 8, 1076457600)         // Feb 2004 timestamp
        buf.putShort(peOff + 20, 0xE0.toShort())   // SizeOfOptionalHeader (PE32)
        val opt = peOff + 24
        buf.putShort(opt, 0x010B.toShort())       // PE32 magic
        buf.putInt(opt + 28, 0x00400000)          // ImageBase
        buf.putInt(opt + 56, sizeOfImage)         // SizeOfImage

        val sec0 = opt + 0xE0
        ".text".toByteArray().copyInto(buf.array(), sec0)
        buf.putInt(sec0 + 8, 0x72000)
        buf.putInt(sec0 + 12, 0x1000)

        val sec1 = sec0 + 40
        ".data".toByteArray().copyInto(buf.array(), sec1)
        buf.putInt(sec1 + 8, 0x18000)
        buf.putInt(sec1 + 12, 0x74000)
        return buf.array()
    }

    private fun testPakXorRoundTripAndZipBundle() {
        val files = LinkedHashMap<String, ByteArray>()
        files["images\\andySwim.jpg"] = byteArrayOf(10, 20, 30, 40)
        files["images\\_andySwim.gif"] = byteArrayOf(0, 127, -1, 64)
        files["sounds\\chomp1.ogg"] = "OggS-mock-stream".toByteArray()
        files["properties\\resources.xml"] = "<Resources id='FeedingFrenzy'/>".toByteArray()

        val encryptedPak = PakArchiveReader.buildPak(files, true)
        // Verify raw bytes are XOR-0xF7 encrypted (magic 0xBAC04AC0 ^ 0xF7F7F7F7)
        check((encryptedPak[0].toInt() and 0xFF) == (0xC0 xor 0xF7), "First byte XOR 0xF7 encrypted")

        // Wrap both main.pak + FeedingFrenzy.exe into Feeding_Frenzy_Win_Preinstalled_EN.zip
        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("Feeding Frenzy/main.pak"))
            zos.write(encryptedPak)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("Feeding Frenzy/FeedingFrenzy.exe"))
            zos.write(buildSyntheticPeExe(876544))
            zos.closeEntry()
        }

        val reader = PakArchiveReader.openBytes(zipOut.toByteArray(), "Feeding_Frenzy_Win_Preinstalled_EN.zip")
        check(reader.wasXorEncrypted(), "Detected XOR 0xF7 encryption")
        check(reader.size() == 4, "Expected 4 entries in main.pak")
        val swim = reader.findEntry("images/andySwim.jpg")
        check(swim != null, "Located images/andySwim.jpg case-insensitively")
        val alpha = reader.findAlphaMaskFor(swim)
        check(alpha != null && alpha.normalizedPath == "images/_andySwim.gif", "Matched PopCap _andySwim.gif alpha mask")

        val xml = String(reader.readEntryBytes(reader.findEntry("properties/resources.xml")!!))
        check(xml.contains("FeedingFrenzy"), "Decoded payload matches original XML")

        check(reader.hasCompanionExe(), "Extracted companion FeedingFrenzy.exe from ZIP")
        val pe = ExeBinaryInspector.inspect(reader.companionExeBytes)
        check(pe.validPe && pe.detectedBuild.contains("v5.7.18.1"), "Detected v5.7.18.1 PE32 signature")
        check(pe.memoryChains["foodBank"]!!.contains("0x1A8"), "Resolved foodBank pointer offset")
        println("  [OK] PAK XOR-0xF7 + ZIP unwrapper + PE32 Inspector verified")
    }

    private fun testAlphaMaskComposition() {
        val rgb = intArrayOf(0x00FF8800, 0x00112233)
        val mask = intArrayOf(0x00000000, 0x00FFFFFF)
        val composed = AlphaMaskComposer.composeArgb(rgb, 2, 1, mask, 2, 1)
        check(((composed[0] ushr 24) and 0xFF) == 0, "Black mask pixel becomes fully transparent")
        check(((composed[1] ushr 24) and 0xFF) == 255, "White mask pixel becomes fully opaque")
        check((composed[1] and 0x00FFFFFF) == 0x00112233, "RGB color preserved accurately")
        println("  [OK] PopCap RGB + Grayscale Alpha Mask composer verified")
    }

    private fun testProceduralWavSynthesis() {
        for (kind in SfxKind.entries) {
            val wav = ProceduralSfxGenerator.buildWav(kind)
            check(wav.size > 500, "WAV generated for $kind has audio frames")
            check(wav[0] == 'R'.code.toByte() && wav[1] == 'I'.code.toByte() && wav[8] == 'W'.code.toByte(), "Valid RIFF/WAVE header")
        }
        println("  [OK] All ${SfxKind.entries.size} underwater 44.1kHz WAV SFX verified")
    }

    private fun testFortyStagesAndCoreGameplayAndOprek() {
        check(StageCatalog.getAllStages().size == 40, "All 40 Feeding Frenzy stages defined")
        check(StageCatalog.getStage(1).heroSpecies == SpeciesType.ANGELFISH, "Stage 1 starts as Andy the Angelfish")
        check(StageCatalog.getStage(40).heroSpecies == SpeciesType.ORCA, "Stage 40 culminates as Orville the Orca")

        val world = FrenzyWorld()
        world.startNewGame(FrenzyWorld.GameMode.STORY_ADVENTURE, 1)

        // Feed minnows right at player's mouth to test Frenzy Multiplier + Growth + Food Bank
        repeat(6) { i ->
            world.npcFish.add(
                FishEntity(
                    9000 + i,
                    SpeciesType.MINNOW,
                    false,
                    world.player.x + 2f,
                    world.player.y,
                    SpeciesType.MINNOW.baseFoodTier,
                    SpeciesType.MINNOW.baseRadius
                )
            )
            world.step(1f / 60f)
        }
        check(world.score > 0, "Score increased after eating minnows")
        check(world.totalFoodBank >= 6, "Food Bank accumulated eaten fish")
        check(world.frenzyMultiplier >= 2, "FEEDING FRENZY combo multiplier activated (${world.frenzyMultiplier}x)")

        // Test Oprek: God Mode survives Great White Shark collision!
        world.oprek.godMode = true
        val livesBefore = world.lives
        world.player.invulnerableTimer = 0f
        world.npcFish.add(
            FishEntity(
                9500,
                SpeciesType.GREAT_WHITE_SHARK,
                false,
                world.player.x,
                world.player.y,
                SpeciesType.GREAT_WHITE_SHARK.baseFoodTier,
                SpeciesType.GREAT_WHITE_SHARK.baseRadius
            )
        )
        world.step(1f / 60f)
        check(world.lives == livesBefore, "Oprek God Mode protected player from Great White Shark")

        // Test Oprek: Eat-Anything Apex lets Andy eat that Great White Shark!
        world.oprek.eatAnythingApex = true
        world.step(1f / 60f)
        check(world.npcFish.none { it.id == 9500 }, "Oprek Eat-Anything Apex devoured Great White Shark!")

        // Test Oprek Instant Grow + Stage Teleport to Stage 40
        world.forceInstantGrowOprek()
        world.jumpToStage(40)
        check(world.currentStage.stageNumber == 40 && world.player.species == SpeciesType.ORCA, "Stage teleporter jumped to Stage 40 Orca")

        // Simulate 300 frames of continuous gameplay stability
        world.setJoystickInput(0.75f, -0.35f, true)
        repeat(300) {
            world.step(1f / 60f)
        }
        println("  [OK] 40-stage simulation, Frenzy gauge, Food Bank, & Oprek cheats verified")
    }
}
