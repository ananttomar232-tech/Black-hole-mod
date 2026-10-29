package com.ananttomar.livingblackhole.network;

import com.ananttomar.livingblackhole.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Triggers a scripted client-side cinematic moment (shake, flash, glitch, title cards). */
public final class CinematicPacket {
    public static final int ESCAPE = 0;
    public static final int COLLAPSE_START = 1;
    public static final int DETONATION = 2;
    public static final int NEUTRALIZED = 3;
    public static final int BOMB_CAPTURED = 4;
    public static final int FRAGMENT_SECURED = 5;
    public static final int VANISHED = 6;

    public final int type;
    public final Vec3 pos;
    public final float value;

    public CinematicPacket(int type, Vec3 pos, float value) {
        this.type = type;
        this.pos = pos;
        this.value = value;
    }

    public static void encode(CinematicPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type);
        buf.writeDouble(msg.pos.x);
        buf.writeDouble(msg.pos.y);
        buf.writeDouble(msg.pos.z);
        buf.writeFloat(msg.value);
    }

    public static CinematicPacket decode(FriendlyByteBuf buf) {
        return new CinematicPacket(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat());
    }

    public static void handle(CinematicPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleCinematic(msg));
    }
}
