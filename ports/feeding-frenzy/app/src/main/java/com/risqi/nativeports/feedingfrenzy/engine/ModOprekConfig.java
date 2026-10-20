package com.risqi.nativeports.feedingfrenzy.engine;

/**
 * Live Oprek / Mod configuration shared safely between Android UI thread and 60Hz game loop thread.
 */
public final class ModOprekConfig {

    public volatile boolean godMode = false;
    public volatile boolean eatAnythingApex = false;
    public volatile boolean alwaysFrenzy = false;
    public volatile boolean infiniteDash = false;
    public volatile boolean useDirectTouchControl = false; // false = floating joystick, true = touch target follow
    public volatile boolean showHitboxesAndAiVectors = false;
    public volatile boolean preferOriginalPakSprites = true;

    public volatile float speedMultiplier = 1.0f;      // 0.5x .. 3.0x
    public volatile float scaleOverride = 0.0f;        // 0 = automatic growth scale, >0 = manual scale mult
    public volatile float foodMagnetRadius = 0.0f;     // 0 .. 500 world units
    public volatile float spawnRateMultiplier = 1.0f;  // 0.5x .. 3.0x

    public void resetToVanilla() {
        godMode = false;
        eatAnythingApex = false;
        alwaysFrenzy = false;
        infiniteDash = false;
        speedMultiplier = 1.0f;
        scaleOverride = 0.0f;
        foodMagnetRadius = 0.0f;
        spawnRateMultiplier = 1.0f;
    }

    public void applySultanPreset() {
        godMode = true;
        eatAnythingApex = false;
        alwaysFrenzy = true;
        infiniteDash = true;
        speedMultiplier = 1.65f;
        scaleOverride = 0.0f;
        foodMagnetRadius = 260.0f;
        spawnRateMultiplier = 1.6f;
    }

    public void applyTitanOrcaPreset() {
        godMode = true;
        eatAnythingApex = true;
        alwaysFrenzy = true;
        infiniteDash = true;
        speedMultiplier = 1.45f;
        scaleOverride = 2.25f;
        foodMagnetRadius = 340.0f;
        spawnRateMultiplier = 2.0f;
    }
}
