package com.ananttomar.livingblackhole.entity;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.fx.BlackHoleClientFx;
import com.ananttomar.livingblackhole.network.CinematicPacket;
import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.registry.ModDamageTypes;
import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.registry.ModTags;
import com.ananttomar.livingblackhole.singularity.SingularityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * The escaped singularity. It never moves; it grows, devours every block within its gravitational
 * reach, drags non-player entities across the event horizon and, when an Anti-Gravity Bomb reaches
 * its centre, runs a scripted destabilise -> implode -> detonate -> shockwave sequence.
 *
 * <p>The entity position is the exact centre of the event horizon.</p>
 */
public class BlackHoleEntity extends Entity {
    public static final int STATE_BIRTH = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_DESTABILIZING = 2;
    public static final int STATE_COLLAPSE = 3;
    public static final int STATE_SHOCKWAVE = 4;

    public static final int BIRTH_TICKS = 70;
    public static final int DESTABILIZE_TICKS = 90;
    public static final int COLLAPSE_TICKS = 14;
    public static final int SHOCKWAVE_TICKS = 90;
    /** Ticks for the gravitational reach to expand from its seed size to the configured radius. */
    public static final int REACH_GROWTH_TICKS = 20 * 75;

    /** Fixed inclination of the accretion disk, shared by the renderer and the particle system. */
    private static final Quaternionf DISK_ROTATION = new Quaternionf().rotateZ(0.11F).rotateX(0.27F);
    private static final Vector3f DISK_AXIS = DISK_ROTATION.transform(new Vector3f(0.0F, 1.0F, 0.0F));

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_AGE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MASS = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_REACH = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID eventId;
    @Nullable
    private UUID neutralizer;
    private int lifeTicks;
    private boolean neutralizedCleanly;
    @Nullable
    private ChunkPos forcedChunk;

    /** Client-only: local tick at which the current synced state was first observed. */
    public int clientStateStart;

    public BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static Quaternionf diskRotation() {
        return new Quaternionf(DISK_ROTATION);
    }

