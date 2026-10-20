package com.risqi.nativeports.feedingfrenzy.engine;

import com.risqi.nativeports.feedingfrenzy.audio.ProceduralSfxGenerator.SfxKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core fixed-timestep game simulation for PopCap's 2004 Feeding Frenzy (Android 9+ Native Port).
 *
 * <p>Implements classic food-chain growth tiers (Small / Medium / Large), {@code FEEDING FRENZY!}
 * combo multiplier gauge, Food Bank progression, Time Attack clock, seabed Oyster Pearls, power-up
 * bubbles, Naval Mines, Jellyfish stuns, Dash/Inhale skills, and live {@link ModOprekConfig} hooks.
 */
public final class FrenzyWorld {

    public static final float WORLD_WIDTH = 1280.0f;
    public static final float WORLD_HEIGHT = 720.0f;
    private static final float SEABED_Y = 668.0f;
    private static final float SURFACE_Y = 58.0f;

    public enum GameMode {
        STORY_ADVENTURE,
        TIME_ATTACK
    }

    public enum RunState {
        TITLE_DEMO,
        PLAYING,
        PAUSED,
        STAGE_CLEARED,
        GAME_OVER
    }

    public static final class FloatingPopup {
        public float x;
        public float y;
        public float vy;
        public float life;
        public String text;
        public int color;

        public FloatingPopup(float x, float y, String text, int color) {
            this.x = x;
            this.y = y;
            this.vy = -52.0f;
            this.life = 1.05f;
            this.text = text;
            this.color = color;
        }
    }

    public final ModOprekConfig oprek = new ModOprekConfig();
    private final Random rng = new Random(20040211L);

    public GameMode mode = GameMode.STORY_ADVENTURE;
    public RunState state = RunState.TITLE_DEMO;
    public StageCatalog.StageDef currentStage = StageCatalog.getStage(1);

    public FishEntity player;
    public final List<FishEntity> npcFish = new ArrayList<FishEntity>(64);
    public final List<BonusItem> bonuses = new ArrayList<BonusItem>(24);
    public final List<FloatingPopup> popups = new ArrayList<FloatingPopup>(32);
    public final List<SfxKind> pendingSfx = new ArrayList<SfxKind>(16);

    public int score = 0;
    public int highScore = 0;
    public int lives = 3;
    public int totalFoodBank = 0;
    public int stageFoodEaten = 0;
    public int growthTierIndex = 0; // 0 = Small, 1 = Medium, 2 = Large

    public float frenzyMeter = 0.0f; // 0..100
    public int frenzyMultiplier = 1; // 1..8
    public float dashEnergy = 100.0f; // 0..100
    public float suctionCooldown = 0.0f;
    public boolean suctionActive = false;
    public float suctionTimer = 0.0f;

    public float speedBoostTimer = 0.0f;
    public float freezePredatorsTimer = 0.0f;
    public boolean hasBubbleShield = false;
    public float screenShake = 0.0f;
    public float timeAttackRemaining = 90.0f;
    public float worldTimeSeconds = 0.0f;

    // Input state pushed from Kotlin TouchController
    private volatile float inputDirX = 0.0f;
    private volatile float inputDirY = 0.0f;
    private volatile boolean inputHasTarget = false;
    private volatile float inputTargetX = WORLD_WIDTH * 0.5f;
    private volatile float inputTargetY = WORLD_HEIGHT * 0.5f;
    private volatile boolean inputDashHeld = false;

    private int nextEntityId = 1;
    private float spawnAccumulator = 0.0f;
    private float bonusAccumulator = 0.0f;

    public FrenzyWorld() {
        startTitleDemo();
    }

    public synchronized void startTitleDemo() {
        this.state = RunState.TITLE_DEMO;
        loadStageInternal(1, false);
    }

    public synchronized void startNewGame(GameMode selectedMode, int startStageOneBased) {
        this.mode = selectedMode;
        this.score = 0;
        this.lives = 3;
        this.frenzyMeter = 0.0f;
        this.frenzyMultiplier = 1;
        this.state = RunState.PLAYING;
        loadStageInternal(startStageOneBased, true);
    }

