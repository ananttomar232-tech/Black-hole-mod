package com.ananttomar.livingblackhole.client;

import com.ananttomar.livingblackhole.client.fx.ChunkEffects;
import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import com.ananttomar.livingblackhole.network.CinematicPacket;
import com.ananttomar.livingblackhole.network.SyncSingularityPacket;

/** Client-only packet entry points. Only ever class-loaded through DistExecutor. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleSync(SyncSingularityPacket msg) {
        ClientSingularityState.set(msg.events);
    }

    public static void handleChunkFx(ChunkFxPacket msg) {
        ChunkEffects.play(msg);
    }

    public static void handleCinematic(CinematicPacket msg) {
        ClientEffects.onCinematic(msg);
    }
}
