package com.risqi.nativeports.feedingfrenzy.engine;

/**
 * Interactive ocean world pickups, seabed oysters, pearls, and naval mine hazards.
 */
public final class BonusItem {

    public enum Kind {
        STAR_BUBBLE,
        SPEED_BUBBLE,
        FRENZY_BUBBLE,
        FREEZE_BUBBLE,
        SHIELD_BUBBLE,
        EXTRA_LIFE_BUBBLE,
        OYSTER_PEARL_WHITE,
        OYSTER_PEARL_BLACK,
        NAVAL_MINE
    }

    public final int id;
    public final Kind kind;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public float radius;
    public float phase;
    public float stateTimer;
    public boolean oysterOpen = true;
    public boolean collected = false;

    public BonusItem(int id, Kind kind, float x, float y, float radius) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.radius = radius;
    }
}