    public synchronized void jumpToStage(int stageOneBased) {
        if (this.state == RunState.TITLE_DEMO || this.state == RunState.GAME_OVER) {
            this.lives = Math.max(3, this.lives);
        }
        this.state = RunState.PLAYING;
        loadStageInternal(stageOneBased, true);
    }

    public synchronized void advanceToNextStage() {
        int next = currentStage.stageNumber >= StageCatalog.TOTAL_STAGES
                ? 1
                : (currentStage.stageNumber + 1);
        this.state = RunState.PLAYING;
        loadStageInternal(next, true);
    }

    public synchronized void forceInstantGrowOprek() {
        if (player == null) {
            return;
        }
        if (growthTierIndex < 2) {
            growthTierIndex++;
            recomputePlayerStats();
            player.invulnerableTimer = 1.5f;
            screenShake = 0.30f;
            emitSfx(SfxKind.GROW_UP);
            popups.add(new FloatingPopup(player.x, player.y - player.radius, "OPREK GROW UP!", 0xFFFFEB3B));
        } else {
            stageFoodEaten = currentStage.foodGoalToClear;
            checkStageProgression();
        }
    }

    private void loadStageInternal(int stageNum, boolean announce) {
        this.currentStage = StageCatalog.getStage(stageNum);
        this.npcFish.clear();
        this.bonuses.clear();
        this.popups.clear();

        this.stageFoodEaten = 0;
        this.growthTierIndex = 0;
        this.dashEnergy = 100.0f;
        this.suctionCooldown = 0.0f;
        this.suctionActive = false;
        this.speedBoostTimer = 0.0f;
        this.freezePredatorsTimer = 0.0f;
        this.timeAttackRemaining = currentStage.timeAttackSeconds;

        this.player = new FishEntity(
                nextEntityId++,
                currentStage.heroSpecies,
                true,
                WORLD_WIDTH * 0.5f,
                WORLD_HEIGHT * 0.52f,
                currentStage.startingFoodTier,
                currentStage.heroSpecies.baseRadius);
        this.player.invulnerableTimer = 2.4f;
        recomputePlayerStats();

        // Seed ocean ecosystem immediately so the reef feels alive on frame 1
        int initialFish = 16;
        for (int i = 0; i < initialFish; i++) {
            spawnRandomNpc(true);
        }
        // Seed seabed oyster pearl clams + optional mines
        spawnOysterAt(WORLD_WIDTH * 0.24f);
        spawnOysterAt(WORLD_WIDTH * 0.76f);
        for (int m = 0; m < currentStage.maxMines; m++) {
            float mx = 160.0f + rng.nextFloat() * (WORLD_WIDTH - 320.0f);
            float my = 140.0f + rng.nextFloat() * (SEABED_Y - 220.0f);
            BonusItem mine = new BonusItem(nextEntityId++, BonusItem.Kind.NAVAL_MINE, mx, my, 25.0f);
            mine.vy = (rng.nextFloat() - 0.5f) * 26.0f;
            bonuses.add(mine);
        }
        for (int j = 0; j < currentStage.maxJellyfish; j++) {
            spawnJellyfish();
        }

        if (announce) {
            popups.add(new FloatingPopup(
                    player.x,
                    player.y - 60.0f,
                    currentStage.stageTitle + " — EAT SMALLER FISH!",
                    0xFFFFFFFF));
        }
    }

    public void setJoystickInput(float dx, float dy, boolean dashHeld) {
        this.inputDirX = dx;
        this.inputDirY = dy;
        this.inputHasTarget = false;
        this.inputDashHeld = dashHeld;
    }

    public void setDirectTouchTarget(float worldX, float worldY, boolean active, boolean dashHeld) {
        this.inputTargetX = worldX;
        this.inputTargetY = worldY;
        this.inputHasTarget = active;
        this.inputDashHeld = dashHeld;
    }

    public synchronized void triggerSuctionSkill() {
        if (state != RunState.PLAYING) {
            return;
        }
        if (suctionCooldown <= 0.0f || oprek.infiniteDash) {
            suctionActive = true;
            suctionTimer = 0.65f;
            suctionCooldown = oprek.infiniteDash ? 0.25f : 3.2f;
            emitSfx(SfxKind.DASH_WHOOSH);
            popups.add(new FloatingPopup(player.x, player.y - player.radius, "INHALE!", 0xFF72EFDD));
        }
    }

