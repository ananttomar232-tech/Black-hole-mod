package com.ananttomar.livingblackhole.singularity;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Compressed Chunks being unfolded. The structure leaves the player's hand as a miniature and grows
 * back to full size over {@link #GROW_TICKS}; the real blocks are set down the moment the animation
 * lands, so the world never pops in ahead of it. Anything still in flight when the server stops is
 * placed immediately, so a structure can never be lost.
 */
public final class PendingPlacements {
    /** Length of the grow-in animation, shared with the client. */
    public static final int GROW_TICKS = 24;

    private static final class Pending {
        final ResourceKey<Level> dimension;
        final ServerPlayer player;
        final CompoundTag data;
        final BlockPos min;
        int ticksLeft;

        Pending(ResourceKey<Level> dimension, ServerPlayer player, CompoundTag data, BlockPos min, int ticksLeft) {
            this.dimension = dimension;
            this.player = player;
            this.data = data;
            this.min = min;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private PendingPlacements() {
    }

    public static void schedule(ServerLevel level, ServerPlayer player, CompoundTag data, BlockPos min, int delay) {
        PENDING.add(new Pending(level.dimension(), player, data, min, Math.max(0, delay)));
    }

    public static int pending() {
        return PENDING.size();
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (--p.ticksLeft <= 0) {
                it.remove();
                due.add(p);
            }
        }
        due.forEach(p -> land(server, p));
    }

    /** Sets down everything still in flight (server shutdown). */
    public static void flush(MinecraftServer server) {
        List<Pending> all = new ArrayList<>(PENDING);
        PENDING.clear();
        all.forEach(p -> land(server, p));
    }

    private static void land(MinecraftServer server, Pending p) {
        ServerLevel level = server.getLevel(p.dimension);
        if (level == null) {
            LivingBlackHole.LOGGER.warn("Compressed chunk could not be unfolded: dimension {} is gone", p.dimension.location());
            return;
        }
        int placed = ChunkCapture.commit(level, p.player, p.data, p.min);
        Vec3 center = Vec3.atLowerCornerOf(p.min).add(8.0, 8.0, 8.0);
        level.playSound(null, center.x, center.y, center.z, ModSounds.CHUNK_PLACE.get(), SoundSource.PLAYERS, 3.0F, 0.9F);
        if (!p.player.hasDisconnected()) {
            p.player.displayClientMessage(Component.translatable("message.livingblackhole.chunk_placed", placed)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }
}
