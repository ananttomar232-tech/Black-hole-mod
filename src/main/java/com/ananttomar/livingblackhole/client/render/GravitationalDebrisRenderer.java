package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.GravitationalDebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Debris tumbles, shrinks, glows red-hot and is stretched along the radial direction as it nears
 * the horizon - tidal spaghettification.
 */
public class GravitationalDebrisRenderer extends EntityRenderer<GravitationalDebrisEntity> {
    private static final ResourceLocation HEAT = LivingBlackHole.id("textures/entity/black_hole/glow.png");

    private final BlockRenderDispatcher blocks;

    public GravitationalDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GravitationalDebrisEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int packedLight) {
        BlockState state = e.getBlockState();
        if (state.getRenderShape() == RenderShape.INVISIBLE || !e.hasCenter()) {
            return;
        }
        Vec3 c = e.getCenter();
        Vec3 p = e.getPosition(partialTick).add(0.0, 0.5, 0.0);
        Vec3 to = c.subtract(p);
        double d = to.length();
        float horizon = e.getHorizon();
        if (d < horizon * 0.9 || d < 1.0E-3) {
            return;
        }
        float near = Mth.clamp(1.0F - (float) (d - horizon) / 12.0F, 0.0F, 1.0F);
        float shrink = Mth.clamp((float) (d - horizon) / 7.0F, 0.12F, 1.0F);
        float stretch = 1.0F + 2.8F * near * near;
        float age = e.tickCount + partialTick;
        int seed = e.getId();

        ps.pushPose();
        ps.translate(0.0, 0.5, 0.0);
        Vec3 dir = to.scale(1.0 / d);
        ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z));
        float thin = shrink / Mth.sqrt(stretch);
        ps.scale(thin, shrink * stretch, thin);
        ps.mulPose(Axis.YP.rotationDegrees(age * (6.0F + (seed % 7)) + seed * 37.0F));
        ps.mulPose(Axis.XP.rotationDegrees(age * (4.0F + (seed % 5)) * (1.0F + near * 3.0F)));
        ps.translate(-0.5, -0.5, -0.5);
        int light = near > 0.35F ? LightTexture.FULL_BRIGHT : packedLight;
        this.blocks.renderSingleBlock(state, ps, buffer, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();

        if (near > 0.05F) {
            float k = near * near;
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(HEAT));
            ps.pushPose();
            ps.translate(0.0, 0.5, 0.0);
            RenderHelper.billboard(glow, ps, this.entityRenderDispatcher.cameraOrientation(), 0.9F + near * 0.8F, age * 0.1F,
                    1.0F * k, 0.45F * k, 0.15F * k, 1.0F);
            ps.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GravitationalDebrisEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