    public synchronized List<SfxKind> drainPendingSfx() {
        if (pendingSfx.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<SfxKind> copy = new ArrayList<SfxKind>(pendingSfx);
        pendingSfx.clear();
        return copy;
    }

    private void emitSfx(SfxKind sfx) {
        if (pendingSfx.size() < 24) {
            pendingSfx.add(sfx);
        }
    }

    public synchronized void step(float dtSeconds) {
        float dt = Math.min(0.05f, Math.max(0.001f, dtSeconds));
        worldTimeSeconds += dt;
        if (screenShake > 0.0f) {
            screenShake = Math.max(0.0f, screenShake - dt);
        }

        if (state == RunState.PAUSED || state == RunState.STAGE_CLEARED || state == RunState.GAME_OVER) {
            updatePopups(dt);
            return;
        }

        recomputePlayerStats();
        updateTimers(dt);
        updatePlayerMovement(dt);
        updateNpcFish(dt);
        updateBonuses(dt);
        updatePopups(dt);

        // Maintain healthy reef population
        int desiredPopulation = (int) (18 * Math.max(0.5f, oprek.spawnRateMultiplier));
        spawnAccumulator += dt * oprek.spawnRateMultiplier;
        if (npcFish.size() < desiredPopulation && spawnAccumulator >= 0.42f) {
            spawnAccumulator = 0.0f;
            spawnRandomNpc(false);
        }

        bonusAccumulator += dt;
        if (bonusAccumulator >= 4.8f && bonuses.size() < 12) {
            bonusAccumulator = 0.0f;
            spawnRandomBubble();
        }

        if (state == RunState.PLAYING && mode == GameMode.TIME_ATTACK) {
            timeAttackRemaining -= dt;
            if (timeAttackRemaining <= 0.0f) {
                timeAttackRemaining = 0.0f;
                if (!oprek.godMode) {
                    state = RunState.GAME_OVER;
                    emitSfx(SfxKind.PLAYER_HURT);
                }
            }
        }
    }

    private void recomputePlayerStats() {
        if (player == null) {
            return;
        }
        int effectiveTier = currentStage.startingFoodTier + growthTierIndex * 2;
        if (oprek.eatAnythingApex) {
            effectiveTier = 50;
        }
        player.foodTier = effectiveTier;

        float growthScale = 1.0f + (growthTierIndex * 0.34f);
        if (oprek.scaleOverride > 0.05f) {
            growthScale = oprek.scaleOverride;
        }
        player.radius = currentStage.heroSpecies.baseRadius * growthScale;
    }

    private void updateTimers(float dt) {
        if (speedBoostTimer > 0.0f) {
            speedBoostTimer = Math.max(0.0f, speedBoostTimer - dt);
        }
        if (freezePredatorsTimer > 0.0f) {
            freezePredatorsTimer = Math.max(0.0f, freezePredatorsTimer - dt);
        }
        if (suctionCooldown > 0.0f) {
            suctionCooldown = Math.max(0.0f, suctionCooldown - dt);
        }
        if (suctionActive) {
            suctionTimer -= dt;
            if (suctionTimer <= 0.0f) {
                suctionActive = false;
            }
        }

        if (oprek.alwaysFrenzy) {
            frenzyMeter = 100.0f;
            frenzyMultiplier = 8;
        } else if (frenzyMeter > 0.0f) {
            frenzyMeter = Math.max(0.0f, frenzyMeter - dt * 11.5f);
            updateFrenzyMultiplier();
        }
    }

    private void updatePlayerMovement(float dt) {
        if (player == null) {
            return;
        }
        if (player.invulnerableTimer > 0.0f) {
            player.invulnerableTimer = Math.max(0.0f, player.invulnerableTimer - dt);
        }
        if (player.chompTimer > 0.0f) {
            player.chompTimer = Math.max(0.0f, player.chompTimer - dt);
        }
        if (player.stunTimer > 0.0f) {
            player.stunTimer = Math.max(0.0f, player.stunTimer - dt);
            player.vx *= 0.90f;
            player.vy *= 0.90f;
            player.x = clamp(player.x + player.vx * dt, player.radius, WORLD_WIDTH - player.radius);
            player.y = clamp(player.y + player.vy * dt, SURFACE_Y, SEABED_Y - player.radius * 0.4f);
            return;
        }

        float desiredX = 0.0f;
        float desiredY = 0.0f;

        if (state == RunState.TITLE_DEMO) {
            // Autonomous demo autopilot chasing nearest edible fish while dodging predators
            FishEntity bestPrey = null;
            float bestDist = Float.MAX_VALUE;
            for (int i = 0; i < npcFish.size(); i++) {
                FishEntity f = npcFish.get(i);
                if (canPlayerEat(f)) {
                    float d = distSq(player.x, player.y, f.x, f.y);
                    if (d < bestDist) {
                        bestDist = d;
                        bestPrey = f;
                    }
                }
            }
            if (bestPrey != null) {
                float dx = bestPrey.x - player.x;
                float dy = bestPrey.y - player.y;
                float len = (float) Math.hypot(dx, dy);
                if (len > 1.0f) {
                    desiredX = dx / len;
                    desiredY = dy / len;
                }
            } else {
                desiredX = (float) Math.cos(worldTimeSeconds * 0.9f);
                desiredY = (float) Math.sin(worldTimeSeconds * 1.4f) * 0.6f;
            }
        } else if (inputHasTarget) {
            float dx = inputTargetX - player.x;
            float dy = inputTargetY - player.y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 8.0f) {
                float scale = Math.min(1.0f, dist / 95.0f);
                desiredX = (dx / dist) * scale;
                desiredY = (dy / dist) * scale;
            }
        } else {
            desiredX = inputDirX;
            desiredY = inputDirY;
        }

        boolean dashing = (inputDashHeld && (dashEnergy > 4.0f || oprek.infiniteDash));
        if (dashing && !oprek.infiniteDash) {
            dashEnergy = Math.max(0.0f, dashEnergy - dt * 52.0f);
        } else {
            dashEnergy = Math.min(100.0f, dashEnergy + dt * 26.0f);
        }

        float maxSpeed = player.species.baseSpeed * oprek.speedMultiplier;
        if (speedBoostTimer > 0.0f) {
            maxSpeed *= 1.42f;
        }
        if (dashing) {
            maxSpeed *= 1.55f;
        }

        float targetVx = desiredX * maxSpeed;
        float targetVy = desiredY * maxSpeed;
        float responsiveness = Math.min(1.0f, dt * 10.5f);
        player.vx += (targetVx - player.vx) * responsiveness;
        player.vy += (targetVy - player.vy) * responsiveness;

        if (Math.abs(player.vx) > 8.0f) {
            player.facingRight = player.vx >= 0.0f;
        }

        player.x = clamp(player.x + player.vx * dt, player.radius, WORLD_WIDTH - player.radius);
        player.y = clamp(player.y + player.vy * dt, SURFACE_Y, SEABED_Y - player.radius * 0.45f);
        player.tailPhase += dt * (6.0f + player.currentSpeed() * 0.045f);
    }

