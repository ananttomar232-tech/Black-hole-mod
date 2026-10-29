package com.ananttomar.livingblackhole.network;

import com.ananttomar.livingblackhole.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Visuals of one Pocket Black Hole shot: the beam and everything it swallowed. */
public final class BeamFxPacket {
    public static final int MAX_ENTRIES = 400;

    /** A swallowed block (state id) or entity (state id -1). */
    public record Absorbed(BlockPos pos, int stateId) {
    }

    public final int shooterId;
    public final Vec3 start;
    public final Vec3 end;
    public final float radius;
    public final float power;
    public final List<Absorbed> absorbed;

    public BeamFxPacket(int shooterId, Vec3 start, Vec3 end, float radius, float power, List<Absorbed> absorbed) {
        this.shooterId = shooterId;
        this.start = start;
        this.end = end;
        this.radius = radius;
        this.power = power;
        this.absorbed = absorbed;
    }

    public static void encode(BeamFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.shooterId);
        buf.writeDouble(msg.start.x);
        buf.writeDouble(msg.start.y);
        buf.writeDouble(msg.start.z);
        buf.writeDouble(msg.end.x);
        buf.writeDouble(msg.end.y);
        buf.writeDouble(msg.end.z);
        buf.writeFloat(msg.radius);
        buf.writeFloat(msg.power);
        int n = Math.min(MAX_ENTRIES, msg.absorbed.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Absorbed a = msg.absorbed.get(i);
            buf.writeLong(a.pos().asLong());
            buf.writeVarInt(a.stateId() + 1);
        }
    }

    public static BeamFxPacket decode(FriendlyByteBuf buf) {
        int shooter = buf.readVarInt();
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float radius = buf.readFloat();
        float power = buf.readFloat();
        int n = Math.min(MAX_ENTRIES, buf.readVarInt());
        List<Absorbed> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Absorbed(BlockPos.of(buf.readLong()), buf.readVarInt() - 1));
        }
        return new BeamFxPacket(shooter, start, end, radius, power, list);
    }

    public static void handle(BeamFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleBeam(msg));
    }
}
