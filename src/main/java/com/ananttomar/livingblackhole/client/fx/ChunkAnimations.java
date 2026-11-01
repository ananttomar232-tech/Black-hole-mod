package com.ananttomar.livingblackhole.client.fx;

import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.client.particle.GlowParticle;
import com.ananttomar.livingblackhole.client.particle.HomingParticle;
import com.ananttomar.livingblackhole.client.particle.ModParticleProviders;
import com.ananttomar.livingblackhole.client.particle.StreakParticle;
import com.ananttomar.livingblackhole.client.render.StructureMesh;
import com.ananttomar.livingblackhole.client.render.StructureMeshCache;
import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import com.ananttomar.livingblackhole.singularity.StructureData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The swallowed structure itself on screen: on capture the real blocks (baked into a
 * {@link StructureMesh}) tremble in place, then shrink, spin and arc straight into the shooter's
 * Pocket Black Hole; on placement they leave the hand as a miniature and scale back up to full size,
 * landing exactly where the server sets the blocks down.
 */
public final class ChunkAnimations {
    /** Fraction of the capture spent trembling in place before the structure lets go of the ground. */
    private static final float TREMBLE = 0.14F;
    /** Size of the structure at the hand end of its flight, relative to full size. */
    private static final float HAND_SCALE = 0.006F;
    /** Ticks a landed structure stays drawn while the real blocks arrive and are meshed. */
    private static final int LAND_HOLD = 5;

    /** Diagnostics for the CI self-test. */
    public static int started;
    public static int framesDrawn;

    private static final class Anim {
        final StructureData data;
        /** World position of the structure's local origin. */
        final Vec3 origin;
        final int shooterId;
        final boolean grow;
        final int duration;
        final boolean mine;
        Vec3 hand;
        int age;
        boolean finished;

        Anim(ChunkFxPacket msg, boolean mine) {
            this.data = msg.structure;
            this.origin = Vec3.atLowerCornerOf(msg.min);
            this.shooterId = msg.shooterId;
            this.grow = msg.type == ChunkFxPacket.PLACE;
            this.duration = Math.max(1, msg.duration);
            this.hand = msg.hand;
            this.mine = mine;
        }
    }

    private static final List<Anim> ACTIVE = new ArrayList<>();

    private ChunkAnimations() {
    }

    public static void start(ChunkFxPacket msg, boolean mine) {
        if (msg.structure.isEmpty()) {
            return;
        }
        // Bake now, while the packet is fresh, so the first frame already shows the structure.
        StructureMeshCache.get(msg.structure);
        ACTIVE.add(new Anim(msg, mine));
        started++;
    }

