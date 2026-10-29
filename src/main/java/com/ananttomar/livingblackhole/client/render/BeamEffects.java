package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.client.particle.ParticleFx;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.ananttomar.livingblackhole.network.BeamFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Pocket Black Hole beams: a camera-facing core, a soft sheath and a helix of matter. */
public final class BeamEffects {
    private static final ResourceLocation BEAM = LivingBlackHole.id("textures/entity/pocket_beam.png");
    private static final int LIFE = 22;
    /** Diagnostics for the CI self-test: frames that drew at least one beam. */
    public static int framesDrawn;

    private static final class Beam {
        final int shooterId;
        final Vec3 fallbackStart;
        final Vec3 end;
        final float radius;
        final float power;
        int age;

        Beam(BeamFxPacket msg) {
            this.shooterId = msg.shooterId;
            this.fallbackStart = msg.start;
            this.end = msg.end;
            this.radius = msg.radius;
            this.power = msg.power;
        }
    }

    private static final List<Beam> BEAMS = new ArrayList<>();

    private BeamEffects() {
    }

    public static void add(BeamFxPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        BEAMS.add(new Beam(msg));
        RandomSource r = level.random;
        if (mc.player != null && mc.player.getId() == msg.shooterId) {
            ClientEffects.addShake(0.25F + msg.power * 0.45F);
        }
        ParticleFx.beam(level, msg.start, msg.end, msg.radius, msg.power);
        int crumbs = 0;
        for (BeamFxPacket.Absorbed a : msg.absorbed) {
            Vec3 at = Vec3.atCenterOf(a.pos());
            ParticleFx.absorb(level, at, msg.shooterId, msg.start, a.stateId() < 0);
            if (a.stateId() >= 0 && crumbs++ < 160) {
                BlockState state = Block.stateById(a.stateId());
                for (int i = 0; i < 2; i++) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            at.x + (r.nextDouble() - 0.5), at.y + (r.nextDouble() - 0.5), at.z + (r.nextDouble() - 0.5),
                            0.0, 0.0, 0.0);
                }
            }
        }
    }

    public static void tick() {
        Iterator<Beam> it = BEAMS.iterator();
        while (it.hasNext()) {
            if (++it.next().age > LIFE) {
                it.remove();
            }
        }
    }

    public static void clear() {
        BEAMS.clear();
    }

    public static int activeCount() {
        return BEAMS.size();
    }

    private static Vec3 start(Beam beam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level != null ? mc.level.getEntity(beam.shooterId) : null;
        if (!(e instanceof LivingEntity living)) {
            return beam.fallbackStart;
        }
        if (living == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vector3f look = cam.getLookVector();
            Vector3f up = cam.getUpVector();
            Vector3f left = cam.getLeftVector();
            Vec3 p = cam.getPosition();
            return p.add(look.x() * 0.7, look.y() * 0.7, look.z() * 0.7)
                    .add(-left.x() * 0.3, -left.y() * 0.3, -left.z() * 0.3)
                    .add(-up.x() * 0.25, -up.y() * 0.25, -up.z() * 0.25);
        }
        return PocketBlackHoleItem.handPosition(living);
    }

    public static void render(PoseStack ps, Camera camera, float partialTick) {
        if (BEAMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.eyes(BEAM);
        VertexConsumer vc = buffers.getBuffer(type);
        Vec3 cam = camera.getPosition();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();

        for (Beam beam : BEAMS) {
            float life = (beam.age + partialTick) / LIFE;
            if (life >= 1.0F) {
                continue;
            }
            // Hold at full intensity, then decay.
            float fade = life < 0.35F ? 1.0F : (float) Math.pow(1.0F - (life - 0.35F) / 0.65F, 1.5);
            Vec3 a = start(beam, partialTick).subtract(cam);
            Vec3 b = beam.end.subtract(cam);
            float time = (mc.level != null ? mc.level.getGameTime() : 0) + partialTick;

            float core = (0.06F + beam.power * 0.08F) * (1.0F + (1.0F - life) * 0.5F);
            ribbon(vc, m, n, a, b, core, core, -time * 0.25F, 0.85F * fade, 0.55F * fade, 1.0F * fade);
            ribbon(vc, m, n, a, b, core * 3.0F, beam.radius * 0.9F, -time * 0.12F, 0.45F * fade, 0.18F * fade, 0.85F * fade);

            // Helix of matter spiralling down the beam.
            Vec3 axis = b.subtract(a);
            double len = axis.length();
            if (len < 1.0E-3) {
                continue;
            }
            Vec3 dir = axis.scale(1.0 / len);
            Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
            Vec3 u = dir.cross(ref).normalize();
            Vec3 w = dir.cross(u).normalize();
            int steps = 40;
            for (int strand = 0; strand < 2; strand++) {
                Vec3 prev = null;
                for (int i = 0; i <= steps; i++) {
                    double t = i / (double) steps;
                    double radius = Mth.lerp(t, 0.12, beam.radius * 0.6) * (1.0 - life * 0.5);
                    double phase = t * 14.0 - time * 0.6 + strand * Math.PI;
                    Vec3 p = a.add(dir.scale(len * t)).add(u.scale(Math.cos(phase) * radius)).add(w.scale(Math.sin(phase) * radius));
                    if (prev != null) {
                        ribbon(vc, m, n, prev, p, 0.035F, 0.035F, 0.0F, 0.7F * fade, 0.4F * fade, 1.0F * fade);
                    }
                    prev = p;
                }
            }
        }
        buffers.endBatch(type);
        framesDrawn++;
    }

    /** Camera-facing tapered ribbon from {@code a} to {@code b} (camera-relative coordinates). */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float w0, float w1, float vOffset,
                               float r, float g, float bl) {
        Vec3 axis = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = axis.cross(mid);
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize();
        float len = (float) axis.length();
        Vec3 s0 = side.scale(w0);
        Vec3 s1 = side.scale(w1);
        RenderHelper.quad(vc, m, n,
                (float) (a.x - s0.x), (float) (a.y - s0.y), (float) (a.z - s0.z), 0.0F, vOffset,
                (float) (a.x + s0.x), (float) (a.y + s0.y), (float) (a.z + s0.z), 1.0F, vOffset,
                (float) (b.x + s1.x), (float) (b.y + s1.y), (float) (b.z + s1.z), 1.0F, vOffset + len * 0.25F,
                (float) (b.x - s1.x), (float) (b.y - s1.y), (float) (b.z - s1.z), 0.0F, vOffset + len * 0.25F,
                r, g, bl, 1.0F);
    }
}
