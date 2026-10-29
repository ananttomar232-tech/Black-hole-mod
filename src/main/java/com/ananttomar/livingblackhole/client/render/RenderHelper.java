package com.ananttomar.livingblackhole.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Low-level geometry helpers for NEW_ENTITY-format render types. */
public final class RenderHelper {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private RenderHelper() {
    }

    public static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float z,
                              float u, float v, float r, float g, float b, float a) {
        vc.vertex(pose, x, y, z)
                .color(clamp(r), clamp(g), clamp(b), clamp(a))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static float clamp(float f) {
        return Mth.clamp(f, 0.0F, 1.0F);
    }

    /** Emits a quad with both windings so it survives back-face culling. */
    public static void quad(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                            float x0, float y0, float z0, float u0, float v0,
                            float x1, float y1, float z1, float u1, float v1,
                            float x2, float y2, float z2, float u2, float v2,
                            float x3, float y3, float z3, float u3, float v3,
                            float r, float g, float b, float a) {
        vertex(vc, pose, normal, x0, y0, z0, u0, v0, r, g, b, a);
        vertex(vc, pose, normal, x1, y1, z1, u1, v1, r, g, b, a);
        vertex(vc, pose, normal, x2, y2, z2, u2, v2, r, g, b, a);
        vertex(vc, pose, normal, x3, y3, z3, u3, v3, r, g, b, a);

        vertex(vc, pose, normal, x3, y3, z3, u3, v3, r, g, b, a);
        vertex(vc, pose, normal, x2, y2, z2, u2, v2, r, g, b, a);
        vertex(vc, pose, normal, x1, y1, z1, u1, v1, r, g, b, a);
        vertex(vc, pose, normal, x0, y0, z0, u0, v0, r, g, b, a);
    }

    /** Double-sided quad whose first edge (v0-v1) has colour A and second edge (v2-v3) colour B. */
    public static void quadGradient(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                                    float x0, float y0, float z0, float u0, float v0,
                                    float x1, float y1, float z1, float u1, float v1,
                                    float x2, float y2, float z2, float u2, float v2,
                                    float x3, float y3, float z3, float u3, float v3,
                                    float ra, float ga, float ba, float rb, float gb, float bb) {
        vertex(vc, pose, normal, x0, y0, z0, u0, v0, ra, ga, ba, 1.0F);
        vertex(vc, pose, normal, x1, y1, z1, u1, v1, ra, ga, ba, 1.0F);
        vertex(vc, pose, normal, x2, y2, z2, u2, v2, rb, gb, bb, 1.0F);
        vertex(vc, pose, normal, x3, y3, z3, u3, v3, rb, gb, bb, 1.0F);

        vertex(vc, pose, normal, x3, y3, z3, u3, v3, rb, gb, bb, 1.0F);
        vertex(vc, pose, normal, x2, y2, z2, u2, v2, rb, gb, bb, 1.0F);
        vertex(vc, pose, normal, x1, y1, z1, u1, v1, ra, ga, ba, 1.0F);
        vertex(vc, pose, normal, x0, y0, z0, u0, v0, ra, ga, ba, 1.0F);
    }

    /** A camera-facing square of half-size {@code size}, rolled by {@code roll} radians. */
    public static void billboard(VertexConsumer vc, PoseStack ps, Quaternionf cameraRotation, float size, float roll,
                                 float r, float g, float b, float a) {
        ps.pushPose();
        ps.mulPose(cameraRotation);
        ps.mulPose(Axis.ZP.rotation(roll));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        quad(vc, m, n,
                -size, -size, 0.0F, 0.0F, 1.0F,
                size, -size, 0.0F, 1.0F, 1.0F,
                size, size, 0.0F, 1.0F, 0.0F,
                -size, size, 0.0F, 0.0F, 0.0F,
                r, g, b, a);
        ps.popPose();
    }

    /** UV sphere; {@code shade} receives the unit normal and returns nothing - colour is uniform. */
    public static void sphere(VertexConsumer vc, PoseStack ps, float radius, int lat, int lon, float r, float g, float b, float a) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
            float y0 = Mth.sin(t0) * radius;
            float y1 = Mth.sin(t1) * radius;
            float c0 = Mth.cos(t0) * radius;
            float c1 = Mth.cos(t1) * radius;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon;
                float p1 = Mth.TWO_PI * (j + 1) / lon;
                float cp0 = Mth.cos(p0);
                float sp0 = Mth.sin(p0);
                float cp1 = Mth.cos(p1);
                float sp1 = Mth.sin(p1);
                float u0 = j / (float) lon;
                float u1 = (j + 1) / (float) lon;
                float v0 = i / (float) lat;
                float v1 = (i + 1) / (float) lat;
                quad(vc, m, n,
                        c0 * cp0, y0, c0 * sp0, u0, v0,
                        c0 * cp1, y0, c0 * sp1, u1, v0,
                        c1 * cp1, y1, c1 * sp1, u1, v1,
                        c1 * cp0, y1, c1 * sp0, u0, v1,
                        r, g, b, a);
            }
        }
    }
}
