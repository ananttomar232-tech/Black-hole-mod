package com.ananttomar.livingblackhole.client.particle;

import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Swallowed matter streaming back into the device held by its shooter. */
public class HomingParticle extends StreakParticle {
    private final int targetId;
    private Vec3 fallback;
    private double speed;

    public HomingParticle(ClientLevel level, double x, double y, double z, int targetId, Vec3 fallback, SpriteSet sprites) {
        super(level, x, y, z, (level.random.nextDouble() - 0.5) * 0.3, level.random.nextDouble() * 0.25, (level.random.nextDouble() - 0.5) * 0.3, sprites);
        this.targetId = targetId;
        this.fallback = fallback;
        this.speed = 0.15 + level.random.nextDouble() * 0.1;
        this.lifetime = 60;
        this.fadeIn = 0.05F;
        this.fadeOut = 0.1F;
        this.stretch(2.0F);
    }

    private Vec3 target() {
        fallback = handTarget(this.level, targetId, fallback);
        return fallback;
    }

    /** Where swallowed matter should fly: the device in the shooter's hand (first-person aware). */
    public static Vec3 handTarget(ClientLevel level, int entityId, Vec3 fallback) {
        Entity e = level.getEntity(entityId);
        if (e instanceof LivingEntity living) {
            Minecraft mc = Minecraft.getInstance();
            if (living == mc.player && mc.options.getCameraType().isFirstPerson()) {
                Vec3 look = living.getLookAngle();
                Vec3 right = new Vec3(-look.z, 0.0, look.x);
                right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
                return living.getEyePosition().add(look.scale(0.55)).add(right.scale(0.28)).add(0.0, -0.26, 0.0);
            }
            return PocketBlackHoleItem.handPosition(living);
        }
        return fallback;
    }

    @Override
    public void tick() {
        Vec3 t = target();
        double dx = t.x - this.x;
        double dy = t.y - this.y;
        double dz = t.z - this.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.35 || this.age > 6 && d < speed) {
            this.remove();
            return;
        }
        speed = Math.min(1.6, speed * 1.09 + 0.01);
        double steer = Math.min(1.0, 0.12 + this.age * 0.03);
        this.xd += (dx / d * speed - this.xd) * steer;
        this.yd += (dy / d * speed - this.yd) * steer;
        this.zd += (dz / d * speed - this.zd) * steer;
        super.tick();
    }
}
