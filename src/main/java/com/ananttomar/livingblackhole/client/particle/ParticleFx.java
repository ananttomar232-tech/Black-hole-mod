package com.ananttomar.livingblackhole.client.particle;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Choreographed particle emitters. Client only. */
public final class ParticleFx {
    private ParticleFx() {
    }

    public static float density() {
        return (float) (double) Config.PARTICLE_DENSITY.get();
    }

    private static void add(Particle p) {
        Minecraft.getInstance().particleEngine.add(p);
    }

    /** Per-tick emission of a living black hole: disk plasma, infalling dust, horizon sparks, jets. */
    public static void blackHole(ClientLevel level, BlackHoleEntity bh, double cameraDistance) {
        if (ModParticleProviders.accretion == null || ModParticleProviders.streak == null || ModParticleProviders.spark == null || ModParticleProviders.jet == null) {
            return;
        }
        RandomSource r = level.random;
        float density = density() * (cameraDistance > 160.0 ? 0.35F : cameraDistance > 80.0 ? 0.7F : 1.0F);
        int state = bh.getState();
        float scale = bh.getVisualScale(0.0F);
        float horizon = bh.getHorizonRadius() * Math.max(scale, 0.05F);
        float instability = bh.getInstability(0.0F);
        Vec3 c = bh.position();
        Quaternionf disk = BlackHoleEntity.diskRotation();
        Vector3f axis = BlackHoleEntity.diskAxis();

        if (state <= BlackHoleEntity.STATE_DESTABILIZING && scale > 0.05F) {
            float speed = 1.0F + instability * 3.0F;
            int disc = Math.round(16 * density * (1.0F + instability * 1.5F));
            for (int i = 0; i < disc; i++) {
                double radius = horizon * (1.45 + Math.pow(r.nextDouble(), 1.6) * 3.6);
                double angle = r.nextDouble() * Math.PI * 2.0;
                double height = (r.nextDouble() - 0.5) * radius * 0.08;
                add(new AccretionParticle(level, c, disk, radius, angle, height, horizon, speed, ModParticleProviders.accretion));
            }

            int dust = Math.round(7 * density);
            float reach = bh.getReach();
            for (int i = 0; i < dust; i++) {
                Vec3 dir = randomUnit(r);
                double dist = reach * (0.35 + r.nextDouble() * 0.65);
                Vec3 p = c.add(dir.scale(dist));
                Vec3 tangent = dir.cross(new Vec3(axis.x(), axis.y(), axis.z()));
                tangent = tangent.lengthSqr() > 1.0E-6 ? tangent.normalize().scale(0.05) : Vec3.ZERO;
                add(new StreakParticle(level, p.x, p.y, p.z, tangent.x, tangent.y, tangent.z, ModParticleProviders.streak)
                        .attract(c, 0.02, horizon)
                        .stretch(3.5F)
                        .color(0.72F + r.nextFloat() * 0.28F, 0.42F + r.nextFloat() * 0.25F, 1.0F)
                        .size(0.03F + r.nextFloat() * 0.04F)
                        .life(120)
                        .alpha(0.85F));
            }

            int sparks = Math.round(4 * density * (1.0F + instability * 4.0F));
            for (int i = 0; i < sparks; i++) {
                Vec3 dir = randomUnit(r);
                Vec3 p = c.add(dir.scale(horizon * (1.02 + r.nextDouble() * 0.25)));
                Vec3 v = dir.cross(new Vec3(axis.x(), axis.y(), axis.z())).scale(0.25 + r.nextDouble() * 0.2);
                add(new GlowParticle(level, p.x, p.y, p.z, v.x, v.y, v.z, ModParticleProviders.spark)
                        .color(1.0F, 0.8F + r.nextFloat() * 0.2F, 0.6F + instability * 0.4F)
                        .size(0.08F + r.nextFloat() * 0.12F)
                        .life(8 + r.nextInt(8))
                        .physics(0.0F, 0.88F));
            }

            int jets = Math.round(3 * density * (1.0F + instability * 3.0F));
            for (int i = 0; i < jets; i++) {
                float sign = r.nextBoolean() ? 1.0F : -1.0F;
                double sp = 0.9 + r.nextDouble() * 0.8 + instability * 1.5;
                Vec3 off = randomUnit(r).scale(horizon * 0.25);
                Vec3 p = c.add(axis.x() * horizon * 0.9 * sign, axis.y() * horizon * 0.9 * sign, axis.z() * horizon * 0.9 * sign).add(off);
                add(new StreakParticle(level, p.x, p.y, p.z, axis.x() * sp * sign, axis.y() * sp * sign, axis.z() * sp * sign, ModParticleProviders.jet)
                        .stretch(2.5F)
                        .color(0.5F + instability * 0.4F, 0.65F + instability * 0.3F, 1.0F)
                        .size(0.25F + r.nextFloat() * 0.25F)
                        .life(18 + r.nextInt(14))
                        .alpha(0.7F)
                        .grow(1.8F)
                        .physics(0.0F, 0.99F));
            }
        }

        if (state == BlackHoleEntity.STATE_COLLAPSE) {
            // Implosion: everything nearby is yanked inward at once.
            int n = Math.round(60 * density);
            for (int i = 0; i < n; i++) {
                Vec3 dir = randomUnit(r);
                double dist = 6.0 + r.nextDouble() * 30.0;
                Vec3 p = c.add(dir.scale(dist));
                Vec3 v = dir.scale(-1.5 - r.nextDouble() * 1.5);
                add(new StreakParticle(level, p.x, p.y, p.z, v.x, v.y, v.z, ModParticleProviders.shock)
                        .attract(c, 0.2, 0.6F)
                        .stretch(5.0F)
                        .color(0.85F, 0.9F, 1.0F)
                        .size(0.08F)
                        .life(30));
            }
        }
    }