    private void updateNpcFish(float dt) {
        float effectiveMagnet = oprek.foodMagnetRadius;
        if (suctionActive) {
            effectiveMagnet = Math.max(effectiveMagnet, 285.0f);
        }

        for (int i = npcFish.size() - 1; i >= 0; i--) {
            FishEntity npc = npcFish.get(i);
            if (!npc.alive) {
                npcFish.remove(i);
                continue;
            }

            if (npc.species == SpeciesType.JELLYFISH) {
                npc.y -= npc.species.baseSpeed * dt * 0.65f;
                npc.x += (float) Math.sin(worldTimeSeconds * 2.2f + npc.id) * 28.0f * dt;
                npc.tailPhase += dt * 3.2f;
                if (npc.y < -60.0f) {
                    npc.y = SEABED_Y;
                }
            } else if (freezePredatorsTimer > 0.0f && !canPlayerEat(npc)) {
                npc.aiState = FishEntity.AiState.STUNNED;
                npc.vx *= 0.92f;
                npc.vy *= 0.92f;
                npc.x += npc.vx * dt;
                npc.y += npc.vy * dt;
            } else {
                updateNpcAi(npc, dt, effectiveMagnet);
            }

            // Cull far off-screen wanderers
            if (npc.x < -190.0f || npc.x > WORLD_WIDTH + 190.0f) {
                npcFish.remove(i);
                continue;
            }

            // Check player <-> NPC interaction
            float dx = npc.x - player.x;
            float dy = npc.y - player.y;
            float combined = (player.radius + npc.radius) * 0.76f;
            if (dx * dx + dy * dy <= combined * combined) {
                handlePlayerFishCollision(npc);
                if (!npc.alive) {
                    npcFish.remove(i);
                }
            }
        }
    }

