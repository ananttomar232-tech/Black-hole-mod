package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.block.CoreFragmentBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A pulsing sky beam that marks each Core Fragment from hundreds of blocks away. */
public class CoreFragmentRenderer implements BlockEntityRenderer<CoreFragmentBlockEntity> {
    private static final ResourceLocation BEAM = LivingBlackHole.id("textures/entity/core_beam.png");
    private static final ResourceLocation HALO = LivingBlackHole.id("textures/entity/core_halo.png");

    public CoreFragmentRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CoreFragmentBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        float time = (level.getGameTime() % 24000L) + partialTick;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (be.getBlockPos().getX() + 0.5);
        double dz = cam.z - (be.getBlockPos().getZ() + 0.5);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float height = Math.min(320, level.getMaxBuildHeight() - be.getBlockPos().getY());
        float pulse = 0.8F + 0.2F * Mth.sin(time * 0.15F);
        // Thicken with distance so the beacon stays readable on the horizon.
        float widen = 1.0F + (float) Mth.clamp(horizontal / 90.0, 0.0, 3.0);

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        float facing = (float) Math.atan2(dx, dz);
        ps.mulPose(Axis.YP.rotation(facing));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer beam = buffer.getBuffer(RenderType.eyes(BEAM));
        float scroll = -time * 0.02F;
        float vSpan = height / 12.0F;
        for (int layer = 0; layer < 2; layer++) {
            float w = (layer == 0 ? 0.12F : 0.42F) * widen;
            float k = (layer == 0 ? 0.95F : 0.35F) * pulse;
            RenderHelper.quad(beam, m, n,
                    -w, 0.9F, 0.0F, 0.0F, scroll + vSpan,
                    w, 0.9F, 0.0F, 1.0F, scroll + vSpan,
                    w, height, 0.0F, 1.0F, scroll,
                    -w, height, 0.0F, 0.0F, scroll,
                    0.35F * k, 0.95F * k, 1.0F * k, 1.0F);
        }
        ps.popPose();

        ps.pushPose();
        ps.translate(0.5, 0.75, 0.5);
        VertexConsumer halo = buffer.getBuffer(RenderType.eyes(HALO));
        RenderHelper.billboard(halo, ps, Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation(), 1.15F * pulse, time * 0.03F,
                0.3F * pulse, 0.85F * pulse, 1.0F * pulse, 1.0F);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CoreFragmentBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
