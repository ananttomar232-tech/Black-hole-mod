package com.ananttomar.livingblackhole.singularity;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.network.CinematicPacket;
import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.network.SyncSingularityPacket;
import com.ananttomar.livingblackhole.registry.ModBlocks;
import com.ananttomar.livingblackhole.registry.ModEntities;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-side orchestration of the escape event, the fragment hunt and the neutralisation. */
public final class SingularityManager {
    private static final BlockState[] CRASH_DEBRIS = {
            Blocks.OBSIDIAN.defaultBlockState(),
            Blocks.CRYING_OBSIDIAN.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(),
            Blocks.BASALT.defaultBlockState(),
            Blocks.MAGMA_BLOCK.defaultBlockState(),
            Blocks.TUFF.defaultBlockState()
    };

    private SingularityManager() {
    }

    // ------------------------------------------------------------------ escape

    /** The Pocket Black Hole hit its limit in the player's hands. */
    public static void triggerEscape(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        ServerLevel level = player.serverLevel();

        // The device is gone - instantly and permanently.
        stack.shrink(stack.getCount());
        if (player.getItemInHand(hand).isEmpty() || player.getItemInHand(hand) == stack) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        Vec3 center = player.position().add(0.0, 1.0, 0.0);
        SingularityEvent ev = startEvent(level, center, player);

        // The breach throws the holder clear of the forming horizon.
        Vec3 look = player.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        if (back.lengthSqr() < 1.0E-4) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            back = new Vec3(Math.cos(a), 0.0, Math.sin(a));
        }
        back = back.normalize();
        player.setDeltaMovement(back.x * 2.1, 0.95, back.z * 2.1);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 2, false, true, true));
        award(player, "containment_breach");
        LivingBlackHole.LOGGER.info("{} breached a Pocket Black Hole at {} (event {}).", player.getGameProfile().getName(), BlockPos.containing(center), ev.id);
    }

    public static SingularityEvent startEvent(ServerLevel level, Vec3 center, @Nullable ServerPlayer owner) {
        BlackHoleEntity bh = ModEntities.BLACK_HOLE.get().create(level);
        if (bh == null) {
            throw new IllegalStateException("Black hole entity type failed to construct");
        }
        bh.moveTo(center.x, center.y, center.z, 0.0F, 0.0F);

        SingularityEvent ev = new SingularityEvent(UUID.randomUUID(), bh.getUUID(), level.dimension(), center,
                owner != null ? owner.getUUID() : null, level.getGameTime());
        bh.setEventId(ev.id);
        level.addFreshEntity(bh);

        BlockPos[] fragments = placeFragments(level, center, level.random);
        System.arraycopy(fragments, 0, ev.fragments, 0, SingularityEvent.FRAGMENTS);
        ev.reach = Config.SUCTION_RADIUS.get();

        MinecraftServer server = level.getServer();
        SingularitySavedData.get(server).add(ev);
        syncAll(server);

        ModNetwork.sendToLevel(level, new CinematicPacket(CinematicPacket.ESCAPE, center, 0.0F));
        level.playSound(null, center.x, center.y, center.z, ModSounds.CONTAINMENT_BREACH.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        level.playSound(null, center.x, center.y, center.z, ModSounds.BLACK_HOLE_BIRTH.get(), SoundSource.HOSTILE, 10.0F, 1.0F);
        for (ServerPlayer p : level.players()) {
            p.playNotifySound(ModSounds.ALARM.get(), SoundSource.MASTER, 0.9F, 1.0F);
        }

        MutableComponent coords = Component.empty();
        for (int i = 0; i < SingularityEvent.FRAGMENTS; i++) {
            if (i > 0) {
                coords.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            coords.append(formatPos(ev.fragments[i]).withStyle(ChatFormatting.AQUA));
        }
        Component headline = Component.translatable("message.livingblackhole.breach", formatPos(BlockPos.containing(center)))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component fragmentsLine = Component.translatable("message.livingblackhole.fragments", coords).withStyle(ChatFormatting.RED);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(headline);
            p.sendSystemMessage(fragmentsLine);
        }
        return ev;
    }

    public static MutableComponent formatPos(BlockPos pos) {
        return Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
    }

    // ------------------------------------------------------------------ fragments

    private static BlockPos[] placeFragments(ServerLevel level, Vec3 center, RandomSource random) {
        int maxR = Config.FRAGMENT_RADIUS.get();
        int minR = Math.min(Config.FRAGMENT_MIN_DISTANCE.get(), Math.max(8, maxR - 8));
        int refY = Mth.floor(center.y);
        BlockPos[] out = new BlockPos[SingularityEvent.FRAGMENTS];
        double baseAngle = random.nextDouble() * Math.PI * 2.0;

        for (int i = 0; i < SingularityEvent.FRAGMENTS; i++) {
            BlockPos chosen = null;
            for (int attempt = 0; attempt < 32 && chosen == null; attempt++) {
                // Spread the three fragments around the compass, then jitter.
                double angle = baseAngle + i * (Math.PI * 2.0 / 3.0) + (random.nextDouble() - 0.5) * 1.4;
                double r = Math.sqrt(minR * (double) minR + random.nextDouble() * (maxR * (double) maxR - minR * (double) minR));
                int x = Mth.floor(center.x + Math.cos(angle) * r);
                int z = Mth.floor(center.z + Math.sin(angle) * r);
                if (!level.getWorldBorder().isWithinBounds(x, z)) {
                    continue;
                }
                BlockPos surface = findSurface(level, x, z, refY);
                if (surface == null) {
                    continue;
                }
                boolean tooClose = false;
                for (int j = 0; j < i; j++) {
                    if (out[j].distSqr(surface) < 48 * 48) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) {
                    chosen = surface;
                }
            }
            if (chosen == null) {
                // Void worlds and tiny borders: build a landing pad at minimum distance.
                double angle = baseAngle + i * (Math.PI * 2.0 / 3.0);
                int x = Mth.floor(center.x + Math.cos(angle) * minR);
                int z = Mth.floor(center.z + Math.sin(angle) * minR);
                int y = Mth.clamp(refY, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 3);
                chosen = new BlockPos(x, y, z);
                buildPlatform(level, chosen.below());
            }
            buildCrashSite(level, chosen, random);
            level.setBlock(chosen, ModBlocks.CORE_FRAGMENT.get().defaultBlockState(), Block.UPDATE_ALL);
            out[i] = chosen;
        }
        return out;
    }

    @Nullable
    private static BlockPos findSurface(ServerLevel level, int x, int z, int refY) {
        if (!level.dimensionType().hasCeiling()) {
            // Forces generation of the target chunk if needed - the fragments must exist now.
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) {
                return null;
            }
            return new BlockPos(x, y, z);
        }
        int top = Math.min(refY + 48, level.getMaxBuildHeight() - 3);
        int bottom = Math.max(refY - 64, level.getMinBuildHeight() + 2);
        for (int y = top; y > bottom; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockPos below = p.below();
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(below).isSolidRender(level, below)) {
                return p;
            }
        }
        return null;
    }

    private static void buildPlatform(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                if (canReshape(level, p, level.getBlockState(p))) {
                    level.setBlock(p, (dx == 0 && dz == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** A small scorched impact site so the fragment reads as something that fell from the sky. */
    private static void buildCrashSite(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (!under.isSolidRender(level, below) || !under.getFluidState().isEmpty()) {
            buildPlatform(level, below);
        } else if (canReshape(level, below, under)) {
            level.setBlock(below, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }
        boolean flat = !level.dimensionType().hasCeiling();
        for (int i = 0; i < 14; i++) {
            int dx = random.nextInt(7) - 3;
            int dz = random.nextInt(7) - 3;
            if (dx == 0 && dz == 0) {
                continue;
            }
            BlockPos column = pos.offset(dx, 0, dz);
            BlockPos ground = flat
                    ? new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1, column.getZ())
                    : column.below();
            if (Math.abs(ground.getY() - below.getY()) > 3) {
                continue;
            }
            BlockState existing = level.getBlockState(ground);
            if (!existing.isSolidRender(level, ground) || !canReshape(level, ground, existing)) {
                continue;
            }
            level.setBlock(ground, CRASH_DEBRIS[random.nextInt(CRASH_DEBRIS.length)], Block.UPDATE_ALL);
        }
        // Clear grass, snow or flowers occupying the fragment's own space and the space above it.
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos p = pos.above(dy);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && canReshape(level, p, s)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean canReshape(ServerLevel level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) == null && !state.is(ModTags.SINGULARITY_IMMUNE) && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public static void onFragmentBroken(ServerLevel level, BlockPos pos, @Nullable Player breaker) {
        MinecraftServer server = level.getServer();
        SingularitySavedData data = SingularitySavedData.get(server);
        for (SingularityEvent ev : data.all()) {
            if (!ev.dimension.equals(level.dimension())) {
                continue;
            }
            int index = ev.fragmentIndex(pos);
            if (index < 0 || ev.collected[index]) {
                continue;
            }
            ev.collected[index] = true;
            data.setDirty();

            int got = ev.collectedCount();
            Component who = breaker != null ? breaker.getDisplayName() : Component.literal("?");
            Component msg = Component.translatable("message.livingblackhole.fragment_secured", who, got, SingularityEvent.FRAGMENTS)
                    .withStyle(ChatFormatting.AQUA);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
                if (got >= SingularityEvent.FRAGMENTS) {
                    p.sendSystemMessage(Component.translatable("message.livingblackhole.all_fragments").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }
            }
            level.playSound(null, pos, ModSounds.FRAGMENT_COLLECT.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
            ModNetwork.sendToLevel(level, new CinematicPacket(CinematicPacket.FRAGMENT_SECURED, Vec3.atCenterOf(pos), got));
            syncAll(server);
            return;
        }
    }

    // ------------------------------------------------------------------ black hole callbacks

    public static void onMassChanged(BlackHoleEntity bh) {
        if (bh.getEventId() == null || bh.getServer() == null) {
            return;
        }
        SingularitySavedData data = SingularitySavedData.get(bh.getServer());
        SingularityEvent ev = data.get(bh.getEventId());
        if (ev != null && ev.mass != bh.getMass()) {
            ev.mass = bh.getMass();
            ev.reach = bh.getReach();
            data.setDirty();
        }
    }

    public static void onCollapseStarted(BlackHoleEntity bh) {
        if (bh.getServer() == null) {
            return;
        }
        SingularityEvent ev = findFor(bh);
        if (ev != null) {
            ev.phase = SingularityEvent.PHASE_COLLAPSING;
            SingularitySavedData.get(bh.getServer()).setDirty();
            syncAll(bh.getServer());
        }
        Component msg = Component.translatable("message.livingblackhole.collapse_started").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        for (ServerPlayer p : bh.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    public static void onNeutralized(BlackHoleEntity bh, @Nullable Entity cause) {
        MinecraftServer server = bh.getServer();
        if (server == null) {
            return;
        }
        SingularityEvent ev = findFor(bh);
        SingularitySavedData data = SingularitySavedData.get(server);
        if (ev != null) {
            data.remove(ev.id);
        }
        syncAll(server);

        ServerLevel level = (ServerLevel) bh.level();
        ModNetwork.sendToAll(new CinematicPacket(CinematicPacket.NEUTRALIZED, bh.position(), 0.0F));

        List<ServerPlayer> heroes = new ArrayList<>();
        if (cause instanceof ServerPlayer sp) {
            heroes.add(sp);
        }
        for (ServerPlayer p : level.players()) {
            if (!heroes.contains(p) && (p.distanceToSqr(bh) < 200.0 * 200.0 || (ev != null && p.getUUID().equals(ev.owner)))) {
                heroes.add(p);
            }
        }
        for (ServerPlayer p : heroes) {
            award(p, "cosmic_savior");
        }
        Component name = cause != null ? cause.getDisplayName() : Component.translatable("message.livingblackhole.unknown_hero");
        Component msg = Component.translatable("message.livingblackhole.neutralized", name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    public static void onBlackHoleRemoved(BlackHoleEntity bh, boolean clean) {
        MinecraftServer server = bh.getServer();
        if (server == null) {
            return;
        }
        SingularityEvent ev = findFor(bh);
        if (ev == null) {
            return;
        }
        SingularitySavedData.get(server).remove(ev.id);
        syncAll(server);
        if (!clean) {
            ModNetwork.sendToAll(new CinematicPacket(CinematicPacket.VANISHED, bh.position(), 0.0F));
            Component msg = Component.translatable("message.livingblackhole.vanished").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
            }
        }
    }

    @Nullable
    private static SingularityEvent findFor(BlackHoleEntity bh) {
        MinecraftServer server = bh.getServer();
        if (server == null) {
            return null;
        }
        SingularitySavedData data = SingularitySavedData.get(server);
        SingularityEvent ev = bh.getEventId() != null ? data.get(bh.getEventId()) : null;
        return ev != null ? ev : data.byBlackHole(bh.getUUID());
    }

    // ------------------------------------------------------------------ sync & upkeep

    public static void syncAll(MinecraftServer server) {
        ModNetwork.sendToAll(new SyncSingularityPacket(SingularitySavedData.get(server).snapshot()));
    }

    public static void syncTo(ServerPlayer player) {
        ModNetwork.sendTo(player, new SyncSingularityPacket(SingularitySavedData.get(player.server).snapshot()));
    }

    public static void serverTick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % 100 != 0) {
            return;
        }
        SingularitySavedData data = SingularitySavedData.get(server);
        if (data.isEmpty()) {
            return;
        }
        // Drop events whose black hole is provably gone (loaded chunk, entities loaded, no entity).
        boolean changed = false;
        for (SingularityEvent ev : data.snapshot()) {
            ServerLevel level = server.getLevel(ev.dimension);
            if (level == null) {
                continue;
            }
            Entity e = level.getEntity(ev.blackHoleId);
            if (e instanceof BlackHoleEntity bh) {
                ev.missingChecks = 0;
                ev.mass = bh.getMass();
                ev.reach = bh.getReach();
                continue;
            }
            BlockPos at = BlockPos.containing(ev.center);
            if (level.isLoaded(at) && level.areEntitiesLoaded(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4))) {
                if (++ev.missingChecks >= 3) {
                    data.remove(ev.id);
                    changed = true;
                    LivingBlackHole.LOGGER.warn("Removed orphaned singularity event {} at {}", ev.id, at);
                }
            } else {
                ev.missingChecks = 0;
            }
        }
        if (changed || tick % 200 == 0) {
            syncAll(server);
        }
    }

    public static int clearAll(MinecraftServer server) {
        SingularitySavedData data = SingularitySavedData.get(server);
        int removed = 0;
        for (SingularityEvent ev : data.snapshot()) {
            ServerLevel level = server.getLevel(ev.dimension);
            data.remove(ev.id);
            if (level != null && level.getEntity(ev.blackHoleId) instanceof BlackHoleEntity bh) {
                bh.discard();
            }
            removed++;
        }
        for (ServerLevel level : server.getAllLevels()) {
            List<BlackHoleEntity> found = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof BlackHoleEntity bh && !bh.isRemoved()) {
                    found.add(bh);
                }
            }
            for (BlackHoleEntity bh : found) {
                bh.discard();
                removed++;
            }
        }
        syncAll(server);
        return removed;
    }

    public static void award(ServerPlayer player, String name) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(LivingBlackHole.id(name));
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
