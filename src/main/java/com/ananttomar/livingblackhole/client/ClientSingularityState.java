package com.ananttomar.livingblackhole.client;

import com.ananttomar.livingblackhole.singularity.SingularityEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Client mirror of every active singularity event, fed by {@code SyncSingularityPacket}. */
public final class ClientSingularityState {
    private static List<SingularityEvent> events = List.of();

    private ClientSingularityState() {
    }

    public static void set(List<SingularityEvent> list) {
        events = List.copyOf(list);
    }

    public static List<SingularityEvent> events() {
        return events;
    }

    public static boolean active() {
        return !events.isEmpty();
    }

    public static void clear() {
        events = List.of();
    }

    /** The event the HUD should focus: nearest in the player's dimension, else the oldest. */
    @Nullable
    public static SingularityEvent focus(@Nullable Player player) {
        if (events.isEmpty()) {
            return null;
        }
        if (player == null) {
            return events.get(0);
        }
        SingularityEvent best = null;
        double bestDist = Double.MAX_VALUE;
        for (SingularityEvent ev : events) {
            if (!ev.dimension.equals(player.level().dimension())) {
                continue;
            }
            double d = ev.center.distanceToSqr(player.position());
            if (d < bestDist) {
                bestDist = d;
                best = ev;
            }
        }
        return best != null ? best : events.get(0);
    }
}