    private void updateNpcAi(FishEntity npc, float dt, float magnetRadius) {
        npc.aiDecisionTimer -= dt;
        float dxToPlayer = player.x - npc.x;
        float dyToPlayer = player.y - npc.y;
        float distToPlayer = (float) Math.hypot(dxToPlayer, dyToPlayer);

        boolean edibleByPlayer = canPlayerEat(npc);
        if (edibleByPlayer && magnetRadius > 1.0f && distToPlayer < magnetRadius && distToPlayer > 1.0f) {
            // Suction / Oprek Food Magnet pulls edible fish right toward player's mouth!
            float pull = 390.0f;
            npc.vx = (dxToPlayer / distToPlayer) * pull;
            npc.vy = (dyToPlayer / distToPlayer) * pull;
        } else if (npc.aiDecisionTimer <= 0.0f) {
            npc.aiDecisionTimer = 0.28f + rng.nextFloat() * 0.45f;
            if (edibleByPlayer && distToPlayer < 210.0f) {
                npc.aiState = FishEntity.AiState.FLEE_PREDATOR;
                float inv = 1.0f / Math.max(1.0f, distToPlayer);
                float fleeSpeed = npc.species.baseSpeed * 1.18f;
                npc.vx = -dxToPlayer * inv * fleeSpeed;
                npc.vy = -dyToPlayer * inv * fleeSpeed;
            } else if (!edibleByPlayer && state == RunState.PLAYING && distToPlayer < 290.0f) {
                npc.aiState = FishEntity.AiState.CHASE_PREY;
                float inv = 1.0f / Math.max(1.0f, distToPlayer);
                float chaseSpeed = npc.species.baseSpeed * 0.94f;
                npc.vx = dxToPlayer * inv * chaseSpeed;
                npc.vy = dyToPlayer * inv * chaseSpeed;
            } else {
                npc.aiState = FishEntity.AiState.CRUISE;
                float dir = npc.vx >= 0.0f ? 1.0f : -1.0f;
                npc.vx = dir * npc.species.baseSpeed * (0.65f + rng.nextFloat() * 0.45f);
                npc.vy = (rng.nextFloat() - 0.5f) * npc.species.baseSpeed * 0.45f;
            }
        }

        npc.x += npc.vx * dt;
        npc.y = clamp(npc.y + npc.vy * dt, SURFACE_Y + 12.0f, SEABED_Y - npc.radius * 0.5f);
        if (Math.abs(npc.vx) > 4.0f) {
            npc.facingRight = npc.vx >= 0.0f;
        }
        npc.tailPhase += dt * (5.5f + npc.currentSpeed() * 0.04f);
        if (npc.chompTimer > 0.0f) {
            npc.chompTimer = Math.max(0.0f, npc.chompTimer - dt);
        }
    }

    public boolean canPlayerEat(FishEntity target) {
        if (target == null || target.species == SpeciesType.JELLYFISH) {
            return oprek.eatAnythingApex;
        }
        return player.foodTier >= target.foodTier || oprek.eatAnythingApex;
    }

