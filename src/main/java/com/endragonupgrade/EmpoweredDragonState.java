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

    public EmpoweredDragonState(UUID dragonId, float originalMaxHealth) {
        this.dragonId = dragonId;
        this.originalMaxHealth = originalMaxHealth;
        this.freezeTicksLeft = FREEZE_TICKS;
    }

    public boolean isFrozen() {
        return freezeTicksLeft > 0;
    }
}
