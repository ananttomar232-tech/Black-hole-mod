package com.ananttomar.livingblackhole.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/** A crystalline glint circling a Core Fragment while drifting upward. */
public class OrbitParticle extends GlowParticle {
    private final double cx;
    private final double cz;
    private double radius;
    private double angle;
    private final double omega;

    public OrbitParticle(ClientLevel level, double x, double y, double z, double cx, double cy, double cz, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites);
        this.cx = cx;
        this.cz = cz;
        double dx = x - cx;
        double dz = z - cz;
        this.radius = Math.sqrt(dx * dx + dz * dz);
        this.angle = Math.atan2(dz, dx);
        this.omega = (0.05 + this.random.nextDouble() * 0.06) * (this.random.nextBoolean() ? 1 : -1);
        this.yd = 0.01 + this.random.nextDouble() * 0.015;
        this.lifetime = 40 + this.random.nextInt(30);
        this.size(0.06F + this.random.nextFloat() * 0.07F);
        this.setColor(0.45F + this.random.nextFloat() * 0.2F, 0.95F, 1.0F);
        this.animated();
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
        angle += omega;
        radius *= 0.992;
        this.setPos(cx + Math.cos(angle) * radius, this.y + this.yd, cz + Math.sin(angle) * radius);
        this.setSpriteFromAge(this.sprites);
    }
}
