package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.ClientEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a Compressed Chunk as a real 3D miniature of the structure stored in its NBT - the exact
 * house, hill or lake that was swallowed, block for block - floating inside its glowing containment
 * cage. Used in the inventory, in hand, on the ground and in item frames. A chunk with nothing inside
 * falls back to a terrain-cube model.
 */
public final class CompressedChunkItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation CAGE = LivingBlackHole.id("item/compressed_chunk_cage");
    public static final ResourceLocation EMPTY = LivingBlackHole.id("item/compressed_chunk_empty");
    /** Largest extent of the miniature inside the cage, in item units (1 = one block). */
    private static final float FIT = 0.62F;
    /** Height of the miniature's centre: the middle of the cage, above the singularity seed. */
    private static final float CENTER_Y = 7.0F / 16.0F;

    /** Diagnostics for the CI self-test. */
    public static int renders;
    public static int miniatures;

    private static CompressedChunkItemRenderer instance;

    private CompressedChunkItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    public static CompressedChunkItemRenderer get() {
        if (instance == null) {
            instance = new CompressedChunkItemRenderer(Minecraft.getInstance());
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        renders++;
        Minecraft mc = Minecraft.getInstance();
        StructureMesh mesh = StructureMeshCache.forStack(stack);
        if (mesh == null || mesh.isEmpty()) {
            renderModel(mc, EMPTY, stack, ps, buffers, light, overlay);
            return;
        }
        miniatures++;
        ps.pushPose();
        ps.translate(0.5F, CENTER_Y, 0.5F);
        if (context == ItemDisplayContext.GROUND) {
            ps.mulPose(Axis.YP.rotationDegrees((ClientEffects.ticks() + mc.getFrameTime()) * 2.0F));
        }
        float s = FIT / Math.max(1.0F, mesh.span());
        ps.scale(s, s, s);
        ps.translate(-mesh.centerX(), -mesh.centerY(), -mesh.centerZ());
        float f = brightness(mc, light);
        mesh.draw(ps, buffers, light, f, f, f);
        ps.popPose();
        renderModel(mc, CAGE, stack, ps, buffers, light, overlay);
    }

    /** The miniature is baked fully lit; dim it to match where the item is (a dark cave, the night). */
    private static float brightness(Minecraft mc, int light) {
        float block = LightTexture.block(light) / 15.0F;
        float sky = LightTexture.sky(light) / 15.0F;
        if (mc.level != null) {
            sky *= mc.level.getSkyDarken(mc.getFrameTime());
        }
        return 0.3F + 0.7F * Math.max(block, sky);
    }

    private static void renderModel(Minecraft mc, ResourceLocation id, ItemStack stack, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = mc.getModelManager().getModel(id);
        ps.pushPose();
        // ItemRenderer centres the model on the origin; our space is the 0..1 item cube.
        ps.translate(0.5F, 0.5F, 0.5F);
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, ps, buffers, light, overlay, model);
        ps.popPose();
    }
}
