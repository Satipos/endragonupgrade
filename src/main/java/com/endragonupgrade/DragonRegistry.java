package com.endragonupgrade;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tracks empowered Ender Dragons during a server's lifetime.
 * State is NOT persisted across restarts — empowerment is a runtime fight mechanic.
 */
public final class DragonRegistry {
    private static final Map<MinecraftServer, DragonRegistry> INSTANCES = new WeakHashMap<>();

    private final Map<UUID, EmpoweredDragonState> states = new HashMap<>();

    private DragonRegistry() {
    }

    public static DragonRegistry get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new DragonRegistry());
    }

    public boolean isEmpowered(UUID id) {
        return states.containsKey(id);
    }

    public EmpoweredDragonState state(EnderDragon dragon) {
        return states.get(dragon.getUUID());
    }

    public EmpoweredDragonState empower(EnderDragon dragon, Difficulty difficulty) {
        EmpoweredDragonState state = new EmpoweredDragonState(dragon.getUUID(), dragon.getMaxHealth(), difficulty);
        states.put(dragon.getUUID(), state);
        return state;
    }

    public void remove(UUID id) {
        states.remove(id);
    }

    public Iterable<Map.Entry<UUID, EmpoweredDragonState>> entries() {
        return states.entrySet();
    }
}
