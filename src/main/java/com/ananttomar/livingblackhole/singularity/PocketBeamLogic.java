package com.ananttomar.livingblackhole.singularity;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.entity.GravitationalDebrisEntity;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.ananttomar.livingblackhole.network.BeamFxPacket;
import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side resolution of a Pocket Black Hole shot.
 *
 * <p>The beam is a cone along the player's view, out to the configured 15 block reach. Its width
 * grows with charge. Every breakable block and every non-player entity inside it is swallowed
 * nearest-first, converted 1:1 into Compressed Matter and counted against the containment limit.
 * Block removal goes through {@link BlockEvent.BreakEvent} so claim/protection mods are honoured.</p>
 */
public final class PocketBeamLogic {
    private PocketBeamLogic() {
    }

    private record Candidate(BlockPos pos, BlockState state, double depth) {
    }

    public static void fire(ServerLevel level, ServerPlayer player, ItemStack stack, InteractionHand hand, float power) {
        int limit = PocketBlackHoleItem.limit();
        int consumed = PocketBlackHoleItem.getConsumed(stack);
        int capacity = Math.max(0, limit - consumed);
        int perShot = Math.max(1, Math.round(Config.POCKET_MAX_BLOCKS_PER_SHOT.get() * (0.35F + 0.65F * power)));
        double range = Config.POCKET_RANGE.get();

        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double halfAngle = Math.toRadians(Mth.lerp(power, 3.0F, 7.5F));
        double tan = Math.tan(halfAngle);
        double baseRadius = 0.55 + power * 0.45;
        double endRadius = baseRadius + range * tan;

        // ---- blocks
        List<Candidate> candidates = new ArrayList<>();
        if (player.mayBuild()) {
            Vec3 end = eye.add(dir.scale(range));
            AABB box = new AABB(eye, end).inflate(endRadius + 1.0);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                    for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                        double vx = x + 0.5 - eye.x;
                        double vy = y + 0.5 - eye.y;
                        double vz = z + 0.5 - eye.z;
                        double depth = vx * dir.x + vy * dir.y + vz * dir.z;
                        if (depth < 0.8 || depth > range) {
                            continue;
                        }
                        double distSq = vx * vx + vy * vy + vz * vz;
                        if (distSq > range * range) {
                            continue;
                        }
                        double coneR = baseRadius + depth * tan;
                        if (distSq - depth * depth > coneR * coneR) {
                            continue;
                        }
                        cursor.set(x, y, z);
                        if (level.isOutsideBuildHeight(cursor)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir() || state.is(ModTags.SINGULARITY_IMMUNE) || state.getDestroySpeed(level, cursor) < 0.0F) {
                            continue;
                        }
                        candidates.add(new Candidate(cursor.immutable(), state, depth));
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(Candidate::depth));
        }

        List<BeamFxPacket.Absorbed> absorbed = new ArrayList<>();
        int blocks = 0;
        for (Candidate c : candidates) {
            if (blocks >= capacity || blocks >= perShot) {
                break;
            }
            if (!level.mayInteract(player, c.pos())) {
                continue;
            }
            BlockState current = level.getBlockState(c.pos());
            if (current != c.state()) {
                continue;
            }
            if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, c.pos(), current, player))) {
                continue;
            }
            level.setBlock(c.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            blocks++;
            if (absorbed.size() < BeamFxPacket.MAX_ENTRIES) {
                absorbed.add(new BeamFxPacket.Absorbed(c.pos(), Block.getId(current)));
            }
        }

        // ---- entities
        int entities = 0;
        AABB entityBox = new AABB(eye, eye.add(dir.scale(range))).inflate(endRadius + 2.0);
        List<Entity> targets = level.getEntities(player, entityBox, e -> isSwallowable(player, e));
        for (Entity e : targets) {
            Vec3 center = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            Vec3 v = center.subtract(eye);
            double depth = v.dot(dir);
            if (depth < 0.5 || depth > range || v.lengthSqr() > range * range) {
                continue;
            }
            double coneR = baseRadius + depth * tan + e.getBbWidth() * 0.5 + 0.4;
            if (v.lengthSqr() - depth * depth > coneR * coneR) {
                continue;
            }
            if (e instanceof LivingEntity living) {
                living.discard();
            } else {
                e.discard();
            }
            entities++;
            if (absorbed.size() < BeamFxPacket.MAX_ENTRIES) {
                absorbed.add(new BeamFxPacket.Absorbed(BlockPos.containing(center), -1));
            }
        }

        // ---- payout & bookkeeping
        int matter = blocks + entities;
        while (matter > 0) {
            int n = Math.min(64, matter);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.COMPRESSED_MATTER.get(), n));
            matter -= n;
        }
        int total = consumed + blocks;
        PocketBlackHoleItem.setConsumed(stack, total);

        Vec3 tip = eye.add(dir.scale(range));
        ModNetwork.sendToTrackingAndSelf(player, new BeamFxPacket(player.getId(), eye, tip, (float) endRadius, power, absorbed));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.POCKET_FIRE.get(), SoundSource.PLAYERS,
                1.1F, 0.85F + power * 0.25F + PocketBlackHoleItem.instability(stack) * 0.2F);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        player.getCooldowns().addCooldown(stack.getItem(), Config.POCKET_COOLDOWN.get());

        if (total >= limit) {
            SingularityManager.triggerEscape(player, stack, hand);
            return;
        }
        warnIfUnstable(level, player, stack, total, limit);
    }

    private static boolean isSwallowable(Player shooter, Entity e) {
        if (!e.isAlive() || e instanceof Player || e.isSpectator()) {
            return false;
        }
        if (e instanceof BlackHoleEntity || e instanceof GravitationalDebrisEntity || e instanceof AntiGravityBombEntity) {
            return false;
        }
        if (e.getType().is(Tags.EntityTypes.BOSSES) || e.getType().is(ModTags.SINGULARITY_IMMUNE_ENTITIES)) {
            return false;
        }
        // Never eat the shooter's own pets or anything they are riding.
        if (e instanceof OwnableEntity ownable && shooter.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        return !shooter.isPassengerOfSameVehicle(e) && !e.hasPassenger(shooter);
    }

    private static void warnIfUnstable(ServerLevel level, ServerPlayer player, ItemStack stack, int total, int limit) {
        float f = total / (float) limit;
        int stage = f >= 0.9F ? 3 : f >= 0.75F ? 2 : f >= 0.5F ? 1 : 0;
        int warned = stack.getOrCreateTag().getInt(PocketBlackHoleItem.TAG_WARNED);
        if (stage <= warned) {
            return;
        }
        stack.getOrCreateTag().putInt(PocketBlackHoleItem.TAG_WARNED, stage);
        ChatFormatting color = stage == 3 ? ChatFormatting.RED : stage == 2 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
        int integrity = Math.round((1.0F - f) * 100.0F);
        player.displayClientMessage(Component.translatable("message.livingblackhole.integrity_warning." + stage, integrity)
                .withStyle(color, ChatFormatting.BOLD), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WARNING_BEEP.get(), SoundSource.PLAYERS,
                0.8F + stage * 0.2F, 0.8F + stage * 0.15F);
    }
}
