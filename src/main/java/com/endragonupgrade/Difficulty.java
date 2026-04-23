package com.endragonupgrade;

/**
 * Picks between the stock fight tuning and the "очень сложная" variant added in v2.5.
 */
public enum Difficulty {
    /** Default empowered dragon: 600 HP, 3 stages, tuning unchanged from v2.0. */
    HARD(1.0f, 600.0f, 3, "§5§lСложная"),
    /** Very hard: double HP, stage IV, doubled attack cadence, custom HUD flair. */
    VERY_HARD(2.0f, 1200.0f, 4, "§4§lОчень сложная");

    public final float damageMul;
    public final float maxHp;
    public final int stageCount;
    public final String displayName;

    Difficulty(float damageMul, float maxHp, int stageCount, String displayName) {
        this.damageMul = damageMul;
        this.maxHp = maxHp;
        this.stageCount = stageCount;
        this.displayName = displayName;
    }

    public static Difficulty byId(int id) {
        Difficulty[] values = values();
        if (id < 0 || id >= values.length) return HARD;
        return values[id];
    }

    public int id() {
        return ordinal();
    }
}
