package com.ananttomar.livingblackhole.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Plasma on a decaying Keplerian orbit inside the (tilted) accretion disk. Angular velocity
 * scales with r^-1.5, the orbit shrinks faster near the horizon, the disk thickness collapses,
 * and colour follows the temperature gradient from white-hot inner edge to red outer rim.
 */
public class AccretionParticle extends GlowParticle {
    private final Vec3 center;
    private final Quaternionf rotation;
    private final float horizon;
    private final double omega0;
    private final double inflow;
    private double radius;
    private double angle;
    private double height;
    private final float heatBias;

    public AccretionParticle(ClientLevel level, Vec3 center, Quaternionf rotation, double radius, double angle, double height,
                             float horizon, float speed, SpriteSet sprites) {
        super(level, center.x, center.y, center.z, 0.0, 0.0, 0.0, sprites);
        this.center = center;
        this.rotation = new Quaternionf(rotation);
        this.radius = radius;
        this.angle = angle;
        this.height = height;
        this.horizon = horizon;
        this.omega0 = 0.30 * speed;
        this.inflow = (0.003 + this.random.nextDouble() * 0.008) * Math.max(0.5, speed);
        this.heatBias = this.random.nextFloat() * 0.15F;
        this.lifetime = 70 + this.random.nextInt(90);
        this.size(0.10F + this.random.nextFloat() * 0.24F + (float) radius * 0.012F);
        this.fadeIn = 0.15F;
        this.fadeOut = 0.3F;
        place();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        recolor();
    }

    private void place() {
        Vector3f local = new Vector3f((float) (Math.cos(angle) * radius), (float) height, (float) (Math.sin(angle) * radius));
        rotation.transform(local);
        this.setPos(center.x + local.x(), center.y + local.y(), center.z + local.z());
    }

    private void recolor() {
        float t = Mth.clamp((float) (horizon / radius) + heatBias, 0.0F, 1.0F);
        float r;
        float g;
        float b;
        if (t > 0.6F) {
            float k = (t - 0.6F) / 0.4F;
            r = 1.0F;
            g = Mth.lerp(k, 0.62F, 0.96F);
            b = Mth.lerp(k, 0.22F, 0.88F);
        } else if (t > 0.3F) {
            float k = (t - 0.3F) / 0.3F;
            r = Mth.lerp(k, 0.9F, 1.0F);
            g = Mth.lerp(k, 0.25F, 0.62F);
            b = Mth.lerp(k, 0.06F, 0.22F);
        } else {
            float k = t / 0.3F;
            r = Mth.lerp(k, 0.55F, 0.9F);
            g = Mth.lerp(k, 0.08F, 0.25F);
            b = Mth.lerp(k, 0.18F, 0.06F);
        }
        this.setColor(r, g, b);
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
        double omega = omega0 * Math.pow(horizon / Math.max(radius, horizon), 1.5);
        angle += omega;
        radius -= radius * inflow * (1.0 + 2.5 * horizon / radius);
        height *= 0.982;
        if (radius < horizon * 1.08) {
            this.remove();
            return;
        }
        place();
        recolor();
    }
}
