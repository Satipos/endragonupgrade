package com.endragonupgrade;

import java.util.UUID;

/**
 * Per-dragon runtime state for an empowered Ender Dragon.
 * All timers are measured in server ticks (20 tps).
 *
 * <p>Tuned for "3× harder" compared to v1.0.0:
 *  - triple max HP,
 *  - ~3× faster attack cadence,
 *  - larger/stronger shockwave,
 *  - constant passive damage + slowness aura while near the dragon (stage 2+).
 */
public final class EmpoweredDragonState {
    public static final int FREEZE_TICKS = 100; // 5 seconds at 20tps
    public static final float EMPOWERED_MAX_HEALTH = 600.0f; // 3× vanilla 200

    public final UUID dragonId;
    public final float originalMaxHealth;
    public final Difficulty difficulty;

    public int stage = 0;               // 0 = freezing, 1 / 2 / 3 after freeze finishes
    public int freezeTicksLeft;
    public int chargeCooldown = 0;      // faster charges (every ~4s vs vanilla ~15s)
    public int endermiteCooldown = 60;  // stage 2 (3s)
    public int fireballCooldown = 40;   // stage 2 (2s)
    public int shockwaveCooldown = 80;  // stage 3 (4s)
    public int auraCooldown = 40;       // passive aura every 2s (stage 2+)
    public int tickCounter = 0;
    public boolean shockwaveTell = false; // windup signalling shockwave in 1s

    // v2.0: periodic landings so players get more melee openings.
    public int sitScheduleCooldown = 300;     // first landing triggers ~15s after freeze ends
    public int sitDurationTicksLeft = 0;      // >0 while dragon is being held on the podium
    public static final int SIT_INTERVAL_TICKS = 300;   // schedule next landing ~15s after getting up
    public static final int SIT_HOLD_TICKS = 160;       // sit for ~8s before forcibly taking off

    // Stage 4 (VERY_HARD only) tickers
    public int nova4Cooldown = 100;
    public int pillar4Cooldown = 140;

    // Stage 5/6 (EXTREME only) tickers
    public int rainCooldown = 60;
    public int cloneCooldown = 160;

    // Stage 7/8/9/10 (IMPOSSIBLE only) tickers
    public int blackholeCooldown = 300;   // signature — stage 7
    public int meteorSwarmCooldown = 200; // signature — stage 9
    public int realityTearCooldown = 120; // signature — stage 10
    public int voidWaveCooldown = 60;     // stage 8 — repeating void ring shockwaves
    public int phantomStrikeCooldown = 80;// stage 8/9/10 — teleport strikes

    // Stage 7 Black Hole — when > 0, a singularity is active at (bhX, bhY, bhZ).
    public int blackholeTicks = 0;
    public double bhX, bhY, bhZ;

    // Chain-of-God freeze: while > 0 the dragon is held still (no attacks, no movement, no knockback).
    public int chainFreezeTicksLeft = 0;
    public static final int CHAIN_FREEZE_TICKS = 200; // 10 s at 20tps

    // Shield HP — virtual health beyond vanilla 1024 cap. For IMPOSSIBLE difficulty the
    // dragon is effectively 5000 HP: real max_health stays at 1024, extra (5000-1024) is
    // absorbed by this shield before real damage leaks through. Updated via ServerLivingEntityEvents.
    public static final float VANILLA_MAX_HEALTH_CAP = 1024.0f;
    public float shieldHp = 0f;
    public float shieldMaxHp = 0f;

    public EmpoweredDragonState(UUID dragonId, float originalMaxHealth, Difficulty difficulty) {
        this.dragonId = dragonId;
        this.originalMaxHealth = originalMaxHealth;
        this.difficulty = difficulty;
        this.freezeTicksLeft = FREEZE_TICKS;
    }

    public boolean isFrozen() {
        return freezeTicksLeft > 0;
    }
}