    private void handlePlayerFishCollision(FishEntity npc) {
        if (canPlayerEat(npc)) {
            npc.alive = false;
            player.triggerChomp();

            // Fill Feeding Frenzy combo bar
            float prevMultiplier = frenzyMultiplier;
            frenzyMeter = Math.min(100.0f, frenzyMeter + 24.0f);
            updateFrenzyMultiplier();
            if (frenzyMultiplier > prevMultiplier && frenzyMultiplier >= 2) {
                emitSfx(SfxKind.FRENZY_FANFARE);
            } else {
                emitSfx(npc.radius >= 38.0f ? SfxKind.CHOMP_BIG : SfxKind.CHOMP_SMALL);
            }

            int pts = npc.species.scoreValue * frenzyMultiplier;
            if (state == RunState.PLAYING) {
                score += pts;
                if (score > highScore) {
                    highScore = score;
                }
                int foodUnits = Math.max(1, npc.foodTier);
                stageFoodEaten += foodUnits;
                totalFoodBank += foodUnits;
                popups.add(new FloatingPopup(npc.x, npc.y, "+" + pts, frenzyMultiplier >= 3 ? 0xFFFFD54F : 0xFFA7F3D0));
                checkStageProgression();
            }
            return;
        }

        // Jellyfish stuns player without costing a life
        if (npc.species == SpeciesType.JELLYFISH) {
            if (!oprek.godMode && player.invulnerableTimer <= 0.0f) {
                player.stunTimer = 1.15f;
                player.invulnerableTimer = 1.8f;
                emitSfx(SfxKind.PLAYER_HURT);
                popups.add(new FloatingPopup(player.x, player.y - 30.0f, "ZAP! STUNNED!", 0xFFFF80FF));
            }
            return;
        }

        // Larger predator bites player!
        npc.triggerChomp();
        damagePlayer("CHOMPED BY " + npc.species.displayName.toUpperCase() + "!");
    }

    private void damagePlayer(String message) {
        if (state != RunState.PLAYING || oprek.godMode || player.invulnerableTimer > 0.0f) {
            return;
        }
        if (hasBubbleShield) {
            hasBubbleShield = false;
            player.invulnerableTimer = 2.0f;
            emitSfx(SfxKind.BUBBLE_POP);
            popups.add(new FloatingPopup(player.x, player.y - 36.0f, "SHIELD SAVED YOU!", 0xFF64DFDF));
            return;
        }

        lives--;
        screenShake = 0.42f;
        frenzyMeter = 0.0f;
        frenzyMultiplier = 1;
        emitSfx(SfxKind.PLAYER_HURT);
        popups.add(new FloatingPopup(player.x, player.y - 40.0f, message, 0xFFFF5252));

        if (lives <= 0) {
            state = RunState.GAME_OVER;
        } else {
            player.x = WORLD_WIDTH * 0.5f;
            player.y = WORLD_HEIGHT * 0.45f;
            player.vx = 0.0f;
            player.vy = 0.0f;
            player.invulnerableTimer = 2.8f;
        }
    }

    private void checkStageProgression() {
        int tier1Goal = currentStage.foodGoalToClear / 3;
        int tier2Goal = (currentStage.foodGoalToClear * 2) / 3;

        if (growthTierIndex == 0 && stageFoodEaten >= tier1Goal) {
            growthTierIndex = 1;
            recomputePlayerStats();
            screenShake = 0.25f;
            emitSfx(SfxKind.GROW_UP);
            popups.add(new FloatingPopup(player.x, player.y - player.radius, "GROWTH! MEDIUM SIZE!", 0xFFFFEA00));
        } else if (growthTierIndex == 1 && stageFoodEaten >= tier2Goal) {
            growthTierIndex = 2;
            recomputePlayerStats();
            screenShake = 0.32f;
            emitSfx(SfxKind.GROW_UP);
            popups.add(new FloatingPopup(player.x, player.y - player.radius, "GROWTH! APEX SIZE!", 0xFFFFAB00));
        }

        if (stageFoodEaten >= currentStage.foodGoalToClear) {
            state = RunState.STAGE_CLEARED;
            score += 500 * currentStage.chapterIndex;
            if (score > highScore) {
                highScore = score;
            }
            emitSfx(SfxKind.STAGE_CLEAR);
        }
    }

    private void updateFrenzyMultiplier() {
        if (frenzyMeter >= 90.0f) {
            frenzyMultiplier = 6;
        } else if (frenzyMeter >= 70.0f) {
            frenzyMultiplier = 4;
        } else if (frenzyMeter >= 45.0f) {
            frenzyMultiplier = 3;
        } else if (frenzyMeter >= 22.0f) {
            frenzyMultiplier = 2;
        } else {
            frenzyMultiplier = 1;
        }
    }

