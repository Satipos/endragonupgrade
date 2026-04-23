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
     * Sent by the server after the activation phrase is recognised — tells the client to open
     * the difficulty-selection menu.
     */
    public record OpenMenuPayload() implements CustomPacketPayload {
        public static final OpenMenuPayload INSTANCE = new OpenMenuPayload();
        public static final CustomPacketPayload.Type<OpenMenuPayload> TYPE =
            new Type<>(id("open_menu"));
        public static final StreamCodec<FriendlyByteBuf, OpenMenuPayload> CODEC =
            StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent by the client when the player selects a difficulty in the menu.
     */
    public record SelectDifficultyPayload(int difficultyId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SelectDifficultyPayload> TYPE =
            new Type<>(id("select_difficulty"));
        public static final StreamCodec<FriendlyByteBuf, SelectDifficultyPayload> CODEC =
            CustomPacketPayload.codec(SelectDifficultyPayload::write, SelectDifficultyPayload::new);

        public SelectDifficultyPayload(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(difficultyId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Tells the client which difficulty the currently-tracked dragon is on so the HP bar can pick
     * the right colourway / extra flair.
     */
    public record DifficultyInfoPayload(int difficultyId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DifficultyInfoPayload> TYPE =
            new Type<>(id("difficulty_info"));
        public static final StreamCodec<FriendlyByteBuf, DifficultyInfoPayload> CODEC =
            CustomPacketPayload.codec(DifficultyInfoPayload::write, DifficultyInfoPayload::new);

        public DifficultyInfoPayload(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(difficultyId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Registers all payload types on the appropriate play registries.
     * Must be called on both physical sides.
     */
    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(EmpowerStartPayload.TYPE, EmpowerStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HealthUpdatePayload.TYPE, HealthUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StageTransitionPayload.TYPE, StageTransitionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClearPayload.TYPE, ClearPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.TYPE, OpenMenuPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DifficultyInfoPayload.TYPE, DifficultyInfoPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SelectDifficultyPayload.TYPE, SelectDifficultyPayload.CODEC);
    }
}
