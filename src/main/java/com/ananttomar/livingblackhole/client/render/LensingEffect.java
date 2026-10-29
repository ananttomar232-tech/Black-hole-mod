package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Screen-space gravitational lensing. The nearest black hole is projected to the screen and the
 * frame is re-sampled through a deflection field (Einstein ring magnification, frame-dragging
 * swirl, chromatic dispersion, heat shimmer). The scene depth buffer is sampled so only matter
 * <em>behind</em> the singularity is lensed; foreground blocks stay crisp.
 */
public final class LensingEffect {
    private static final String PROGRAM = LivingBlackHole.MODID + ":lensing";

    @Nullable
    private static PostPass lensPass;
    @Nullable
    private static PostPass blitPass;
    @Nullable
    private static TextureTarget swap;
    private static boolean failed;
    private static int width = -1;
    private static int height = -1;

    private LensingEffect() {
    }

    /** True once the lensing passes compiled and have been used for a frame. */
    public static boolean isReady() {
        return lensPass != null && !failed;
    }

    public static void reset() {
        close();
        failed = false;
    }

    private static void close() {
        if (lensPass != null) {
            lensPass.close();
            lensPass = null;
        }
        if (blitPass != null) {
            blitPass.close();
            blitPass = null;
        }
        if (swap != null) {
            swap.destroyBuffers();
            swap = null;
        }
        width = height = -1;
    }

    private static boolean ensure(Minecraft mc) {
        if (failed) {
            return false;
        }
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (lensPass == null || swap == null) {
                close();
                swap = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
                swap.setFilterMode(GL11.GL_LINEAR);
                lensPass = new PostPass(mc.getResourceManager(), PROGRAM, main, swap);
                lensPass.addAuxAsset("DepthSampler", main::getDepthTextureId, main.width, main.height);
                blitPass = new PostPass(mc.getResourceManager(), "blit", swap, main);
                width = height = -1;
            }
            if (main.width != width || main.height != height) {
                width = main.width;
                height = main.height;
                swap.resize(width, height, Minecraft.ON_OSX);
                Matrix4f ortho = new Matrix4f().setOrtho(0.0F, width, 0.0F, height, 0.1F, 1000.0F);
                lensPass.setOrthoMatrix(ortho);
                blitPass.setOrthoMatrix(ortho);
            }
            return true;
        } catch (Exception e) {
            LivingBlackHole.LOGGER.error("Gravitational lensing shader unavailable, disabling it for this session", e);
            failed = true;
            close();
            return false;
        }
    }

    /**
     * Called at the end of level rendering with the view and projection matrices of that frame.
     */
    public static void apply(Matrix4f view, Matrix4f projection, Camera camera, float partialTick) {
        if (!Config.LENSING.get()) {
            return;
        }
        BlackHoleEntity bh = ClientEffects.nearest();
        if (bh == null || bh.isRemoved()) {
            return;
        }
        float scale = bh.getVisualScale(partialTick);
        float master = (float) (double) Config.LENSING_STRENGTH.get() * Mth.clamp(scale, 0.0F, 1.2F);
        if (master <= 0.01F) {
            return;
        }

        Vec3 cam = camera.getPosition();
        Vec3 c = bh.getPosition(partialTick);
        float horizon = bh.getHorizonRadius() * scale;
        Vector4f clip = project(view, projection, c.subtract(cam));
        if (clip.w <= 0.05F) {
            return;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float ndcZ = clip.z / clip.w;
        if (Math.abs(ndcX) > 2.2F || Math.abs(ndcY) > 2.2F) {
            return;
        }
        // Apparent horizon radius in UV-height units, from a point offset along the camera's up axis.
        Vector3f up = camera.getUpVector();
        Vector4f edge = project(view, projection, c.subtract(cam).add(up.x() * horizon, up.y() * horizon, up.z() * horizon));
        if (edge.w <= 0.05F) {
            return;
        }
        float radiusUv = Math.abs(edge.y / edge.w - ndcY) * 0.5F;
        if (radiusUv < 1.0E-4F) {
            return;
        }
        float edgeFade = Mth.clamp(2.2F - Math.max(Math.abs(ndcX), Math.abs(ndcY)), 0.0F, 1.0F);
        float strength = master * edgeFade;

        Minecraft mc = Minecraft.getInstance();
        if (!ensure(mc) || lensPass == null || blitPass == null) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        EffectInstance fx = lensPass.getEffect();
        fx.safeGetUniform("Center").set(ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F);
        fx.safeGetUniform("Radius").set(Math.min(radiusUv, 0.6F));
        fx.safeGetUniform("Strength").set(strength);
        fx.safeGetUniform("HoleDepth").set(ndcZ * 0.5F + 0.5F);
        fx.safeGetUniform("Instability").set(bh.getInstability(partialTick));
        fx.safeGetUniform("GameTime").set((bh.tickCount + partialTick) / 20.0F);

        main.setFilterMode(GL11.GL_LINEAR);
        lensPass.process(partialTick);
        blitPass.process(partialTick);
        main.setFilterMode(GL11.GL_NEAREST);
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    private static Vector4f project(Matrix4f view, Matrix4f projection, Vec3 rel) {
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
        v.mul(view);
        v.mul(projection);
        return v;
    }
}
