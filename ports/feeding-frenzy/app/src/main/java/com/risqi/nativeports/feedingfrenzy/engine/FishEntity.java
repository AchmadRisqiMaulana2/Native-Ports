package com.risqi.nativeports.feedingfrenzy.engine;

/**
 * Dynamic fish entity (player hero or AI marine creature) with tail kinematics & chomp state.
 */
public final class FishEntity {

    public enum AiState {
        PLAYER_CONTROLLED,
        CRUISE,
        SCHOOLING,
        CHASE_PREY,
        FLEE_PREDATOR,
        STUNNED
    }

    public final int id;
    public SpeciesType species;
    public boolean isPlayer;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public boolean facingRight = true;

    public int foodTier;
    public float radius;
    public float tailPhase;
    public float chompTimer;
    public float stunTimer;
    public float invulnerableTimer;
    public float aiDecisionTimer;
    public AiState aiState = AiState.CRUISE;
    public boolean alive = true;

    public FishEntity(
            int id,
            SpeciesType species,
            boolean isPlayer,
            float x,
            float y,
            int foodTier,
            float radius) {
        this.id = id;
        this.species = species;
        this.isPlayer = isPlayer;
        this.x = x;
        this.y = y;
        this.foodTier = foodTier;
        this.radius = radius;
    }

    public void triggerChomp() {
        this.chompTimer = 0.22f;
    }

    public float currentSpeed() {
        return (float) Math.hypot(vx, vy);
    }
}
