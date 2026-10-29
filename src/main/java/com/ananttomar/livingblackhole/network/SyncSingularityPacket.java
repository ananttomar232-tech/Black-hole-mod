package com.ananttomar.livingblackhole.network;

import com.ananttomar.livingblackhole.client.ClientPacketHandler;
import com.ananttomar.livingblackhole.singularity.SingularityEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Full snapshot of every active singularity event, for the HUD. */
public final class SyncSingularityPacket {
    public final List<SingularityEvent> events;

    public SyncSingularityPacket(List<SingularityEvent> events) {
        this.events = events;
    }

    public static void encode(SyncSingularityPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.events.size());
        for (SingularityEvent ev : msg.events) {
            ev.write(buf);
        }
    }

    public static SyncSingularityPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<SingularityEvent> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(SingularityEvent.read(buf));
        }
        return new SyncSingularityPacket(list);
    }

    public static void handle(SyncSingularityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
    }
}