    /** The detonation burst: a spherical spark storm plus a flat shock ring in the disk plane. */
    public static void detonation(Vec3 c, float horizon) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || ModParticleProviders.shock == null) {
            return;
        }
        RandomSource r = level.random;
        float density = Math.max(0.3F, density());
        Quaternionf disk = BlackHoleEntity.diskRotation();

        int sphere = Math.round(700 * density);
        for (int i = 0; i < sphere; i++) {
            Vec3 dir = randomUnit(r);
            double sp = 1.2 + r.nextDouble() * 3.5;
            add(new StreakParticle(level, c.x, c.y, c.z, dir.x * sp, dir.y * sp, dir.z * sp, ModParticleProviders.shock)
                    .stretch(5.0F)
                    .color(0.7F + r.nextFloat() * 0.3F, 0.85F + r.nextFloat() * 0.15F, 1.0F)
                    .size(0.12F + r.nextFloat() * 0.25F)
                    .life(30 + r.nextInt(40))
                    .physics(0.0F, 0.955F));
        }

        int ring = Math.round(500 * density);
        for (int i = 0; i < ring; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            Vector3f v = new Vector3f((float) Math.cos(a), (float) ((r.nextDouble() - 0.5) * 0.06), (float) Math.sin(a));
            disk.transform(v);
            double sp = 3.0 + r.nextDouble() * 2.0;
            add(new StreakParticle(level, c.x, c.y, c.z, v.x() * sp, v.y() * sp, v.z() * sp, ModParticleProviders.shock)
                    .stretch(6.0F)
                    .color(1.0F, 0.75F + r.nextFloat() * 0.2F, 0.45F + r.nextFloat() * 0.3F)
                    .size(0.25F + r.nextFloat() * 0.3F)
                    .life(40 + r.nextInt(30))
                    .physics(0.0F, 0.965F));
        }

        int embers = Math.round(260 * density);
        for (int i = 0; i < embers; i++) {
            Vec3 dir = randomUnit(r);
            double sp = 0.3 + r.nextDouble() * 1.2;
            add(new GlowParticle(level, c.x + dir.x * horizon, c.y + dir.y * horizon, c.z + dir.z * horizon,
                    dir.x * sp, dir.y * sp, dir.z * sp, ModParticleProviders.accretion)
                    .color(1.0F, 0.5F + r.nextFloat() * 0.4F, 0.2F + r.nextFloat() * 0.3F)
                    .size(0.4F + r.nextFloat() * 0.9F)
                    .life(60 + r.nextInt(80))
                    .grow(1.2F)
                    .physics(0.01F, 0.97F));
        }
    }

    /** Debris torn loose by the Pocket Black Hole flowing back into the shooter's device. */
    public static void absorb(ClientLevel level, Vec3 from, int shooterId, Vec3 fallback, boolean entity) {
        if (ModParticleProviders.matter == null) {
            return;
        }
        RandomSource r = level.random;
        int n = entity ? 10 : 2;
        for (int i = 0; i < n; i++) {
            add(new HomingParticle(level, from.x + (r.nextDouble() - 0.5) * 0.8, from.y + (r.nextDouble() - 0.5) * 0.8,
                    from.z + (r.nextDouble() - 0.5) * 0.8, shooterId, fallback, ModParticleProviders.matter)
                    .color(0.55F + r.nextFloat() * 0.3F, 0.3F + r.nextFloat() * 0.2F, 1.0F)
                    .size(0.06F + r.nextFloat() * 0.06F));
        }
    }

    public static Vec3 randomUnit(RandomSource r) {
        double z = r.nextDouble() * 2.0 - 1.0;
        double a = r.nextDouble() * Math.PI * 2.0;
        double s = Math.sqrt(1.0 - z * z);
        return new Vec3(s * Math.cos(a), z, s * Math.sin(a));
    }
}
