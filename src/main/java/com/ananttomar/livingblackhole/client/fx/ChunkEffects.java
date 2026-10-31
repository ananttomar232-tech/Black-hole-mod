package com.ananttomar.livingblackhole.client.fx;

import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.client.particle.GlowParticle;
import com.ananttomar.livingblackhole.client.particle.ModParticleProviders;
import com.ananttomar.livingblackhole.client.particle.ParticleFx;
import com.ananttomar.livingblackhole.client.particle.StreakParticle;
import com.ananttomar.livingblackhole.client.particle.VortexParticle;
import com.ananttomar.livingblackhole.client.render.BeamEffects;
import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Client choreography for swallowing a chunk and for setting one back down. */
public final class ChunkEffects {
    /** Diagnostics for the CI self-test. */
    public static int captures;
    public static int placements;

    private ChunkEffects() {
    }

    private static void add(Particle p) {
        Minecraft.getInstance().particleEngine.add(p);
    }

    public static void play(ChunkFxPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ModParticleProviders.accretion == null || ModParticleProviders.streak == null) {
            return;
        }
        boolean mine = mc.player != null && mc.player.getId() == msg.shooterId;
        if (msg.type == ChunkFxPacket.CAPTURE) {
            captures++;
            capture(level, msg, mine);
        } else {
            placements++;
            place(level, msg, mine);
        }
    }

    private static void capture(ClientLevel level, ChunkFxPacket msg, boolean mine) {
        RandomSource r = level.random;
        Vec3 c = msg.center();
        float density = Math.max(0.35F, ParticleFx.density());
        BeamEffects.addBeam(msg.shooterId, msg.hand, c, 3.2F, 1.0F);

        // The whirlpool: hundreds of motes on a 16-block cylinder spiralling down the drain.
        int swirl = Math.round(420 * density);
        for (int i = 0; i < swirl; i++) {
            double radius = 2.0 + Math.sqrt(r.nextDouble()) * 9.5;
            double height = (r.nextDouble() - 0.5) * 15.0;
            boolean hot = r.nextFloat() < 0.3F;
            add(new VortexParticle(level, c, radius, r.nextDouble() * Math.PI * 2.0, height, msg.shooterId, msg.hand, false,
                    hot ? ModParticleProviders.spark : ModParticleProviders.accretion)
                    .color(hot ? 1.0F : 0.55F + r.nextFloat() * 0.3F, hot ? 0.85F : 0.3F + r.nextFloat() * 0.25F, 1.0F)
                    .size(0.12F + r.nextFloat() * 0.18F));
        }

        // Every sampled block bursts into its own crumbs, flung toward the centre.
        int crumbs = 0;
        for (ChunkFxPacket.Entry e : msg.entries) {
            Vec3 at = Vec3.atCenterOf(e.pos());
            BlockState state = Block.stateById(e.stateId());
            Vec3 v = c.subtract(at).scale(0.04);
            if (!state.isAir() && crumbs++ < 260) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, v.x, v.y, v.z);
            }
            if (r.nextInt(3) == 0) {
                ParticleFx.absorb(level, at, msg.shooterId, msg.hand, false);
            }
        }

        // Implosion flash at the heart of the chunk.
        add(new GlowParticle(level, c.x, c.y, c.z, 0.0, 0.0, 0.0, ModParticleProviders.accretion)
                .color(0.85F, 0.6F, 1.0F).size(6.0F).life(12).grow(-0.9F));
        if (mine) {
            ClientEffects.addShake(0.55F);
        }
    }

    private static void place(ClientLevel level, ChunkFxPacket msg, boolean mine) {
        RandomSource r = level.random;
        Vec3 c = msg.center();
        float density = Math.max(0.35F, ParticleFx.density());
        BeamEffects.addBeam(msg.shooterId, msg.hand, c, 3.2F, 0.8F);

        int swirl = Math.round(300 * density);
        for (int i = 0; i < swirl; i++) {
            double radius = 2.0 + Math.sqrt(r.nextDouble()) * 9.0;
            add(new VortexParticle(level, c, radius, r.nextDouble() * Math.PI * 2.0, 0.0, msg.shooterId, msg.hand, true,
                    ModParticleProviders.accretion)
                    .color(0.4F + r.nextFloat() * 0.2F, 0.8F + r.nextFloat() * 0.2F, 1.0F)
                    .size(0.12F + r.nextFloat() * 0.16F));
        }
        // Shock ring racing outward across the new ground.
        int ring = Math.round(120 * density);
        for (int i = 0; i < ring; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double sp = 0.8 + r.nextDouble() * 0.6;
            add(new StreakParticle(level, c.x, c.y, c.z, Math.cos(a) * sp, (r.nextDouble() - 0.5) * 0.2, Math.sin(a) * sp, ModParticleProviders.streak)
                    .stretch(4.0F).color(0.7F, 0.9F, 1.0F).size(0.1F).life(18).physics(0.0F, 0.93F));
        }
        int crumbs = 0;
        for (ChunkFxPacket.Entry e : msg.entries) {
            if (crumbs++ >= 220) {
                break;
            }
            Vec3 at = Vec3.atCenterOf(e.pos());
            BlockState state = Block.stateById(e.stateId());
            Vec3 v = at.subtract(c).scale(0.03);
            if (!state.isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, v.x, v.y + 0.05, v.z);
            }
        }
        add(new GlowParticle(level, c.x, c.y, c.z, 0.0, 0.0, 0.0, ModParticleProviders.accretion)
                .color(0.5F, 0.9F, 1.0F).size(1.0F).life(14).grow(6.0F));
        if (mine) {
            ClientEffects.addShake(0.45F);
        }
    }
}
