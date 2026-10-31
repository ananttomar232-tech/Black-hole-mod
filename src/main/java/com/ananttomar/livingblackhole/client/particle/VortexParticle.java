package com.ananttomar.livingblackhole.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;

/**
 * Matter from a swallowed chunk. It whirls around the region's centre on a tightening spiral (angular
 * speed rising as the radius shrinks, like water down a drain), then streaks into the shooter's
 * device. With {@code reverse} the motion runs backwards: matter bursts out of the hand and
 * spirals open into the region being rebuilt.
 */
public class VortexParticle extends StreakParticle {
    private final Vec3 center;
    private final int shooterId;
    private final boolean reverse;
    private Vec3 handFallback;
    private double radius;
    private double angle;
    private double height;
    private final double direction;
    private final double targetRadius;
    private boolean homing;
    private double speed = 0.2;

    public VortexParticle(ClientLevel level, Vec3 center, double radius, double angle, double height, int shooterId,
                          Vec3 hand, boolean reverse, SpriteSet sprites) {
        super(level, center.x, center.y, center.z, 0.0, 0.0, 0.0, sprites);
        this.center = center;
        this.shooterId = shooterId;
        this.handFallback = hand;
        this.reverse = reverse;
        this.direction = level.random.nextBoolean() ? 1.0 : 0.85;
        this.targetRadius = radius;
        this.radius = reverse ? 0.4 : radius;
        this.angle = angle;
        this.height = reverse ? 0.0 : height;
        this.lifetime = reverse ? 45 + level.random.nextInt(15) : 90;
        this.fadeIn = 0.05F;
        this.fadeOut = reverse ? 0.5F : 0.08F;
        this.stretch(2.2F);
        place();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    private void place() {
        this.setPos(center.x + Math.cos(angle) * radius, center.y + height, center.z + Math.sin(angle) * radius);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (homing) {
            Vec3 t = HomingParticle.handTarget(this.level, shooterId, handFallback);
            handFallback = t;
            Vec3 to = t.subtract(this.x, this.y, this.z);
            double d = to.length();
            if (d < 0.35 || d < speed) {
                this.remove();
                return;
            }
            speed = Math.min(1.8, speed * 1.12 + 0.02);
            Vec3 v = to.scale(speed / d);
            moveTo(this.x + v.x, this.y + v.y, this.z + v.z);
            return;
        }
        double omega = direction * (0.09 + 1.1 / (radius + 1.2));
        angle += omega;
        if (reverse) {
            radius += (targetRadius - radius) * 0.09;
            height += ((targetRadius * 0.9) * Math.sin(angle * 0.37) - height) * 0.03;
        } else {
            radius *= 0.92;
            height *= 0.9;
            if (radius < 0.6) {
                homing = true;
            }
        }
        double px = this.x;
        double py = this.y;
        double pz = this.z;
        place();
        this.xd = this.x - px;
        this.yd = this.y - py;
        this.zd = this.z - pz;
    }

    private void moveTo(double nx, double ny, double nz) {
        this.xd = nx - this.x;
        this.yd = ny - this.y;
        this.zd = nz - this.z;
        this.setPos(nx, ny, nz);
    }
}
