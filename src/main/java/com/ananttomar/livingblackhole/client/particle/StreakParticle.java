package com.ananttomar.livingblackhole.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A glowing streak rendered as a camera-facing ribbon stretched along its own velocity, so fast
 * matter reads as motion-blurred light. Optionally falls toward an attractor.
 */
public class StreakParticle extends GlowParticle {
    @Nullable
    private Vec3 attractor;
    private double pull;
    private float horizon;
    private float stretch = 3.0F;

    public StreakParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
    }

    public StreakParticle attract(Vec3 center, double pull, float horizon) {
        this.attractor = center;
        this.pull = pull;
        this.horizon = horizon;
        return this;
    }

    public StreakParticle stretch(float stretch) {
        this.stretch = stretch;
        return this;
    }

    @Override
    public void tick() {
        if (attractor != null) {
            double dx = attractor.x - this.x;
            double dy = attractor.y - this.y;
            double dz = attractor.z - this.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < horizon) {
                this.remove();
                return;
            }
            double a = pull + 2.2 / (d * d + 2.0);
            this.xd += dx / d * a;
            this.yd += dy / d * a;
            this.zd += dz / d * a;
            double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
            double max = 0.8 + 3.0 / (d + 1.0);
            if (speed > max) {
                xd *= max / speed;
                yd *= max / speed;
                zd *= max / speed;
            }
        }
        super.tick();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        this.alpha = this.baseAlpha * fade(partialTick);
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());

        Vector3f vel = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
        float speed = vel.length();
        float width = getQuadSize(partialTick);
        if (speed < 1.0E-4F) {
            super.render(buffer, camera, partialTick);
            return;
        }
        vel.div(speed);
        Vector3f toCam = new Vector3f(-px, -py, -pz);
        if (toCam.lengthSquared() < 1.0E-6F) {
            return;
        }
        toCam.normalize();
        Vector3f side = new Vector3f(vel).cross(toCam);
        if (side.lengthSquared() < 1.0E-6F) {
            super.render(buffer, camera, partialTick);
            return;
        }
        side.normalize().mul(width);
        float length = Math.max(width, speed * stretch);
        Vector3f head = new Vector3f(vel).mul(width * 0.5F);
        Vector3f tail = new Vector3f(vel).mul(-length);

        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTick);

        vertex(buffer, px + tail.x - side.x, py + tail.y - side.y, pz + tail.z - side.z, u1, v1, light);
        vertex(buffer, px + tail.x + side.x, py + tail.y + side.y, pz + tail.z + side.z, u1, v0, light);
        vertex(buffer, px + head.x + side.x, py + head.y + side.y, pz + head.z + side.z, u0, v0, light);
        vertex(buffer, px + head.x - side.x, py + head.y - side.y, pz + head.z - side.z, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }
}
