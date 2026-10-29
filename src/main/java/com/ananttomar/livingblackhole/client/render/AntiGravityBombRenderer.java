package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** The thrown bomb: its 3D model spinning inside a pulsing cyan field halo and gyroscope ring. */
public class AntiGravityBombRenderer extends EntityRenderer<AntiGravityBombEntity> {
    private static final ResourceLocation HALO = LivingBlackHole.id("textures/entity/bomb_halo.png");
    private static final ResourceLocation RING = LivingBlackHole.id("textures/entity/bomb_ring.png");

    private final ItemRenderer itemRenderer;

    public AntiGravityBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
    }

    @Override
    public void render(AntiGravityBombEntity bomb, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int packedLight) {
        if (bomb.tickCount < 2 && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(bomb) < 12.25) {
            return;
        }
        float time = bomb.tickCount + partialTick;
        boolean captured = bomb.isCaptured();
        float spin = time * (captured ? 28.0F : 9.0F);

        ps.pushPose();
        ps.translate(0.0, 0.2, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.2F) * 15.0F));
        ps.scale(1.25F, 1.25F, 1.25F);
        this.itemRenderer.renderStatic(bomb.getItem(), ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ps, buffer, bomb.level(), bomb.getId());
        ps.popPose();

        float pulse = 0.75F + 0.25F * Mth.sin(time * (captured ? 1.4F : 0.45F));
        ps.pushPose();
        ps.translate(0.0, 0.25, 0.0);
        VertexConsumer halo = buffer.getBuffer(RenderType.eyes(HALO));
        RenderHelper.billboard(halo, ps, this.entityRenderDispatcher.cameraOrientation(), (captured ? 1.3F : 0.85F) * pulse, time * 0.05F,
                0.35F * pulse, 0.9F * pulse, 1.0F * pulse, 1.0F);
        VertexConsumer ring = buffer.getBuffer(RenderType.eyes(RING));
        ps.mulPose(Axis.YP.rotationDegrees(-spin * 0.6F));
        ps.mulPose(Axis.XP.rotationDegrees(70.0F + Mth.sin(time * 0.1F) * 20.0F));
        RenderHelper.quad(ring, ps.last().pose(), ps.last().normal(),
                -0.7F, 0.0F, -0.7F, 0.0F, 0.0F,
                0.7F, 0.0F, -0.7F, 1.0F, 0.0F,
                0.7F, 0.0F, 0.7F, 1.0F, 1.0F,
                -0.7F, 0.0F, 0.7F, 0.0F, 1.0F,
                0.4F, 0.95F, 1.0F, 1.0F);
        ps.popPose();
        super.render(bomb, yaw, partialTick, ps, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AntiGravityBombEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
