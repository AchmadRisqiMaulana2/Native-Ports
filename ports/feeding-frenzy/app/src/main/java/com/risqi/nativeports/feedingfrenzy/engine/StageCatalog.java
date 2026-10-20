package com.risqi.nativeports.feedingfrenzy.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines all 40 classic Feeding Frenzy stages across the 5 ocean chapters.
 */
public final class StageCatalog {

    public static final int TOTAL_STAGES = 40;

    public static final class StageDef {
        public final int stageNumber; // 1..40
        public final int chapterIndex; // 1..5
        public final String chapterTitle;
        public final String stageTitle;
        public final SpeciesType heroSpecies;
        public final int startingFoodTier;
        public final SpeciesType[] spawnPool;
        public final int foodGoalToClear;
        public final int maxMines;
        public final int maxJellyfish;
        public final boolean bossSharkStage;
        public final int waterTopColor;
        public final int waterBottomColor;
        public final float timeAttackSeconds;

        public StageDef(
                int stageNumber,
                int chapterIndex,
                String chapterTitle,
                String stageTitle,
                SpeciesType heroSpecies,
                int startingFoodTier,
                SpeciesType[] spawnPool,
                int foodGoalToClear,
                int maxMines,
                int maxJellyfish,
                boolean bossSharkStage,
                int waterTopColor,
                int waterBottomColor,
                float timeAttackSeconds) {
            this.stageNumber = stageNumber;
            this.chapterIndex = chapterIndex;
            this.chapterTitle = chapterTitle;
            this.stageTitle = stageTitle;
            this.heroSpecies = heroSpecies;
            this.startingFoodTier = startingFoodTier;
            this.spawnPool = spawnPool;
            this.foodGoalToClear = foodGoalToClear;
            this.maxMines = maxMines;
            this.maxJellyfish = maxJellyfish;
            this.bossSharkStage = bossSharkStage;
            this.waterTopColor = waterTopColor;
            this.waterBottomColor = waterBottomColor;
            this.timeAttackSeconds = timeAttackSeconds;
        }
    }

    private static final List<StageDef> STAGES = buildAllStages();

    private StageCatalog() {
    }

    public static List<StageDef> getAllStages() {
        return STAGES;
    }

    public static StageDef getStage(int stageNumberOneBased) {
        int clamped = Math.max(1, Math.min(TOTAL_STAGES, stageNumberOneBased));
        return STAGES.get(clamped - 1);
    }

    private static List<StageDef> buildAllStages() {
        List<StageDef> list = new ArrayList<StageDef>(TOTAL_STAGES);
        for (int s = 1; s <= TOTAL_STAGES; s++) {
            int chapter = ((s - 1) / 8) + 1;
            int subStage = ((s - 1) % 8) + 1;
            boolean boss = (subStage == 8);

            SpeciesType hero;
            int startTier;
            String chapterTitle;
            SpeciesType[] pool;
            int topColor;
            int botColor;

            switch (chapter) {
                case 1:
                    hero = SpeciesType.ANGELFISH;
                    startTier = 1;
                    chapterTitle = "Ch.1 • Sunlit Coral Reef";
                    pool = new SpeciesType[] {
                        SpeciesType.MINNOW,
                        SpeciesType.SURGEONFISH,
                        SpeciesType.BUTTERFLYFISH,
                        SpeciesType.LIONFISH,
                        SpeciesType.BARRACUDA
                    };
                    topColor = 0xFF1496D4;
                    botColor = 0xFF062C59;
                    break;
                case 2:
                    hero = SpeciesType.LIONFISH;
                    startTier = 2;
                    chapterTitle = "Ch.2 • Sunken Galleon Kelp";
                    pool = new SpeciesType[] {
                        SpeciesType.MINNOW,
                        SpeciesType.SURGEONFISH,
                        SpeciesType.BUTTERFLYFISH,
                        SpeciesType.TUNA,
                        SpeciesType.BARRACUDA
                    };
                    topColor = 0xFF0F8B8D;
                    botColor = 0xFF052938;
                    break;
                case 3:
                    hero = SpeciesType.ANGLERFISH;
                    startTier = 4;
                    chapterTitle = "Ch.3 • Abyssal Volcanic Trench";
                    pool = new SpeciesType[] {
                        SpeciesType.SURGEONFISH,
                        SpeciesType.LIONFISH,
                        SpeciesType.TUNA,
                        SpeciesType.BARRACUDA,
                        SpeciesType.GREAT_WHITE_SHARK
                    };
                    topColor = 0xFF283E78;
                    botColor = 0xFF090B24;
                    break;
                case 4:
                    hero = SpeciesType.JOHN_DORY;
                    startTier = 5;
                    chapterTitle = "Ch.4 • Lost Ruins of Atlantis";
                    pool = new SpeciesType[] {
                        SpeciesType.BUTTERFLYFISH,
                        SpeciesType.TUNA,
                        SpeciesType.ANGLERFISH,
                        SpeciesType.BARRACUDA,
                        SpeciesType.GREAT_WHITE_SHARK
                    };
                    topColor = 0xFF197278;
                    botColor = 0xFF0B1D36;
                    break;
                default:
                    hero = SpeciesType.ORCA;
                    startTier = 7;
                    chapterTitle = "Ch.5 • Shark King's Lair";
                    pool = new SpeciesType[] {
                        SpeciesType.LIONFISH,
                        SpeciesType.TUNA,
                        SpeciesType.JOHN_DORY,
                        SpeciesType.BARRACUDA,
                        SpeciesType.GREAT_WHITE_SHARK
                    };
                    topColor = 0xFF1D4E89;
                    botColor = 0xFF061224;
                    break;
            }

            String stageTitle = boss
                    ? ("Stage " + s + " • BOSS APEX WATERS")
                    : ("Stage " + s + " (" + chapter + "-" + subStage + ")");
            int foodGoal = 24 + s * 6;
            int mines = s >= 6 ? Math.min(5, (s - 3) / 6) : 0;
            int jellies = s >= 4 ? Math.min(4, (s - 1) / 7) : 0;
            float clockSec = 75.0f + (subStage * 6.0f);

            list.add(new StageDef(
                    s,
                    chapter,
                    chapterTitle,
                    stageTitle,
                    hero,
                    startTier,
                    pool,
                    foodGoal,
                    mines,
                    jellies,
                    boss,
                    topColor,
                    botColor,
                    clockSec));
        }
        return Collections.unmodifiableList(list);
    }
}