    private void updateBonuses(float dt) {
        float magnet = Math.max(oprek.foodMagnetRadius, suctionActive ? 285.0f : 0.0f);
        for (int i = bonuses.size() - 1; i >= 0; i--) {
            BonusItem item = bonuses.get(i);
            item.phase += dt * 3.0f;

            if (item.kind == BonusItem.Kind.OYSTER_PEARL_WHITE || item.kind == BonusItem.Kind.OYSTER_PEARL_BLACK) {
                item.stateTimer += dt;
                item.oysterOpen = (item.stateTimer % 6.0f) < 3.8f;
            } else if (item.kind == BonusItem.Kind.NAVAL_MINE) {
                item.y += (float) Math.sin(item.phase * 0.7f) * 18.0f * dt;
            } else {
                // Powerup bubble rises toward water surface
                if (magnet > 1.0f) {
                    float dx = player.x - item.x;
                    float dy = player.y - item.y;
                    float d = (float) Math.hypot(dx, dy);
                    if (d < magnet && d > 1.0f) {
                        item.x += (dx / d) * 320.0f * dt;
                        item.y += (dy / d) * 320.0f * dt;
                    }
                }
                item.y -= 54.0f * dt;
                item.x += (float) Math.sin(item.phase) * 20.0f * dt;
                if (item.y < -40.0f) {
                    bonuses.remove(i);
                    continue;
                }
            }

            float dx = item.x - player.x;
            float dy = item.y - player.y;
            float hitR = player.radius + item.radius;
            if (dx * dx + dy * dy <= hitR * hitR) {
                if (handleBonusTouch(item)) {
                    bonuses.remove(i);
                }
            }
        }
    }

    private boolean handleBonusTouch(BonusItem item) {
        switch (item.kind) {
            case NAVAL_MINE:
                emitSfx(SfxKind.MINE_EXPLODE);
                damagePlayer("BOOM! NAVAL MINE!");
                return true;
            case OYSTER_PEARL_WHITE:
            case OYSTER_PEARL_BLACK:
                if (!item.oysterOpen) {
                    return false;
                }
                boolean black = item.kind == BonusItem.Kind.OYSTER_PEARL_BLACK;
                int bonusPts = black ? 1000 : 250;
                score += bonusPts * frenzyMultiplier;
                stageFoodEaten += black ? 8 : 3;
                totalFoodBank += black ? 8 : 3;
                emitSfx(SfxKind.PEARL_CHIME);
                popups.add(new FloatingPopup(
                        item.x,
                        item.y - 24.0f,
                        black ? "BLACK PEARL +1000!" : "PEARL +250!",
                        0xFFE0AAFF));
                checkStageProgression();
                return true;
            case SPEED_BUBBLE:
                speedBoostTimer = 8.0f;
                emitSfx(SfxKind.BUBBLE_POP);
                popups.add(new FloatingPopup(item.x, item.y, "TURBO FINS!", 0xFF4CC9F0));
                return true;
            case FRENZY_BUBBLE:
                frenzyMeter = 100.0f;
                updateFrenzyMultiplier();
                emitSfx(SfxKind.FRENZY_FANFARE);
                popups.add(new FloatingPopup(item.x, item.y, "FEEDING FRENZY!!", 0xFFFFB703));
                return true;
            case FREEZE_BUBBLE:
                freezePredatorsTimer = 6.5f;
                emitSfx(SfxKind.BUBBLE_POP);
                popups.add(new FloatingPopup(item.x, item.y, "ICE STUN!", 0xFF90E0EF));
                return true;
            case SHIELD_BUBBLE:
                hasBubbleShield = true;
                emitSfx(SfxKind.BUBBLE_POP);
                popups.add(new FloatingPopup(item.x, item.y, "BUBBLE SHIELD!", 0xFF80FFDB));
                return true;
            case EXTRA_LIFE_BUBBLE:
                lives = Math.min(9, lives + 1);
                emitSfx(SfxKind.GROW_UP);
                popups.add(new FloatingPopup(item.x, item.y, "1-UP EXTRA LIFE!", 0xFF69F0AE));
                return true;
            case STAR_BUBBLE:
            default:
                score += 150 * frenzyMultiplier;
                emitSfx(SfxKind.BUBBLE_POP);
                popups.add(new FloatingPopup(item.x, item.y, "STAR +150!", 0xFFFFF59D));
                return true;
        }
    }

