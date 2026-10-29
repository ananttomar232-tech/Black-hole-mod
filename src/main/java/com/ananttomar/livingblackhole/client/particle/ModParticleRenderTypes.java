package com.ananttomar.livingblackhole.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

public final class ModParticleRenderTypes {
    /**
     * Additive, depth-tested, non-depth-writing particle sheet. Overlapping plasma accumulates
     * light instead of stacking grey alpha, which is what sells the glow.
     */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "LIVINGBLACKHOLE_ADDITIVE";
        }
    };

    private ModParticleRenderTypes() {
    }
}