    public static Vector3f diskAxis() {
        return new Vector3f(DISK_AXIS);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, STATE_BIRTH);
        this.entityData.define(DATA_STATE_AGE, 0);
        this.entityData.define(DATA_MASS, 0);
        this.entityData.define(DATA_REACH, 8.0F);
    }

    // ------------------------------------------------------------------ accessors

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setState(int state) {
        this.entityData.set(DATA_STATE, state);
        this.entityData.set(DATA_STATE_AGE, 0);
    }

    public int getStateAge() {
        return this.entityData.get(DATA_STATE_AGE);
    }

    public int getMass() {
        return this.entityData.get(DATA_MASS);
    }

    public void setMass(int mass) {
        this.entityData.set(DATA_MASS, Math.max(0, mass));
    }

    public float getReach() {
        return this.entityData.get(DATA_REACH);
    }

    @Nullable
    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(@Nullable UUID eventId) {
        this.eventId = eventId;
    }

    public int getLifeTicks() {
        return lifeTicks;
    }

    public boolean isDormantOrDying() {
        return getState() >= STATE_DESTABILIZING;
    }

    /** Radius of the event horizon itself, before birth/collapse scaling. Grows with mass. */
    public float getHorizonRadius() {
        return 4.0F + 3.2F * (1.0F - (float) Math.exp(-getMass() / 6000.0));
    }

    /**
     * Visual scale multiplier in [0, ~1.1] driven by the state machine: grows during birth, jitters
     * while destabilising, crushes to a point during the collapse and is gone afterwards.
     */
    public float getVisualScale(float partialTick) {
        float age = stateAge(partialTick);
        return switch (getState()) {
            case STATE_BIRTH -> {
                float t = Mth.clamp(age / BIRTH_TICKS, 0.0F, 1.0F);
                // Overshooting ease: a violent inflation that settles.
                float s = 1.0F - (float) Math.pow(1.0F - t, 3.0);
                yield s * (1.0F + 0.12F * Mth.sin(t * Mth.PI));
            }
            case STATE_DESTABILIZING -> {
                float t = Mth.clamp(age / DESTABILIZE_TICKS, 0.0F, 1.0F);
                float jitter = Mth.sin(age * (1.3F + t * 3.0F)) * 0.05F * t + Mth.sin(age * 7.1F) * 0.025F * t;
                yield 1.0F + t * 0.18F + jitter;
            }
            case STATE_COLLAPSE -> {
                float t = Mth.clamp(age / COLLAPSE_TICKS, 0.0F, 1.0F);
                yield 1.18F * (1.0F - t * t * (3.0F - 2.0F * t));
            }
            case STATE_SHOCKWAVE -> 0.0F;
            default -> 1.0F;
        };
    }

    /** 0 while calm, rising to 1 across destabilisation and the implosion. */
    public float getInstability(float partialTick) {
        float age = stateAge(partialTick);
        return switch (getState()) {
            case STATE_DESTABILIZING -> Mth.clamp(age / DESTABILIZE_TICKS, 0.0F, 1.0F);
            case STATE_COLLAPSE, STATE_SHOCKWAVE -> 1.0F;
            default -> 0.0F;
        };
    }

    /** State age interpolated for rendering; the client extrapolates between data syncs. */
    public float stateAge(float partialTick) {
        if (this.level().isClientSide) {
            return Math.max(0, this.tickCount - this.clientStateStart) + partialTick;
        }
        return getStateAge() + partialTick;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide && (DATA_STATE.equals(key) || DATA_STATE_AGE.equals(key))) {
            // State and age arrive in the same packet but are applied one at a time, so always
            // re-anchor from the latest pair rather than only on a state change.
            clientStateStart = this.tickCount - getStateAge();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlackHoleClientFx.tick(this));
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        this.lifeTicks++;
        int state = getState();
        int age = getStateAge() + 1;
        this.entityData.set(DATA_STATE_AGE, age);

        float maxReach = Config.SUCTION_RADIUS.get();
        float growth = Mth.clamp(lifeTicks / (float) REACH_GROWTH_TICKS, 0.0F, 1.0F);
        float reach = Mth.lerp(1.0F - (1.0F - growth) * (1.0F - growth), Math.min(8.0F, maxReach), maxReach);
        if (Math.abs(reach - getReach()) > 0.05F) {
            this.entityData.set(DATA_REACH, reach);
        }

        switch (state) {
            case STATE_BIRTH -> {
                float f = Mth.clamp(age / (float) BIRTH_TICKS, 0.0F, 1.0F);
                devourBlocks(level, Math.round(Config.BLOCKS_PER_TICK.get() * f * f));
                applyGravity(level, 0.35F + 0.65F * f);
                if (age >= BIRTH_TICKS) {
                    setState(STATE_ACTIVE);
                }
            }
            case STATE_ACTIVE -> {
                devourBlocks(level, Config.BLOCKS_PER_TICK.get());
                applyGravity(level, 1.0F);
            }
            case STATE_DESTABILIZING -> {
                applyGravity(level, 1.4F);
                if (age % 20 == 0) {
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.WARNING_BEEP.get(), SoundSource.HOSTILE, 4.0F, 0.6F + age / (float) DESTABILIZE_TICKS);
                }
                if (age >= DESTABILIZE_TICKS) {
                    setState(STATE_COLLAPSE);
                }
            }
            case STATE_COLLAPSE -> {
                applyGravity(level, 2.6F);
                if (age >= COLLAPSE_TICKS) {
                    detonate(level);
                    setState(STATE_SHOCKWAVE);
                }
            }
            case STATE_SHOCKWAVE -> {
                if (age == 1) {
                    shockwaveKnockback(level);
                }
                if (age >= SHOCKWAVE_TICKS) {
                    this.neutralizedCleanly = true;
                    this.discard();
                    return;
                }
            }
            default -> setState(STATE_ACTIVE);
        }

        if (state < STATE_SHOCKWAVE) {
            updateChunkTicket(level);
        }
        if (lifeTicks % 40 == 0) {
            SingularityManager.onMassChanged(this);
        }
    }

    // ------------------------------------------------------------------ world destruction

    private void devourBlocks(ServerLevel level, int budget) {
        if (budget <= 0 || !Config.DESTROY_BLOCKS.get()) {
            return;
        }
        RandomSource random = this.random;
        Vec3 c = position();
        double reach = getReach();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int maxDebris = Config.MAX_DEBRIS.get();
        int debris = maxDebris <= 0 ? 0 : level.getEntitiesOfClass(GravitationalDebrisEntity.class,
                new AABB(c, c).inflate(reach + 4.0)).size();

        int eaten = 0;
        int attempts = budget * 14;
        while (eaten < budget && attempts-- > 0) {
            // Uniform point in the sphere...
            double u = random.nextDouble();
            double r = reach * Math.cbrt(u);
            double theta = random.nextDouble() * Math.PI * 2.0;
            double cosPhi = random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(1.0 - cosPhi * cosPhi);
            // ...but tidal force falls off with distance: nearer matter is torn loose first.
            double accept = 0.18 + 0.82 * Math.pow(1.0 - r / reach, 0.6);
            if (random.nextDouble() > accept) {
                continue;
            }
            cursor.set(c.x + r * sinPhi * Math.cos(theta), c.y + r * cosPhi, c.z + r * sinPhi * Math.sin(theta));
            if (level.isOutsideBuildHeight(cursor) || !level.isLoaded(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (!canDevour(level, cursor, state)) {
                continue;
            }
            eaten++;
            BlockPos pos = cursor.immutable();
            RenderShape shape = state.getRenderShape();
            boolean visible = shape == RenderShape.MODEL || shape == RenderShape.ENTITYBLOCK_ANIMATED;
            if (visible && debris < maxDebris && random.nextFloat() < 0.7F) {
                debris++;
                level.addFreshEntity(new GravitationalDebrisEntity(level, pos, state, c, getHorizonRadius()));
            } else if (random.nextInt(3) == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (eaten > 0) {
            setMass(getMass() + eaten);
        }
    }

    public static boolean canDevour(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(ModTags.SINGULARITY_IMMUNE)) {
            return false;
        }
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private boolean isAffected(Entity entity) {
        if (!entity.isAlive() || entity == this || entity instanceof Player || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof BlackHoleEntity || entity instanceof GravitationalDebrisEntity || entity instanceof AntiGravityBombEntity) {
            return false;
        }
        return !entity.getType().is(ModTags.SINGULARITY_IMMUNE_ENTITIES) && !entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    private void applyGravity(ServerLevel level, float strength) {
        Vec3 c = position();
        double reach = getReach();
        double horizon = getHorizonRadius() * Math.max(0.3F, getVisualScale(0.0F));

        if (Config.PULL_MOBS.get()) {
            List<Entity> victims = level.getEntities(this, new AABB(c, c).inflate(reach), this::isAffected);
            for (Entity e : victims) {
                Vec3 p = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
                Vec3 to = c.subtract(p);
                double d = to.length();
                if (d > reach || d < 1.0E-4) {
                    continue;
                }
                if (d < horizon * 1.05) {
                    devourEntity(level, e);
                    continue;
                }
                Vec3 dir = to.scale(1.0 / d);
                double falloff = 1.0 - d / reach;
                double accel = (0.035 + 0.32 * falloff * falloff) * strength;
                Vec3 tangent = new Vec3(-dir.z, 0.0, dir.x);
                if (tangent.lengthSqr() > 1.0E-6) {
                    tangent = tangent.normalize().scale(0.05 * falloff * strength);
                }
                Vec3 v = e.getDeltaMovement().scale(0.9).add(dir.scale(accel)).add(tangent);
                double max = 1.9;
                if (v.lengthSqr() > max * max) {
                    v = v.normalize().scale(max);
                }
                e.setDeltaMovement(v);
                e.hasImpulse = true;
                e.hurtMarked = true;
                e.fallDistance = 0.0F;
            }
        }

        // Players are never dragged - but flying into the horizon itself is fatal.
        DamageSource source = ModDamageTypes.singularity(level);
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            double d = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).distanceTo(c);
            if (d < horizon * 1.08) {
                player.hurt(source, 5.0F);
            }
        }
    }

    private void devourEntity(ServerLevel level, Entity e) {
        if (e instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null && e instanceof LivingEntity living && living.hasCustomName()) {
            // Named pets get one last-second escape: flung clear instead of erased.
            Vec3 away = e.position().subtract(position()).normalize().scale(2.5);
            e.setDeltaMovement(away.x, 1.2, away.z);
            e.hurtMarked = true;
            return;
        }
        level.sendParticles(ModParticles.HORIZON_SPARK.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 18, 0.4, 0.4, 0.4, 0.15);
        if (e instanceof LivingEntity living) {
            living.hurt(ModDamageTypes.singularity(level), 10_000.0F);
            if (living.isAlive()) {
                living.discard();
            }
            setMass(getMass() + 4);
        } else {
            e.discard();
            setMass(getMass() + 1);
        }
    }

    // ------------------------------------------------------------------ the climax

    /** Called when an Anti-Gravity Bomb reaches the centre. */
    public boolean beginCollapse(@Nullable Entity cause) {
        if (this.level().isClientSide || getState() >= STATE_DESTABILIZING) {
            return false;
        }
        ServerLevel level = (ServerLevel) this.level();
        this.neutralizer = cause != null ? cause.getUUID() : null;
        setState(STATE_DESTABILIZING);
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(ModSounds.COLLAPSE_BUILDUP.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.COLLAPSE_BUILDUP.get(), SoundSource.HOSTILE, 10.0F, 0.8F);
        ModNetwork.sendToLevel(level, new CinematicPacket(CinematicPacket.COLLAPSE_START, position(), getHorizonRadius()));
        SingularityManager.onCollapseStarted(this);
        return true;
    }

    private void detonate(ServerLevel level) {
        Vec3 c = position();
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(ModSounds.SINGULARITY_EXPLOSION.get(), SoundSource.MASTER, 1.0F, 1.0F);
        }
        level.playSound(null, c.x, c.y, c.z, ModSounds.SINGULARITY_EXPLOSION.get(), SoundSource.HOSTILE, 24.0F, 0.9F);
        ModNetwork.sendToLevel(level, new CinematicPacket(CinematicPacket.DETONATION, c, getHorizonRadius()));

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(c) < 512.0 * 512.0) {
                level.sendParticles(player, ParticleTypes.EXPLOSION_EMITTER, true, c.x, c.y, c.z, 4, 2.5, 2.5, 2.5, 0.0);
                level.sendParticles(player, ParticleTypes.FLASH, true, c.x, c.y, c.z, 6, 1.0, 1.0, 1.0, 0.0);
            }
        }

        int xp = Config.NEUTRALIZE_XP.get();
        if (xp > 0) {
            ExperienceOrb.award(level, c, xp);
        }

        Entity cause = neutralizer != null ? level.getEntity(neutralizer) : null;
        SingularityManager.onNeutralized(this, cause);
        releaseChunkTicket();
        LivingBlackHole.LOGGER.info("Singularity at {} neutralized (mass {}).", BlockPos.containing(c), getMass());
    }

    private void shockwaveKnockback(ServerLevel level) {
        Vec3 c = position();
        double radius = 72.0;
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(radius), Entity::isAlive)) {
            if (e instanceof BlackHoleEntity) {
                continue;
            }
            Vec3 away = e.position().subtract(c);
            double d = away.length();
            if (d < 1.0E-3 || d > radius) {
                continue;
            }
            double force = (1.0 - d / radius) * 3.2;
            Vec3 push = away.scale(1.0 / d).scale(force);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, Math.max(0.35, push.y + 0.5 * force / 3.2), push.z));
            e.hurtMarked = true;
            e.hasImpulse = true;
            if (e instanceof GravitationalDebrisEntity) {
                e.discard();
            }
        }
    }

    // ------------------------------------------------------------------ chunk loading

    private void updateChunkTicket(ServerLevel level) {
        if (!Config.KEEP_CHUNK_LOADED.get()) {
            releaseChunkTicket();
            return;
        }
        ChunkPos chunk = this.chunkPosition();
        if (!chunk.equals(forcedChunk)) {
            releaseChunkTicket();
            ForgeChunkManager.forceChunk(level, LivingBlackHole.MODID, this, chunk.x, chunk.z, true, true);
            forcedChunk = chunk;
        }
    }

    private void releaseChunkTicket() {
        if (forcedChunk != null && this.level() instanceof ServerLevel level) {
            ForgeChunkManager.forceChunk(level, LivingBlackHole.MODID, this, forcedChunk.x, forcedChunk.z, false, true);
        }
        forcedChunk = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            releaseChunkTicket();
            SingularityManager.onBlackHoleRemoved(this, neutralizedCleanly);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ entity plumbing

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_STATE, Mth.clamp(tag.getInt("State"), STATE_BIRTH, STATE_SHOCKWAVE));
        this.entityData.set(DATA_STATE_AGE, tag.getInt("StateAge"));
        setMass(tag.getInt("Mass"));
        this.lifeTicks = tag.getInt("Life");
        this.eventId = tag.hasUUID("EventId") ? tag.getUUID("EventId") : null;
        this.neutralizer = tag.hasUUID("Neutralizer") ? tag.getUUID("Neutralizer") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("State", getState());
        tag.putInt("StateAge", getStateAge());
        tag.putInt("Mass", getMass());
        tag.putInt("Life", lifeTicks);
        if (eventId != null) {
            tag.putUUID("EventId", eventId);
        }
        if (neutralizer != null) {
            tag.putUUID("Neutralizer", neutralizer);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
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
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0 * 1024.0;
    }
}