    private void spawnRandomNpc(boolean anywhereOnScreen) {
        SpeciesType[] pool = currentStage.spawnPool;
        // Weight smaller edible prey slightly higher so player never starves
        int roll = rng.nextInt(100);
        int idx;
        if (roll < 42) {
            idx = 0;
        } else if (roll < 68) {
            idx = Math.min(pool.length - 1, 1);
        } else if (roll < 84) {
            idx = Math.min(pool.length - 1, 2);
        } else if (roll < 94) {
            idx = Math.min(pool.length - 1, 3);
        } else {
            idx = pool.length - 1;
        }
        SpeciesType sp = pool[idx];

        boolean fromLeft = rng.nextBoolean();
        float x = anywhereOnScreen
                ? (80.0f + rng.nextFloat() * (WORLD_WIDTH - 160.0f))
                : (fromLeft ? -sp.baseRadius - 25.0f : WORLD_WIDTH + sp.baseRadius + 25.0f);
        float y = SURFACE_Y + 30.0f + rng.nextFloat() * (SEABED_Y - SURFACE_Y - 80.0f);

        // Keep initial spawn away from player start center
        if (anywhereOnScreen && player != null && distSq(x, y, player.x, player.y) < 220.0f * 220.0f) {
            x = x < WORLD_WIDTH * 0.5f ? 90.0f : WORLD_WIDTH - 90.0f;
        }

        FishEntity npc = new FishEntity(nextEntityId++, sp, false, x, y, sp.baseFoodTier, sp.baseRadius);
        float dir = fromLeft ? 1.0f : -1.0f;
        npc.vx = dir * sp.baseSpeed * (0.75f + rng.nextFloat() * 0.35f);
        npc.facingRight = dir > 0.0f;
        npcFish.add(npc);
    }

    private void spawnJellyfish() {
        float x = 140.0f + rng.nextFloat() * (WORLD_WIDTH - 280.0f);
        float y = 200.0f + rng.nextFloat() * (SEABED_Y - 220.0f);
        FishEntity jelly = new FishEntity(
                nextEntityId++,
                SpeciesType.JELLYFISH,
                false,
                x,
                y,
                SpeciesType.JELLYFISH.baseFoodTier,
                SpeciesType.JELLYFISH.baseRadius);
        npcFish.add(jelly);
    }

    private void spawnOysterAt(float x) {
        BonusItem.Kind kind = rng.nextInt(4) == 0
                ? BonusItem.Kind.OYSTER_PEARL_BLACK
                : BonusItem.Kind.OYSTER_PEARL_WHITE;
        BonusItem oyster = new BonusItem(nextEntityId++, kind, x, SEABED_Y - 12.0f, 30.0f);
        oyster.stateTimer = rng.nextFloat() * 3.0f;
        bonuses.add(oyster);
    }

    private void spawnRandomBubble() {
        BonusItem.Kind[] bubbleKinds = new BonusItem.Kind[] {
            BonusItem.Kind.STAR_BUBBLE,
            BonusItem.Kind.SPEED_BUBBLE,
            BonusItem.Kind.FRENZY_BUBBLE,
            BonusItem.Kind.FREEZE_BUBBLE,
            BonusItem.Kind.SHIELD_BUBBLE,
            BonusItem.Kind.EXTRA_LIFE_BUBBLE
        };
        BonusItem.Kind picked = bubbleKinds[rng.nextInt(bubbleKinds.length)];
        float bx = 120.0f + rng.nextFloat() * (WORLD_WIDTH - 240.0f);
        BonusItem b = new BonusItem(nextEntityId++, picked, bx, SEABED_Y - 16.0f, 22.0f);
        bonuses.add(b);
    }

    private void updatePopups(float dt) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            FloatingPopup p = popups.get(i);
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0.0f) {
                popups.remove(i);
            }
        }
    }

    private static float distSq(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
