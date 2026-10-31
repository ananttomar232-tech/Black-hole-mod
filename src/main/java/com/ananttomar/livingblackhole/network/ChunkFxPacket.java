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

/** Visuals of a Pocket Black Hole swallowing a chunk, or a Compressed Chunk being set back down. */
public final class ChunkFxPacket {
    public static final int CAPTURE = 0;
    public static final int PLACE = 1;
    public static final int MAX_ENTRIES = 450;

    /** A sampled block of the region: where it was and what it looked like. */
    public record Entry(BlockPos pos, int stateId) {
    }

    public final int type;
    public final int shooterId;
    public final Vec3 hand;
    public final BlockPos min;
    public final List<Entry> entries;

    public ChunkFxPacket(int type, int shooterId, Vec3 hand, BlockPos min, List<Entry> entries) {
        this.type = type;
        this.shooterId = shooterId;
        this.hand = hand;
        this.min = min;
        this.entries = entries;
    }

    public Vec3 center() {
        return Vec3.atLowerCornerOf(min).add(8.0, 8.0, 8.0);
    }

    public static void encode(ChunkFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type);
        buf.writeVarInt(msg.shooterId);
        buf.writeDouble(msg.hand.x);
        buf.writeDouble(msg.hand.y);
        buf.writeDouble(msg.hand.z);
        buf.writeBlockPos(msg.min);
        int n = Math.min(MAX_ENTRIES, msg.entries.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = msg.entries.get(i);
            buf.writeLong(e.pos().asLong());
            buf.writeVarInt(e.stateId());
        }
    }

    public static ChunkFxPacket decode(FriendlyByteBuf buf) {
        int type = buf.readVarInt();
        int shooter = buf.readVarInt();
        Vec3 hand = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        BlockPos min = buf.readBlockPos();
        int n = Math.min(MAX_ENTRIES, buf.readVarInt());
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(BlockPos.of(buf.readLong()), buf.readVarInt()));
        }
        return new ChunkFxPacket(type, shooter, hand, min, list);
    }

    public static void handle(ChunkFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleChunkFx(msg));
    }
}
