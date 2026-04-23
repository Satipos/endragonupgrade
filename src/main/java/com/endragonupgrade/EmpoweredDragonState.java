package com.endragonupgrade;

import java.util.UUID;

/**
 * Per-dragon runtime state for an empowered Ender Dragon.
 * All timers are measured in server ticks (20 tps).
 */
public final class EmpoweredDragonState {
    public static final int FREEZE_TICKS = 60; // 3 seconds at 20tps

    public final UUID dragonId;
    public final float maxHealth;

    public int stage = 0;            // 0 = frozen/animating, 1 / 2 / 3 after freeze
    public int freezeTicksLeft;
    public int chargeCooldown = 0;   // remaining ticks before next strafe/charge bias
    public int endermiteCooldown = 8 * 20;    // stage 2: summon endermites every 8s
    public int fireballCooldown = 3 * 20;     // stage 2: purple fireball every ~3s
    public int shockwaveCooldown = 12 * 20;   // stage 3: shockwave every 12s
    public int tickCounter = 0;

    public EmpoweredDragonState(UUID dragonId, float maxHealth) {
        this.dragonId = dragonId;
        this.maxHealth = maxHealth;
        this.freezeTicksLeft = FREEZE_TICKS;
    }

    public boolean isFrozen() {
        return freezeTicksLeft > 0;
    }
}
