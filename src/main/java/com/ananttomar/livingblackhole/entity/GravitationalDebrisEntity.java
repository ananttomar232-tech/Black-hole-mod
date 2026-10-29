package com.ananttomar.livingblackhole.entity;

import com.ananttomar.livingblackhole.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A block torn out of the world, tumbling and spaghettifying on its way across the event horizon.
 * Motion is integrated identically on both sides from the synced centre, so the server only needs
 * to send occasional corrections.
 */
public class GravitationalDebrisEntity extends Entity {
    private static final EntityDataAccessor<BlockState> DATA_BLOCK = SynchedEntityData.defineId(GravitationalDebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Vector3f> DATA_CENTER = SynchedEntityData.defineId(GravitationalDebrisEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_HORIZON = SynchedEntityData.defineId(GravitationalDebrisEntity.class, EntityDataSerializers.FLOAT);

    public static final int MAX_LIFE = 420;

    public GravitationalDebrisEntity(EntityType<? extends GravitationalDebrisEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public GravitationalDebrisEntity(Level level, BlockPos origin, BlockState state, Vec3 center, float horizon) {
        this(ModEntities.GRAVITATIONAL_DEBRIS.get(), level);
        this.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        this.entityData.set(DATA_BLOCK, state);
        this.entityData.set(DATA_CENTER, new Vector3f((float) center.x, (float) center.y, (float) center.z));
        this.entityData.set(DATA_HORIZON, horizon);

        // Initial kick: the block is wrenched loose (slight lift) and already shares some of the
        // disk's angular momentum, so debris spirals in rather than falling straight.
        Vec3 to = center.subtract(blockCenter());
        Vec3 dir = to.lengthSqr() > 1.0E-6 ? to.normalize() : new Vec3(0.0, 1.0, 0.0);
        Vec3 tangent = new Vec3(-dir.z, 0.0, dir.x);
        tangent = tangent.lengthSqr() > 1.0E-6 ? tangent.normalize() : Vec3.ZERO;
        double swirl = 0.06 + this.random.nextDouble() * 0.12;
        this.setDeltaMovement(dir.scale(0.04).add(0.0, 0.08 + this.random.nextDouble() * 0.1, 0.0).add(tangent.scale(swirl)));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_BLOCK, Blocks.STONE.defaultBlockState());
        this.entityData.define(DATA_CENTER, new Vector3f());
        this.entityData.define(DATA_HORIZON, 2.5F);
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_BLOCK);
    }

    public Vec3 getCenter() {
        Vector3f c = this.entityData.get(DATA_CENTER);
        return new Vec3(c.x(), c.y(), c.z());
    }

    public float getHorizon() {
        return this.entityData.get(DATA_HORIZON);
    }

    public Vec3 blockCenter() {
        return position().add(0.0, 0.5, 0.0);
    }

    public boolean hasCenter() {
        Vector3f c = this.entityData.get(DATA_CENTER);
        return c.x() != 0.0F || c.y() != 0.0F || c.z() != 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!hasCenter()) {
            if (!this.level().isClientSide && this.tickCount > 5) {
                discard();
            }
            return;
        }
        Vec3 c = getCenter();
        Vec3 p = blockCenter();
        Vec3 to = c.subtract(p);
        double d = to.length();
        float horizon = getHorizon();

        if (!this.level().isClientSide && (d < horizon * 0.85 || this.tickCount > MAX_LIFE)) {
            discard();
            return;
        }
        if (d < horizon * 0.8) {
            // Client: hold position behind the horizon until the server removes us.
            return;
        }

        Vec3 dir = to.scale(1.0 / d);
        // Inverse-square pull, softened near the centre, plus a constant baseline so distant
        // debris never stalls.
        double accel = 0.03 + 3.4 / (d * d + 6.0);
        Vec3 v = getDeltaMovement().add(dir.scale(accel));

        // Bleed off tangential momentum so orbits always decay into the horizon.
        double radial = v.dot(dir);
        Vec3 radialV = dir.scale(radial);
        Vec3 tangentialV = v.subtract(radialV).scale(0.975);
        v = radialV.add(tangentialV);

        double max = 1.4 + 3.0 / (d + 1.0);
        double speed = v.length();
        if (speed > max) {
            v = v.scale(max / speed);
        }

        if (!this.level().isClientSide) {
            // Catch fast debris that would step straight across the horizon in one tick.
            Vec3 next = p.add(v);
            double t = Math.max(0.0, Math.min(1.0, c.subtract(p).dot(v) / Math.max(v.lengthSqr(), 1.0E-6)));
            if (p.add(v.scale(t)).distanceTo(c) < horizon * 0.85 || next.distanceTo(c) < horizon * 0.85) {
                discard();
                return;
            }
        }

        this.setDeltaMovement(v);
        this.setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }
}
