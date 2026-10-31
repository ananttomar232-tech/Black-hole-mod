package com.ananttomar.livingblackhole.network;

import com.ananttomar.livingblackhole.LivingBlackHole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            LivingBlackHole.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(SyncSingularityPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSingularityPacket::encode)
                .decoder(SyncSingularityPacket::decode)
                .consumerMainThread(SyncSingularityPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChunkFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChunkFxPacket::encode)
                .decoder(ChunkFxPacket::decode)
                .consumerMainThread(ChunkFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(CinematicPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CinematicPacket::encode)
                .decoder(CinematicPacket::decode)
                .consumerMainThread(CinematicPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    public static void sendToLevel(ServerLevel level, Object message) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), message);
    }

    public static void sendToTrackingAndSelf(Entity entity, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
    }
}
