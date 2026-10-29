package com.ananttomar.livingblackhole.entity;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.network.CinematicPacket;
import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.registry.ModEntities;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * The thrown Anti-Gravity Bomb. Outside a gravity well it is inert and drops back as an item when
 * it lands. Inside the capture radius of a black hole, the singularity's own gravity seizes it,
 * ignores every obstacle and spirals it into the centre, which begins the collapse.
 */
public class AntiGravityBombEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(AntiGravityBombEntity.class, EntityDataSerializers.INT);

    private int capturedTicks;

    public AntiGravityBombEntity(EntityType<? extends AntiGravityBombEntity> type, Level level) {
        super(type, level);
    }

    public AntiGravityBombEntity(Level level, LivingEntity shooter) {
        super(ModEntities.ANTI_GRAVITY_BOMB.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TARGET, -1);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ANTI_GRAVITY_BOMB.get();
    }

    @Override
    protected float getGravity() {
        // Anti-gravity: it barely falls even when free.
        return isCaptured() ? 0.0F : 0.012F;
    }

    public boolean isCaptured() {
        return this.entityData.get(DATA_TARGET) >= 0;
    }

    public int getCapturedTicks() {
        return capturedTicks;
    }

    @Nullable
    public BlackHoleEntity getTarget() {
        int id = this.entityData.get(DATA_TARGET);
        if (id < 0) {
            return null;
        }
        Entity e = this.level().getEntity(id);
        return e instanceof BlackHoleEntity bh && !bh.isRemoved() ? bh : null;
    }

    @Override
    public void tick() {
        BlackHoleEntity target = getTarget();
        if (target == null && !this.level().isClientSide) {
            if (isCaptured()) {
                // Our singularity vanished from under us.
                fizzle();
                return;
            }
            target = findGravityWell();
            if (target != null) {
                capture(target);
            }
        }

        if (target != null) {
            capturedTicks++;
            steerInto(target);
        }

        super.tick();
        if (this.isRemoved()) {
            return;
        }

        if (this.level().isClientSide) {
            spawnTrail(target != null);
            return;
        }

        if (target != null) {
            double d = position().distanceTo(target.position());
            if (d < 1.6 || capturedTicks > 400) {
                target.beginCollapse(getOwner());
                discard();
            }
        } else if (this.tickCount > 1200) {
            fizzle();
        }
    }

    @Nullable
    private BlackHoleEntity findGravityWell() {
        double radius = Config.BOMB_CAPTURE_RADIUS.get();
        return this.level().getEntitiesOfClass(BlackHoleEntity.class, getBoundingBox().inflate(radius),
                        bh -> bh.isAlive() && bh.getState() <= BlackHoleEntity.STATE_ACTIVE && bh.distanceTo(this) <= radius)
                .stream()
                .min(Comparator.comparingDouble(bh -> bh.distanceToSqr(this)))
                .orElse(null);
    }

    private void capture(BlackHoleEntity target) {
        this.entityData.set(DATA_TARGET, target.getId());
        this.setNoGravity(true);
        this.level().playSound(null, getX(), getY(), getZ(), ModSounds.BOMB_CAPTURED.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
        if (this.level() instanceof ServerLevel serverLevel) {
            ModNetwork.sendToLevel(serverLevel, new CinematicPacket(CinematicPacket.BOMB_CAPTURED, target.position(), 0.0F));
        }
        if (getOwner() instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.livingblackhole.bomb_captured").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        }
    }

    private void steerInto(BlackHoleEntity target) {
        Vec3 to = target.position().subtract(position());
        double d = to.length();
        if (d < 1.0E-4) {
            return;
        }
        Vec3 dir = to.scale(1.0 / d);
        double capture = Config.BOMB_CAPTURE_RADIUS.get();
        // Speed climbs as it falls deeper; the tangential term gives it a short spiral on the way.
        double speed = Mth.clamp(0.45 + (capture - d) * 0.055, 0.45, 2.4);
        speed = Math.min(speed, Math.max(d, 0.2));
        Vec3 tangent = new Vec3(-dir.z, 0.0, dir.x);
        tangent = tangent.lengthSqr() > 1.0E-6 ? tangent.normalize().scale(Math.min(0.35, d * 0.02)) : Vec3.ZERO;
        if (d < 5.0) {
            tangent = Vec3.ZERO;
        }
        Vec3 desired = dir.scale(speed).add(tangent);
        double grip = Math.min(1.0, 0.18 + capturedTicks * 0.02);
        this.setDeltaMovement(getDeltaMovement().lerp(desired, grip));
    }

    private void spawnTrail(boolean captured) {
        Vec3 v = getDeltaMovement();
        int count = captured ? 5 : 2;
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            this.level().addParticle(ModParticles.ANTIGRAV_SPARK.get(),
                    getX() - v.x * t + (this.random.nextDouble() - 0.5) * 0.2,
                    getY() + 0.2 - v.y * t + (this.random.nextDouble() - 0.5) * 0.2,
                    getZ() - v.z * t + (this.random.nextDouble() - 0.5) * 0.2,
                    (this.random.nextDouble() - 0.5) * 0.02, 0.02 + this.random.nextDouble() * 0.03, (this.random.nextDouble() - 0.5) * 0.02);
        }
        if (captured) {
            this.level().addParticle(ModParticles.HORIZON_SPARK.get(), getX(), getY() + 0.2, getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, (this.random.nextDouble() - 0.5) * 0.1, (this.random.nextDouble() - 0.5) * 0.1);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (isCaptured() || entity instanceof BlackHoleEntity || entity instanceof GravitationalDebrisEntity) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult result) {
        if (isCaptured()) {
            // Gravity has it now - walls, water and mobs are irrelevant.
            return;
        }
        super.onHit(result);
        if (!this.level().isClientSide) {
            fizzle();
        }
    }

    /** Outside a gravity well the bomb has nothing to invert; it powers down and drops intact. */
    private void fizzle() {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.2, getZ(), getItem().copy());
        drop.setDefaultPickUpDelay();
        drop.setDeltaMovement(0.0, 0.15, 0.0);
        level.addFreshEntity(drop);
        level.sendParticles(ModParticles.ANTIGRAV_SPARK.get(), getX(), getY() + 0.2, getZ(), 26, 0.3, 0.3, 0.3, 0.04);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BOMB_FIZZLE.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        if (getOwner() instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.livingblackhole.bomb_inert").withStyle(ChatFormatting.GRAY), true);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }
}
