package com.ananttomar.livingblackhole.network;

import com.ananttomar.livingblackhole.client.ClientPacketHandler;
import com.ananttomar.livingblackhole.singularity.StructureData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A Pocket Black Hole swallowing a chunk, or a Compressed Chunk being unfolded. Carries the whole
 * structure so every watching client can draw the real blocks shrinking into (or growing out of)
 * the shooter's hand.
 */
public final class ChunkFxPacket {
    public static final int CAPTURE = 0;
    public static final int PLACE = 1;
    /** Length of the shrink-into-the-hand animation. */
    public static final int CAPTURE_TICKS = 34;

    public final int type;
    public final int shooterId;
    public final Vec3 hand;
    /** World position of the structure's local origin (where it was taken from, or where it will land). */
    public final BlockPos min;
    public final int duration;
    public final StructureData structure;

    public ChunkFxPacket(int type, int shooterId, Vec3 hand, BlockPos min, int duration, StructureData structure) {
        this.type = type;
        this.shooterId = shooterId;
        this.hand = hand;
        this.min = min;
        this.duration = duration;
        this.structure = structure;
    }

    /** Centre of the 16x16x16 region in world space. */
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
        buf.writeVarInt(msg.duration);
        msg.structure.write(buf);
    }

    public static ChunkFxPacket decode(FriendlyByteBuf buf) {
        int type = buf.readVarInt();
        int shooter = buf.readVarInt();
        Vec3 hand = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        BlockPos min = buf.readBlockPos();
        int duration = buf.readVarInt();
        StructureData structure = StructureData.read(buf);
        return new ChunkFxPacket(type, shooter, hand, min, duration, structure);
    }

    public static void handle(ChunkFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleChunkFx(msg));
    }
}
