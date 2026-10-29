package com.ananttomar.livingblackhole.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

/** Full-bright additive particle with fade-in/out and a size curve over its life. */
public class GlowParticle extends TextureSheetParticle {
    protected final SpriteSet sprites;
    protected float baseSize;
    protected float baseAlpha = 1.0F;
    protected float fadeIn = 0.12F;
    protected float fadeOut = 0.45F;
    protected float growth;
    protected boolean animateSprites;

    public GlowParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.pickSprite(sprites);
        this.baseSize = this.quadSize;
    }

    public GlowParticle color(float r, float g, float b) {
        this.setColor(r, g, b);
        return this;
    }

    public GlowParticle size(float size) {
        this.quadSize = size;
        this.baseSize = size;
        return this;
    }

    public GlowParticle life(int ticks) {
        this.lifetime = Math.max(1, ticks);
        return this;
    }

    public GlowParticle alpha(float a) {
        this.baseAlpha = a;
        return this;
    }

    public GlowParticle grow(float growth) {
        this.growth = growth;
        return this;
    }

    public GlowParticle physics(float gravity, float friction) {
        this.gravity = gravity;
        this.friction = friction;
        return this;
    }

    public GlowParticle animated() {
        this.animateSprites = true;
        this.setSpriteFromAge(this.sprites);
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.animateSprites && !this.removed) {
            this.setSpriteFromAge(this.sprites);
        }
    }

    protected float lifeFraction(float partialTick) {
        return Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
    }

    protected float fade(float partialTick) {
        float f = lifeFraction(partialTick);
        float in = fadeIn <= 0.0F ? 1.0F : Math.min(1.0F, f / fadeIn);
        float out = fadeOut <= 0.0F ? 1.0F : Math.min(1.0F, (1.0F - f) / fadeOut);
        return Mth.clamp(Math.min(in, out), 0.0F, 1.0F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.baseSize * (1.0F + this.growth * lifeFraction(partialTick));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        this.alpha = this.baseAlpha * fade(partialTick);
        super.render(buffer, camera, partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.ADDITIVE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}
