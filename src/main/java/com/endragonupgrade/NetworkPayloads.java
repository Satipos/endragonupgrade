package com.endragonupgrade;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Clientbound custom packet payload types used by the mod.
 * Registered on both ends (common entrypoint) via {@link #registerCommon()}.
 */
public final class NetworkPayloads {
    private NetworkPayloads() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, path);
    }

    /**
     * Sent when a dragon is about to become empowered — starts the 3-second freeze animation
     * on the client (visual effects, sound trigger from the server).
     */
    public record EmpowerStartPayload(int dragonEntityId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<EmpowerStartPayload> TYPE =
            new Type<>(id("empower_start"));
        public static final StreamCodec<FriendlyByteBuf, EmpowerStartPayload> CODEC =
            CustomPacketPayload.codec(EmpowerStartPayload::write, EmpowerStartPayload::new);

        public EmpowerStartPayload(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(dragonEntityId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent periodically (and on change) while an empowered dragon is alive.
     * Drives the custom HP bar on the client.
     */
    public record HealthUpdatePayload(int dragonEntityId, float health, float maxHealth, int stage)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<HealthUpdatePayload> TYPE =
            new Type<>(id("health_update"));
        public static final StreamCodec<FriendlyByteBuf, HealthUpdatePayload> CODEC =
            CustomPacketPayload.codec(HealthUpdatePayload::write, HealthUpdatePayload::new);

        public HealthUpdatePayload(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(dragonEntityId);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeVarInt(stage);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent when the dragon transitions to a new stage. Triggers the screen shake on the client
     * and is ordered before the user-visible chat message from the server.
     */
    public record StageTransitionPayload(int newStage) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StageTransitionPayload> TYPE =
            new Type<>(id("stage_transition"));
        public static final StreamCodec<FriendlyByteBuf, StageTransitionPayload> CODEC =
            CustomPacketPayload.codec(StageTransitionPayload::write, StageTransitionPayload::new);

        public StageTransitionPayload(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(newStage);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent when the empowered dragon dies or the fight ends — hides the custom HP bar client-side.
     */
    public record ClearPayload() implements CustomPacketPayload {
        public static final ClearPayload INSTANCE = new ClearPayload();
        public static final CustomPacketPayload.Type<ClearPayload> TYPE =
            new Type<>(id("clear"));
        public static final StreamCodec<FriendlyByteBuf, ClearPayload> CODEC =
            StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Registers all payload types on the clientbound play registry.
     * Must be called on both physical sides.
     */
    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(EmpowerStartPayload.TYPE, EmpowerStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HealthUpdatePayload.TYPE, HealthUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StageTransitionPayload.TYPE, StageTransitionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClearPayload.TYPE, ClearPayload.CODEC);
    }
}
