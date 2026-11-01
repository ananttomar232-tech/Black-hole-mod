package com.ananttomar.livingblackhole.client.fx;

import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.client.particle.GlowParticle;
import com.ananttomar.livingblackhole.client.particle.ModParticleProviders;
import com.ananttomar.livingblackhole.client.particle.ParticleFx;
import com.ananttomar.livingblackhole.client.particle.StreakParticle;
import com.ananttomar.livingblackhole.client.particle.VortexParticle;
import com.ananttomar.livingblackhole.client.render.BeamEffects;
import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import com.ananttomar.livingblackhole.singularity.StructureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
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
        ChunkAnimations.start(msg, mine);
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

        // Sampled blocks shed crumbs toward the centre as the structure is torn loose.
        StructureData data = msg.structure;
        Vec3 origin = Vec3.atLowerCornerOf(msg.min);
        int step = Math.max(1, data.blocks.length / 260);
        for (int i = r.nextInt(step); i < data.blocks.length; i += step) {
            int packed = data.blocks[i];
            Vec3 at = origin.add(StructureData.x(packed) + 0.5, StructureData.y(packed) + 0.5, StructureData.z(packed) + 0.5);
            BlockState state = data.state(packed);
            Vec3 v = c.subtract(at).scale(0.04);
            if (!state.isAir()) {
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

        // Matter pours back out of the hand and spirals toward where the structure will land.
        int swirl = Math.round(300 * density);
        for (int i = 0; i < swirl; i++) {
            double radius = 2.0 + Math.sqrt(r.nextDouble()) * 9.0;
            add(new VortexParticle(level, c, radius, r.nextDouble() * Math.PI * 2.0, 0.0, msg.shooterId, msg.hand, true,
                    ModParticleProviders.accretion)
                    .color(0.4F + r.nextFloat() * 0.2F, 0.8F + r.nextFloat() * 0.2F, 1.0F)
                    .size(0.12F + r.nextFloat() * 0.16F));
        }
        if (mine) {
            ClientEffects.addShake(0.25F);
        }
    }

    /** The structure touches down: a shock ring races across the ground and its blocks shed dust. */
    public static void land(ClientLevel level, StructureData data, Vec3 origin, boolean mine) {
        if (ModParticleProviders.accretion == null || ModParticleProviders.streak == null) {
            return;
        }
        RandomSource r = level.random;
        int[] b = data.bounds();
        Vec3 base = origin.add((b[0] + b[3] + 1) * 0.5, b[1], (b[2] + b[5] + 1) * 0.5);
        Vec3 c = origin.add((b[0] + b[3] + 1) * 0.5, (b[1] + b[4] + 1) * 0.5, (b[2] + b[5] + 1) * 0.5);
        float density = Math.max(0.35F, ParticleFx.density());
        int ring = Math.round(140 * density);
        for (int i = 0; i < ring; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double sp = 0.8 + r.nextDouble() * 0.6;
            add(new StreakParticle(level, base.x, base.y + 0.2, base.z, Math.cos(a) * sp, r.nextDouble() * 0.05, Math.sin(a) * sp, ModParticleProviders.streak)
                    .stretch(4.0F).color(0.7F, 0.9F, 1.0F).size(0.1F).life(18).physics(0.0F, 0.93F));
        }
        int step = Math.max(1, data.blocks.length / 220);
        for (int i = r.nextInt(step); i < data.blocks.length; i += step) {
            int packed = data.blocks[i];
            BlockState state = data.state(packed);
            if (state.isAir()) {
                continue;
            }
            Vec3 at = origin.add(StructureData.x(packed) + 0.5, StructureData.y(packed) + 0.5, StructureData.z(packed) + 0.5);
            Vec3 v = at.subtract(c).scale(0.03);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, v.x, v.y + 0.05, v.z);
        }
        add(new GlowParticle(level, c.x, c.y, c.z, 0.0, 0.0, 0.0, ModParticleProviders.accretion)
                .color(0.5F, 0.9F, 1.0F).size(1.0F).life(14).grow(6.0F));
        if (mine) {
            ClientEffects.addShake(0.45F);
        }
    }
}