    public static boolean active() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        for (Iterator<Anim> it = ACTIVE.iterator(); it.hasNext(); ) {
            Anim a = it.next();
            a.age++;
            a.hand = HomingParticle.handTarget(level, a.shooterId, a.hand);
            if (a.age <= a.duration) {
                trail(level, a);
            }
            if (!a.finished && a.age >= a.duration) {
                a.finished = true;
                arrive(level, a);
            }
            if (a.age > a.duration + (a.grow ? LAND_HOLD : 0)) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ motion

    private record Frame(Vec3 center, float scale, float spin, float r, float g, float b) {
    }

    private static float progress(Anim a, float partialTick) {
        return Mth.clamp((a.age + partialTick) / a.duration, 0.0F, 1.0F);
    }

    private static Frame frame(Anim a, StructureMesh mesh, float t, RandomSource jitter) {
        Vec3 home = a.origin.add(mesh.centerX(), mesh.centerY(), mesh.centerZ());
        if (!a.grow) {
            if (t < TREMBLE) {
                // Gripped by the singularity: the whole structure shudders in its socket.
                float k = t / TREMBLE;
                double j = 0.07 * k;
                Vec3 shake = new Vec3((jitter.nextDouble() - 0.5) * j, (jitter.nextDouble() - 0.5) * j, (jitter.nextDouble() - 0.5) * j);
                return new Frame(home.add(shake), 1.002F, 0.0F, 1.0F - 0.1F * k, 1.0F - 0.2F * k, 1.0F);
            }
            float u = (t - TREMBLE) / (1.0F - TREMBLE);
            float travel = (float) Math.pow(u, 1.7);
            float scale = HAND_SCALE + (1.0F - HAND_SCALE) * (float) Math.pow(1.0F - u, 2.3);
            Vec3 c = home.lerp(a.hand, travel).add(0.0, Math.sin(Math.PI * u) * 2.2 * (1.0F - u), 0.0);
            float spin = u * u * 320.0F;
            return new Frame(c, scale, spin, 1.0F - 0.25F * u, 1.0F - 0.45F * u, 1.0F);
        }
        if (t >= 1.0F) {
            return new Frame(home, 1.002F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
        float ease = 1.0F - (float) Math.pow(1.0F - t, 2.4);
        // Overshoots a touch and settles, like something springing back into shape.
        float c1 = 1.4F;
        float back = 1.0F + (c1 + 1.0F) * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
        float scale = Math.max(HAND_SCALE, HAND_SCALE + (1.0F - HAND_SCALE) * back);
        Vec3 c = a.hand.lerp(home, ease).add(0.0, Math.sin(Math.PI * t) * 2.5, 0.0);
        float spin = -(1.0F - ease) * (1.0F - ease) * 300.0F;
        return new Frame(c, scale, spin, 0.75F + 0.25F * t, 0.92F + 0.08F * t, 1.0F);
    }

    // ------------------------------------------------------------------ rendering

    public static void render(PoseStack ps, Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        RandomSource jitter = RandomSource.create(ClientEffects.ticks() * 31L);
        for (Anim a : ACTIVE) {
            StructureMesh mesh = StructureMeshCache.get(a.data);
            if (mesh == null || mesh.isEmpty()) {
                continue;
            }
            float t = progress(a, partialTick);
            Frame f = frame(a, mesh, t, jitter);
            ps.pushPose();
            ps.translate(f.center().x - cam.x, f.center().y - cam.y, f.center().z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(f.spin()));
            ps.scale(f.scale(), f.scale(), f.scale());
            ps.translate(-mesh.centerX(), -mesh.centerY(), -mesh.centerZ());
            mesh.draw(ps, buffers, LightTexture.FULL_BRIGHT, f.r(), f.g(), f.b());
            ps.popPose();
            framesDrawn++;
        }
        buffers.endBatch();
    }

    // ------------------------------------------------------------------ particles

    private static void trail(ClientLevel level, Anim a) {
        if (ModParticleProviders.streak == null) {
            return;
        }
        StructureMesh mesh = StructureMeshCache.get(a.data);
        if (mesh == null) {
            return;
        }
        float t = progress(a, 0.0F);
        if (!a.grow && t < TREMBLE) {
            return;
        }
        Frame f = frame(a, mesh, t, level.random);
        RandomSource r = level.random;
        float radius = mesh.span() * 0.5F * f.scale();
        for (int i = 0; i < 3; i++) {
            Vec3 at = f.center().add((r.nextDouble() - 0.5) * radius * 2.0, (r.nextDouble() - 0.5) * radius * 2.0, (r.nextDouble() - 0.5) * radius * 2.0);
            Minecraft.getInstance().particleEngine.add(new StreakParticle(level, at.x, at.y, at.z,
                    (r.nextDouble() - 0.5) * 0.05, (r.nextDouble() - 0.5) * 0.05, (r.nextDouble() - 0.5) * 0.05, ModParticleProviders.streak)
                    .stretch(2.0F).color(a.grow ? 0.6F : 0.75F, a.grow ? 0.9F : 0.5F, 1.0F).size(0.06F).life(10).physics(0.0F, 0.9F));
        }
    }

    private static void arrive(ClientLevel level, Anim a) {
        if (a.grow) {
            ChunkEffects.land(level, a.data, a.origin, a.mine);
            return;
        }
        if (ModParticleProviders.accretion == null) {
            return;
        }
        // Swallowed: a small violet flare where the structure vanished into the device.
        Minecraft.getInstance().particleEngine.add(new GlowParticle(level, a.hand.x, a.hand.y, a.hand.z, 0.0, 0.0, 0.0, ModParticleProviders.accretion)
                .color(0.85F, 0.55F, 1.0F).size(0.6F).life(10).grow(-0.6F));
        if (a.mine) {
            ClientEffects.addShake(0.3F);
        }
    }
}
