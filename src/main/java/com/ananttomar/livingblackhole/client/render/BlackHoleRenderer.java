package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the singularity in layers, back to front:
 * <ol>
 *     <li>corona - a huge, faint additive halo of scattered light</li>
 *     <li>event horizon - a pitch-black sphere that writes depth and hides everything behind it</li>
 *     <li>accretion disk - differentially rotating plasma bands with relativistic Doppler beaming</li>
 *     <li>photon ring and lensed arcs - camera-facing, rolled to follow the projected spin axis</li>
 *     <li>relativistic jets - camera-facing ribbons of scrolling plasma along the spin axis</li>
 *     <li>collapse flare and the expanding Fresnel shockwave shell + disk-plane ring</li>
 * </ol>
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {
    private static final ResourceLocation VOID = LivingBlackHole.id("textures/entity/black_hole/void.png");
    private static final ResourceLocation DISK = LivingBlackHole.id("textures/entity/black_hole/disk.png");
    private static final ResourceLocation HALO = LivingBlackHole.id("textures/entity/black_hole/halo.png");
    private static final ResourceLocation ARCS = LivingBlackHole.id("textures/entity/black_hole/arcs.png");
    private static final ResourceLocation CORONA = LivingBlackHole.id("textures/entity/black_hole/corona.png");
    private static final ResourceLocation JET = LivingBlackHole.id("textures/entity/black_hole/jet.png");
    private static final ResourceLocation FLARE = LivingBlackHole.id("textures/entity/black_hole/flare.png");
    private static final ResourceLocation GLOW = LivingBlackHole.id("textures/entity/black_hole/glow.png");
    private static final ResourceLocation SHOCK_RING = LivingBlackHole.id("textures/entity/black_hole/shockwave.png");
    private static final ResourceLocation PLASMA = LivingBlackHole.id("textures/entity/black_hole/plasma.png");

    private static final int DISK_SEGMENTS = 128;
    private static final int DISK_BANDS = 7;
    private static final float DISK_INNER = 1.3F;
    private static final float DISK_OUTER = 5.4F;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(BlackHoleEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity entity) {
        return VOID;
    }

    @Override
    public void render(BlackHoleEntity bh, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int packedLight) {
        float time = bh.tickCount + partialTick;
        int state = bh.getState();
        float scale = bh.getVisualScale(partialTick);
        float instability = bh.getInstability(partialTick);
        float horizon = bh.getHorizonRadius() * scale;

        Camera camera = this.entityRenderDispatcher.camera;
        Vec3 center = bh.getPosition(partialTick);
        Vec3 toCam = camera.getPosition().subtract(center);
        Vector3f view = toCam.lengthSqr() > 1.0E-6 ? new Vector3f((float) toCam.x, (float) toCam.y, (float) toCam.z).normalize() : new Vector3f(0.0F, 0.0F, 1.0F);
        Quaternionf camRot = this.entityRenderDispatcher.cameraOrientation();
        Quaternionf diskRot = BlackHoleEntity.diskRotation();
        Vector3f axis = BlackHoleEntity.diskAxis();

        // Roll that aligns a billboard's +Y with the spin axis projected onto the screen.
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        float roll = -(float) Math.atan2(axis.dot(left), axis.dot(up));
        float faceOn = Math.abs(axis.dot(view));

        if (horizon > 0.02F && state < BlackHoleEntity.STATE_SHOCKWAVE) {
            float heat = 1.0F + instability * 0.8F;

            // 1. Corona
            float pulse = 1.0F + 0.06F * Mth.sin(time * (0.06F + instability * 0.5F));
            VertexConsumer corona = buffer.getBuffer(RenderType.eyes(CORONA));
            RenderHelper.billboard(corona, ps, camRot, horizon * 10.0F * pulse, time * 0.0015F,
                    0.42F * heat, 0.16F * heat, 0.36F + instability * 0.4F, 1.0F);

            // 2. Event horizon
            VertexConsumer core = buffer.getBuffer(RenderType.entitySolid(VOID));
            RenderHelper.sphere(core, ps, horizon, 24, 48, 0.0F, 0.0F, 0.0F, 1.0F);

            // 3. Accretion disk
            renderDisk(ps, buffer, diskRot, view, horizon, time, instability);

            // 4. Photon ring + lensed far side of the disk
            float ringBoost = 0.85F + 0.15F * Mth.sin(time * 0.11F) + instability * 0.5F;
            VertexConsumer halo = buffer.getBuffer(RenderType.eyes(HALO));
            RenderHelper.billboard(halo, ps, camRot, horizon * 2.35F, roll, ringBoost, 0.86F * ringBoost, 0.7F * ringBoost + instability * 0.3F, 1.0F);
            float arcs = (1.0F - faceOn * 0.85F) * (0.9F + instability * 0.4F);
            VertexConsumer arc = buffer.getBuffer(RenderType.eyes(ARCS));
            RenderHelper.billboard(arc, ps, camRot, horizon * 3.1F, roll, arcs, 0.62F * arcs, 0.3F * arcs + instability * 0.4F, 1.0F);

            // 5. Jets
            renderJets(ps, buffer, diskRot, center, camera.getPosition(), horizon, time, instability);
        }

        // Birth flash and collapse flare.
        if (state == BlackHoleEntity.STATE_BIRTH) {
            float age = bh.stateAge(partialTick);
            float f = Mth.clamp(1.0F - age / 25.0F, 0.0F, 1.0F);
            if (f > 0.0F) {
                VertexConsumer flare = buffer.getBuffer(RenderType.eyes(FLARE));
                RenderHelper.billboard(flare, ps, camRot, 6.0F + age * 1.2F, time * 0.01F, f, f * 0.85F, f, 1.0F);
            }
        } else if (state == BlackHoleEntity.STATE_DESTABILIZING || state == BlackHoleEntity.STATE_COLLAPSE) {
            float t = state == BlackHoleEntity.STATE_COLLAPSE
                    ? 0.5F + 0.5F * Mth.clamp(bh.stateAge(partialTick) / BlackHoleEntity.COLLAPSE_TICKS, 0.0F, 1.0F)
                    : instability * 0.5F;
            float flicker = 0.85F + 0.15F * Mth.sin(time * 2.3F);
            float size = bh.getHorizonRadius() * (1.5F + t * t * 9.0F);
            VertexConsumer flare = buffer.getBuffer(RenderType.eyes(FLARE));
            RenderHelper.billboard(flare, ps, camRot, size, time * 0.05F, t * flicker, t * 0.95F * flicker, t * flicker, 1.0F);
        }

        if (state == BlackHoleEntity.STATE_SHOCKWAVE) {
            renderShockwave(bh, ps, buffer, camRot, diskRot, center, camera.getPosition(), partialTick, time);
        }
    }

    private void renderDisk(PoseStack ps, MultiBufferSource buffer, Quaternionf diskRot, Vector3f view, float horizon, float time, float instability) {
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(DISK));
        ps.pushPose();
        ps.mulPose(diskRot);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();

        float inner = horizon * DISK_INNER;
        float outer = horizon * DISK_OUTER;
        float spin = 1.0F + instability * 4.0F;

        // Precompute Doppler factors per segment edge: tangent velocity . direction to camera.
        float[] doppler = new float[DISK_SEGMENTS + 1];
        float[] cos = new float[DISK_SEGMENTS + 1];
        float[] sin = new float[DISK_SEGMENTS + 1];
        Vector3f tangent = new Vector3f();
        for (int j = 0; j <= DISK_SEGMENTS; j++) {
            float theta = Mth.TWO_PI * j / DISK_SEGMENTS;
            cos[j] = Mth.cos(theta);
            sin[j] = Mth.sin(theta);
            tangent.set(-sin[j], 0.0F, cos[j]);
            diskRot.transform(tangent);
            doppler[j] = tangent.dot(view);
        }

        for (int k = 0; k < DISK_BANDS; k++) {
            float f0 = k / (float) DISK_BANDS;
            float f1 = (k + 1) / (float) DISK_BANDS;
            // Bands are packed toward the hot inner edge.
            float r0 = Mth.lerp(f0 * f0, inner, outer);
            float r1 = Mth.lerp(f1 * f1, inner, outer);
            float rm = (r0 + r1) * 0.5F;
            // Keplerian differential rotation: inner bands lap the outer ones.
            float omega = 0.0065F * spin * (float) Math.pow(inner / rm, 1.5);
            float offset = time * omega;
            float v0 = f0 * f0;
            float v1 = f1 * f1;
            float temperature = 1.0F - (f0 + f1) * 0.5F;
            float baseR = 1.0F;
            float baseG = Mth.lerp(temperature, 0.38F, 0.93F);
            float baseB = Mth.lerp(temperature, 0.12F, 0.78F);
            baseR = Mth.lerp(instability, baseR, 0.8F);
            baseG = Mth.lerp(instability, baseG, 0.9F);
            baseB = Mth.lerp(instability, baseB, 1.0F);
            float bright = Mth.lerp(temperature, 0.55F, 1.0F) * (1.0F + instability * 0.4F);

            for (int j = 0; j < DISK_SEGMENTS; j++) {
                float u0 = j / (float) DISK_SEGMENTS * 3.0F - offset;
                float u1 = (j + 1) / (float) DISK_SEGMENTS * 3.0F - offset;
                float d = (doppler[j] + doppler[j + 1]) * 0.5F;
                float beam = Mth.clamp(1.0F + 0.75F * d, 0.25F, 1.75F);
                float r = baseR * Mth.lerp(Math.max(d, 0.0F), 1.0F, 0.82F);
                float g = baseG * Mth.lerp(Math.max(d, 0.0F), 1.0F, 1.0F) * Mth.lerp(Math.max(-d, 0.0F), 1.0F, 0.7F);
                float b = baseB * Mth.lerp(Math.max(d, 0.0F), 1.0F, 1.35F) * Mth.lerp(Math.max(-d, 0.0F), 1.0F, 0.5F);
                float k2 = bright * beam;
                RenderHelper.quad(vc, m, n,
                        cos[j] * r0, 0.0F, sin[j] * r0, u0, v0,
                        cos[j + 1] * r0, 0.0F, sin[j + 1] * r0, u1, v0,
                        cos[j + 1] * r1, 0.0F, sin[j + 1] * r1, u1, v1,
                        cos[j] * r1, 0.0F, sin[j] * r1, u0, v1,
                        r * k2, g * k2, b * k2, 1.0F);
            }
        }
        ps.popPose();
    }

    private void renderJets(PoseStack ps, MultiBufferSource buffer, Quaternionf diskRot, Vec3 center, Vec3 cam,
                            float horizon, float time, float instability) {
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(JET));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Vector3f axis = BlackHoleEntity.diskAxis();
        float length = horizon * (11.0F + instability * 6.0F);
        float flicker = 0.75F + 0.25F * Mth.sin(time * 0.9F) * Mth.sin(time * 0.37F);
        float intensity = (0.55F + instability * 0.45F) * flicker;
        float scroll = -time * 0.045F * (1.0F + instability * 2.0F);

        for (int sign = -1; sign <= 1; sign += 2) {
            Vector3f dir = new Vector3f(axis).mul(sign);
            Vector3f base = new Vector3f(dir).mul(horizon * 0.85F);
            Vector3f tip = new Vector3f(dir).mul(horizon * 0.85F + length);
            for (int layer = 0; layer < 2; layer++) {
                float w0 = horizon * (layer == 0 ? 0.22F : 0.55F);
                float w1 = horizon * (layer == 0 ? 0.9F : 2.1F);
                float k = intensity * (layer == 0 ? 1.0F : 0.35F);
                Vector3f s0 = sideVector(dir, base, center, cam).mul(w0);
                Vector3f s1 = sideVector(dir, tip, center, cam).mul(w1);
                float v0 = scroll + layer * 0.37F;
                float v1 = v0 + length / (horizon * 4.0F);
                RenderHelper.quad(vc, m, n,
                        base.x - s0.x, base.y - s0.y, base.z - s0.z, 0.0F, v0,
                        base.x + s0.x, base.y + s0.y, base.z + s0.z, 1.0F, v0,
                        tip.x + s1.x, tip.y + s1.y, tip.z + s1.z, 1.0F, v1,
                        tip.x - s1.x, tip.y - s1.y, tip.z - s1.z, 0.0F, v1,
                        0.42F * k, 0.6F * k, 1.0F * k, 1.0F);
            }
        }
    }

    /** Unit vector perpendicular to the jet axis and to the line of sight at {@code local}. */
    private static Vector3f sideVector(Vector3f dir, Vector3f local, Vec3 center, Vec3 cam) {
        Vector3f toCam = new Vector3f((float) (cam.x - center.x - local.x), (float) (cam.y - center.y - local.y), (float) (cam.z - center.z - local.z));
        Vector3f side = new Vector3f(dir).cross(toCam);
        if (side.lengthSquared() < 1.0E-6F) {
            side.set(1.0F, 0.0F, 0.0F);
        }
        return side.normalize();
    }

    private void renderShockwave(BlackHoleEntity bh, PoseStack ps, MultiBufferSource buffer, Quaternionf camRot, Quaternionf diskRot,
                                 Vec3 center, Vec3 cam, float partialTick, float time) {
        float age = bh.stateAge(partialTick);
        float life = BlackHoleEntity.SHOCKWAVE_TICKS;
        float t = Mth.clamp(age / (life * 0.7F), 0.0F, 1.0F);
        float fade = (float) Math.pow(Mth.clamp(1.0F - age / life, 0.0F, 1.0F), 1.5);
        float ease = 1.0F - (float) Math.pow(1.0F - t, 3.0);

        // Core flash.
        if (age < 24.0F) {
            float f = 1.0F - age / 24.0F;
            VertexConsumer flare = buffer.getBuffer(RenderType.eyes(FLARE));
            RenderHelper.billboard(flare, ps, camRot, 20.0F + age * 5.0F, time * 0.02F, f, f, f, 1.0F);
        }
        VertexConsumer glow = buffer.getBuffer(RenderType.eyes(GLOW));
        RenderHelper.billboard(glow, ps, camRot, 14.0F + ease * 30.0F, 0.0F, 0.9F * fade, 0.75F * fade, 1.0F * fade, 1.0F);

        // Fresnel shell: bright at the silhouette, transparent face-on.
        float shell = 4.0F + 150.0F * ease;
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(PLASMA));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int lat = 24;
        int lon = 48;
        float scroll = time * 0.01F;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon;
                float p1 = Mth.TWO_PI * (j + 1) / lon;
                float[] q = new float[12];
                float fres = 0.0F;
                float[][] corners = {{t0, p0}, {t0, p1}, {t1, p1}, {t1, p0}};
                for (int c = 0; c < 4; c++) {
                    float nx = Mth.cos(corners[c][0]) * Mth.cos(corners[c][1]);
                    float ny = Mth.sin(corners[c][0]);
                    float nz = Mth.cos(corners[c][0]) * Mth.sin(corners[c][1]);
                    q[c * 3] = nx * shell;
                    q[c * 3 + 1] = ny * shell;
                    q[c * 3 + 2] = nz * shell;
                    double vx = cam.x - (center.x + q[c * 3]);
                    double vy = cam.y - (center.y + q[c * 3 + 1]);
                    double vz = cam.z - (center.z + q[c * 3 + 2]);
                    double len = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-4;
                    fres += 1.0F - (float) Math.abs((nx * vx + ny * vy + nz * vz) / len);
                }
                fres = (float) Math.pow(fres / 4.0F, 2.2) * fade;
                if (fres < 0.004F) {
                    continue;
                }
                float u0 = j / (float) lon * 4.0F + scroll;
                float u1 = (j + 1) / (float) lon * 4.0F + scroll;
                float v0 = i / (float) lat * 2.0F;
                float v1 = (i + 1) / (float) lat * 2.0F;
                RenderHelper.quad(vc, m, n,
                        q[0], q[1], q[2], u0, v0,
                        q[3], q[4], q[5], u1, v0,
                        q[6], q[7], q[8], u1, v1,
                        q[9], q[10], q[11], u0, v1,
                        0.6F * fres, 0.85F * fres, 1.0F * fres, 1.0F);
            }
        }

        // Disk-plane shock rings: a fast white-blue front and a slower ember ring of disk matter.
        ps.pushPose();
        ps.mulPose(diskRot);
        VertexConsumer ring = buffer.getBuffer(RenderType.eyes(SHOCK_RING));
        Matrix4f rm = ps.last().pose();
        Matrix3f rn = ps.last().normal();
        float fast = 6.0F + 200.0F * (1.0F - (float) Math.pow(1.0F - Mth.clamp(age / (life * 0.6F), 0.0F, 1.0F), 4.0));
        float slow = 3.0F + 110.0F * ease;
        annulus(ring, rm, rn, fast * 0.8F, fast, 96, time * 0.01F, 0.75F * fade, 0.92F * fade, 1.0F * fade);
        annulus(ring, rm, rn, slow * 0.72F, slow, 96, -time * 0.006F, 1.0F * fade, 0.55F * fade, 0.22F * fade);
        ps.popPose();
    }

    private static void annulus(VertexConsumer vc, Matrix4f m, Matrix3f n, float r0, float r1, int segments, float uOffset,
                                float r, float g, float b) {
        for (int j = 0; j < segments; j++) {
            float a0 = Mth.TWO_PI * j / segments;
            float a1 = Mth.TWO_PI * (j + 1) / segments;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float u0 = j / (float) segments * 6.0F + uOffset;
            float u1 = (j + 1) / (float) segments * 6.0F + uOffset;
            RenderHelper.quad(vc, m, n,
                    c0 * r0, 0.0F, s0 * r0, u0, 0.0F,
                    c1 * r0, 0.0F, s1 * r0, u1, 0.0F,
                    c1 * r1, 0.0F, s1 * r1, u1, 1.0F,
                    c0 * r1, 0.0F, s0 * r1, u0, 1.0F,
                    r, g, b, 1.0F);
        }
    }
}
