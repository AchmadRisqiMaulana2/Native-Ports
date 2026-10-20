package com.risqi.nativeports.feedingfrenzy.engine;

/**
 * Aquatic food-chain species catalog modeled after PopCap's 2004 Feeding Frenzy.
 */
public enum SpeciesType {
    MINNOW(
            "Minnow",
            new String[] { "minnow", "fish1", "smallfish" },
            1,
            16.0f,
            155.0f,
            10,
            0xFF4AD8FF,
            0xFF0E7CB8),
    SURGEONFISH(
            "Surgeonfish",
            new String[] { "surgeon", "tang", "bluefish", "fish2" },
            2,
            24.0f,
            175.0f,
            25,
            0xFF3377FF,
            0xFFFFD426),
    ANGELFISH(
            "Andy the Angelfish",
            new String[] { "andy", "angelfish", "angel" },
            2,
            26.0f,
            205.0f,
            35,
            0xFFFFB018,
            0xFF1A56DB),
    BUTTERFLYFISH(
            "Butterflyfish",
            new String[] { "butterfly", "parrot", "fish3" },
            3,
            34.0f,
            190.0f,
            55,
            0xFFFFDF38,
            0xFF222831),
    LIONFISH(
            "Leon the Lionfish",
            new String[] { "leon", "lionfish", "lion" },
            4,
            40.0f,
            215.0f,
            85,
            0xFFFF5E3A,
            0xFFFFE6D0),
    TUNA(
            "Yellowfin Tuna",
            new String[] { "tuna", "mackerel", "fish4" },
            5,
            50.0f,
            240.0f,
            130,
            0xFF5CE1E6,
            0xFF1D4ED8),
    ANGLERFISH(
            "Eddie the Anglerfish",
            new String[] { "eddie", "angler", "anglerfish" },
            6,
            56.0f,
            220.0f,
            180,
            0xFF7E57C2,
            0xFF80FF72),
    JOHN_DORY(
            "J.D. the John Dory",
            new String[] { "jd", "johndory", "dory" },
            7,
            64.0f,
            255.0f,
            240,
            0xFF8CE99A,
            0xFF2B8A3E),
    BARRACUDA(
            "Razor Barracuda",
            new String[] { "barracuda", "cuda" },
            8,
            74.0f,
            325.0f,
            340,
            0xFFA5B4FC,
            0xFF312E81),
    GREAT_WHITE_SHARK(
            "Great White Shark",
            new String[] { "shark", "greatwhite", "sharkking" },
            9,
            96.0f,
            285.0f,
            600,
            0xFF94A3B8,
            0xFFE2E8F0),
    ORCA(
            "Orville the Orca",
            new String[] { "orville", "orca", "killerwhale" },
            10,
            108.0f,
            290.0f,
            950,
            0xFF1E293B,
            0xFFF8FAFC),
    JELLYFISH(
            "Sting Jellyfish",
            new String[] { "jelly", "jellyfish" },
            99,
            34.0f,
            68.0f,
            0,
            0xDDFF7AE5,
            0xDD9B5DE5);

    public final String displayName;
    public final String[] pakStemHints;
    public final int baseFoodTier;
    public final float baseRadius;
    public final float baseSpeed;
    public final int scoreValue;
    public final int primaryColor;
    public final int accentColor;

    SpeciesType(
            String displayName,
            String[] pakStemHints,
            int baseFoodTier,
            float baseRadius,
            float baseSpeed,
            int scoreValue,
            int primaryColor,
            int accentColor) {
        this.displayName = displayName;
        this.pakStemHints = pakStemHints;
        this.baseFoodTier = baseFoodTier;
        this.baseRadius = baseRadius;
        this.baseSpeed = baseSpeed;
        this.scoreValue = scoreValue;
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
    }
}
