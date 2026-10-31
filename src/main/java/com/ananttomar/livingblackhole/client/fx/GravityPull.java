package com.ananttomar.livingblackhole.client.fx;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The black hole's tug on the player. Player movement is client-authoritative, so the pull is
 * added to the local player's own motion every tick: smooth, no server rubber-banding.
 *
 * <p>Tuning: on the ground the extra acceleration settles into a drift of {@code a / 0.454} blocks
 * per tick. At the edge of the 30 block reach that is ~0.03 b/t (a gentle lean); next to the core
 * it reaches ~0.2 b/t - roughly walking speed - so walking away barely holds ground while sprinting
 * (~0.28 b/t) always escapes. In the air the pull is reduced so jumps don't turn into launches.</p>
 */
public final class GravityPull {
    private static final double EDGE_ACCEL = 0.014;
    private static final double CORE_ACCEL = 0.075;
    private static final double AIR_FACTOR = 0.3;
    private static final double VERTICAL_FACTOR = 0.5;

    /** Diagnostics for the CI self-test: ticks in which the pull was applied. */
    public static int ticksApplied;

    private GravityPull() {
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            return;
        }
        BlackHoleEntity bh = ClientEffects.nearest();
        if (bh == null || bh.getState() >= BlackHoleEntity.STATE_SHOCKWAVE) {
            return;
        }
        double strength = Config.PLAYER_PULL.get();
        float scale = Math.min(1.0F, bh.getVisualScale(1.0F));
        if (strength <= 0.0 || scale < 0.05F) {
            return;
        }
        Vec3 to = bh.position().subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
        double d = to.length();
        double reach = bh.getReach();
        if (d > reach || d < 0.5) {
            return;
        }
        double falloff = 1.0 - d / reach;
        double accel = (EDGE_ACCEL + (CORE_ACCEL - EDGE_ACCEL) * falloff * falloff) * strength * scale;
        if (bh.getState() == BlackHoleEntity.STATE_DESTABILIZING || bh.getState() == BlackHoleEntity.STATE_COLLAPSE) {
            accel *= 1.4;
        }
        if (!player.onGround()) {
            accel *= AIR_FACTOR;
        }
        Vec3 dir = to.scale(1.0 / d);
        player.setDeltaMovement(player.getDeltaMovement().add(dir.x * accel, dir.y * accel * VERTICAL_FACTOR, dir.z * accel));
        ticksApplied++;
    }
}
