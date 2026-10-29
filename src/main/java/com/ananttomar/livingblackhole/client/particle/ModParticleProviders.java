package com.ananttomar.livingblackhole.client.particle;

import com.ananttomar.livingblackhole.registry.ModParticles;
import net.minecraft.client.particle.SpriteSet;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/**
 * Registers providers for every mod particle and keeps the sprite sets so client code can
 * construct parametrised particles (orbits with centres, homing targets...) directly.
 */
public final class ModParticleProviders {
    public static SpriteSet accretion;
    public static SpriteSet streak;
    public static SpriteSet spark;
    public static SpriteSet matter;
    public static SpriteSet antigrav;
    public static SpriteSet glint;
    public static SpriteSet shock;
    public static SpriteSet jet;

    private ModParticleProviders() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ACCRETION.get(), sprites -> {
            accretion = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new GlowParticle(level, x, y, z, xd, yd, zd, sprites)
                    .color(1.0F, 0.62F + level.random.nextFloat() * 0.3F, 0.25F)
                    .size(0.12F + level.random.nextFloat() * 0.1F)
                    .life(30 + level.random.nextInt(20))
                    .physics(0.0F, 0.94F);
        });
        event.registerSpriteSet(ModParticles.DEBRIS_STREAK.get(), sprites -> {
            streak = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new StreakParticle(level, x, y, z, xd, yd, zd, sprites)
                    .color(0.85F, 0.55F, 1.0F)
                    .size(0.06F)
                    .life(25)
                    .physics(0.0F, 0.96F);
        });
        event.registerSpriteSet(ModParticles.HORIZON_SPARK.get(), sprites -> {
            spark = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new GlowParticle(level, x, y, z, xd, yd, zd, sprites)
                    .color(0.78F + level.random.nextFloat() * 0.22F, 0.45F + level.random.nextFloat() * 0.3F, 1.0F)
                    .size(0.05F + level.random.nextFloat() * 0.07F)
                    .life(10 + level.random.nextInt(10))
                    .physics(0.0F, 0.9F);
        });
        event.registerSpriteSet(ModParticles.MATTER_STREAM.get(), sprites -> {
            matter = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new GlowParticle(level, x, y, z, xd, yd, zd, sprites)
                    .color(0.62F, 0.32F, 1.0F)
                    .size(0.07F + level.random.nextFloat() * 0.05F)
                    .life(25 + level.random.nextInt(20))
                    .physics(0.0F, 0.97F);
        });
        event.registerSpriteSet(ModParticles.ANTIGRAV_SPARK.get(), sprites -> {
            antigrav = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new GlowParticle(level, x, y, z, xd, yd, zd, sprites)
                    .color(0.35F + level.random.nextFloat() * 0.2F, 0.92F, 1.0F)
                    .size(0.04F + level.random.nextFloat() * 0.06F)
                    .life(22 + level.random.nextInt(18))
                    .physics(-0.06F, 0.96F)
                    .grow(-0.6F);
        });
        event.registerSpriteSet(ModParticles.CORE_GLINT.get(), sprites -> {
            glint = sprites;
            // Velocity arguments carry the orbit centre (spawned from Block#animateTick only).
            return (type, level, x, y, z, cx, cy, cz) -> new OrbitParticle(level, x, y, z, cx, cy, cz, sprites);
        });
        event.registerSpriteSet(ModParticles.SHOCK_SPARK.get(), sprites -> {
            shock = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new StreakParticle(level, x, y, z, xd, yd, zd, sprites)
                    .stretch(4.0F)
                    .color(0.75F, 0.95F, 1.0F)
                    .size(0.12F)
                    .life(30 + level.random.nextInt(20))
                    .physics(0.0F, 0.95F);
        });
        event.registerSpriteSet(ModParticles.JET_PLASMA.get(), sprites -> {
            jet = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new GlowParticle(level, x, y, z, xd, yd, zd, sprites)
                    .color(0.55F, 0.7F, 1.0F)
                    .size(0.3F)
                    .life(20)
                    .grow(1.5F);
        });
    }
}
