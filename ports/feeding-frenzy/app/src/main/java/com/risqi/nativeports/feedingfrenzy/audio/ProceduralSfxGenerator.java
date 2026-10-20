package com.risqi.nativeports.feedingfrenzy.audio;

import java.io.ByteArrayOutputStream;

/**
 * Pure Java 44.1kHz 16-bit PCM WAV sound synthesizer for classic arcade aquatic SFX.
 *
 * <p>Provides crisp underwater chomp, big gulp, bubble pop, grow fanfare, Feeding Frenzy combo
 * arpeggio, pearl chime, dash whoosh, and mine boom out-of-the-box — while allowing seamless
 * runtime replacement with extracted {@code sounds/*.ogg|.wav} from {@code main.pak}.
 */
public final class ProceduralSfxGenerator {

    public static final int SAMPLE_RATE = 44100;

    public enum SfxKind {
        CHOMP_SMALL,
        CHOMP_BIG,
        GROW_UP,
        FRENZY_FANFARE,
        BUBBLE_POP,
        PEARL_CHIME,
        MINE_EXPLODE,
        PLAYER_HURT,
        STAGE_CLEAR,
        DASH_WHOOSH
    }

    private ProceduralSfxGenerator() {
    }

    public static byte[] buildWav(SfxKind kind) {
        short[] pcm = synthesizePcm(kind);
        return encodeWav16Mono(pcm, SAMPLE_RATE);
    }

    public static short[] synthesizePcm(SfxKind kind) {
        switch (kind) {
            case CHOMP_SMALL:
                return synthSweepPop(0.085f, 260.0f, 760.0f, 0.35f, 4211L);
            case CHOMP_BIG:
                return synthSweepPop(0.145f, 155.0f, 540.0f, 0.50f, 9917L);
            case BUBBLE_POP:
                return synthSweepPop(0.095f, 520.0f, 1380.0f, 0.05f, 1337L);
            case GROW_UP:
                return synthArpeggio(new float[] { 392.0f, 493.88f, 587.33f, 783.99f }, 0.075f, 0.55f);
            case FRENZY_FANFARE:
                return synthArpeggio(new float[] { 523.25f, 659.25f, 783.99f, 1046.5f, 1318.5f }, 0.070f, 0.62f);
            case PEARL_CHIME:
                return synthArpeggio(new float[] { 880.0f, 1174.66f, 1567.98f }, 0.085f, 0.45f);
            case STAGE_CLEAR:
                return synthArpeggio(
                        new float[] { 392.0f, 523.25f, 659.25f, 783.99f, 1046.5f, 1318.5f },
                        0.095f,
                        0.60f);
            case MINE_EXPLODE:
                return synthExplosion(0.42f, 7777L);
            case PLAYER_HURT:
                return synthSweepPop(0.24f, 410.0f, 115.0f, 0.45f, 5150L);
            case DASH_WHOOSH:
            default:
                return synthSweepPop(0.16f, 220.0f, 690.0f, 0.55f, 2468L);
        }
    }

    private static short[] synthSweepPop(
            float durationSec,
            float freqStart,
            float freqEnd,
            float noiseMix,
            long seed) {
        int count = Math.max(64, (int) (SAMPLE_RATE * durationSec));
        short[] out = new short[count];
        double phase = 0.0;
        long rng = seed;
        for (int i = 0; i < count; i++) {
            float t = (float) i / (float) count;
            float env = (float) Math.sin(Math.PI * Math.pow(t, 0.55));
            float freq = freqStart + (freqEnd - freqStart) * (t * t);
            phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
            double tone = Math.sin(phase) * (1.0 - noiseMix * 0.45);
            rng = rng * 6364136223846793005L + 1442695040888963407L;
            double white = (((rng >>> 33) & 0xFFFF) / 32768.0) - 1.0;
            double sample = (tone + white * noiseMix * (1.0 - t)) * env * 0.72;
            out[i] = clampPcm(sample);
        }
        return out;
    }

    private static short[] synthArpeggio(float[] notesHz, float noteSec, float gain) {
        int perNote = Math.max(64, (int) (SAMPLE_RATE * noteSec));
        int total = perNote * notesHz.length;
        short[] out = new short[total];
        double phase = 0.0;
        for (int n = 0; n < notesHz.length; n++) {
            float f = notesHz[n];
            for (int i = 0; i < perNote; i++) {
                float t = (float) i / (float) perNote;
                float env = (float) Math.pow(1.0f - t * 0.72f, 1.4) * Math.min(1.0f, t * 18.0f);
                phase += (2.0 * Math.PI * f) / SAMPLE_RATE;
                double sample = (Math.sin(phase) * 0.72 + Math.sin(phase * 2.0) * 0.28) * env * gain;
                out[n * perNote + i] = clampPcm(sample);
            }
        }
        return out;
    }

    private static short[] synthExplosion(float durationSec, long seed) {
        int count = Math.max(128, (int) (SAMPLE_RATE * durationSec));
        short[] out = new short[count];
        long rng = seed;
        double lowPass = 0.0;
        double boomPhase = 0.0;
        for (int i = 0; i < count; i++) {
            float t = (float) i / (float) count;
            float env = (float) Math.pow(1.0f - t, 2.1);
            rng = rng * 6364136223846793005L + 1442695040888963407L;
            double white = (((rng >>> 33) & 0xFFFF) / 32768.0) - 1.0;
            lowPass = lowPass * 0.84 + white * 0.16;
            boomPhase += (2.0 * Math.PI * (95.0 - 60.0 * t)) / SAMPLE_RATE;
            double sample = (lowPass * 1.45 + Math.sin(boomPhase) * 0.65) * env * 0.82;
            out[i] = clampPcm(sample);
        }
        return out;
    }

    private static short clampPcm(double normalized) {
        double c = Math.max(-0.98, Math.min(0.98, normalized));
        return (short) (c * 32767.0);
    }

    public static byte[] encodeWav16Mono(short[] pcm, int sampleRate) {
        int dataBytes = pcm.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataBytes);
        writeAscii(out, "RIFF");
        writeInt32LE(out, 36 + dataBytes);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt32LE(out, 16);
        writeInt16LE(out, 1); // PCM
        writeInt16LE(out, 1); // Mono
        writeInt32LE(out, sampleRate);
        writeInt32LE(out, sampleRate * 2);
        writeInt16LE(out, 2);
        writeInt16LE(out, 16);
        writeAscii(out, "data");
        writeInt32LE(out, dataBytes);
        for (int i = 0; i < pcm.length; i++) {
            writeInt16LE(out, pcm[i]);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        for (int i = 0; i < s.length(); i++) {
            out.write(s.charAt(i) & 0xFF);
        }
    }

    private static void writeInt16LE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void writeInt32LE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }
}
