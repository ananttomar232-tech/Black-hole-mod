package com.ananttomar.livingblackhole.item;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.singularity.ChunkCapture;
import com.ananttomar.livingblackhole.singularity.SingularityManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A singularity held inside a magnetic containment lattice. Right-click a block and it swallows
 * the whole 16x16x16 region around it - a house, a hill, a lake - leaving a hole in the world and
 * a Compressed Chunk in your inventory. Every chunk strains the lattice; at the limit it breaks.
 */
public class PocketBlackHoleItem extends Item {
    public static final String TAG_CONSUMED = "ChunksConsumed";
    public static final String TAG_WARNED = "WarnStage";

    public PocketBlackHoleItem(Properties properties) {
        super(properties);
    }

    public static int limit() {
        try {
            return Config.CHUNK_LIMIT.get();
        } catch (IllegalStateException notLoaded) {
            return 10;
        }
    }

    public static int getConsumed(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(TAG_CONSUMED) : 0;
    }

    public static void setConsumed(ItemStack stack, int consumed) {
        stack.getOrCreateTag().putInt(TAG_CONSUMED, Math.max(0, consumed));
    }

    /** 0 = pristine containment, 1 = breaching. */
    public static float instability(ItemStack stack) {
        return Mth.clamp(getConsumed(stack) / (float) limit(), 0.0F, 1.0F);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        consume(serverLevel, serverPlayer, context.getItemInHand(), context.getHand(), context.getClickedPos());
        return InteractionResult.CONSUME;
    }

    /** Swallows the chunk around {@code target}. Public so commands and tests can drive it. */
    public static boolean consume(ServerLevel level, ServerPlayer player, ItemStack stack, InteractionHand hand, net.minecraft.core.BlockPos target) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        ChunkCapture.Captured captured = ChunkCapture.capture(level, player, target);
        if (captured == null) {
            player.displayClientMessage(Component.translatable("message.livingblackhole.nothing_to_consume").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int count = ChunkCapture.data(captured.chunk()).getInt("Count");
        ItemHandlerHelper.giveItemToPlayer(player, captured.chunk());

        Vec3 center = Vec3.atLowerCornerOf(captured.min()).add(8.0, 8.0, 8.0);
        Vec3 hand3 = handPosition(player);
        ModNetwork.sendToTrackingAndSelf(player, new ChunkFxPacket(ChunkFxPacket.CAPTURE, player.getId(), hand3, captured.min(), captured.fx()));
        level.playSound(null, center.x, center.y, center.z, ModSounds.VACUUM.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.VACUUM.get(), SoundSource.PLAYERS, 1.0F,
                1.05F + instability(stack) * 0.25F);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        player.getCooldowns().addCooldown(stack.getItem(), Config.POCKET_COOLDOWN.get());

        int total = getConsumed(stack) + 1;
        setConsumed(stack, total);
        player.displayClientMessage(Component.translatable("message.livingblackhole.chunk_consumed", count, total, limit())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (total >= limit()) {
            SingularityManager.triggerEscape(player, stack, hand);
        } else {
            warnIfUnstable(level, player, stack, total);
        }
        return true;
    }

    private static void warnIfUnstable(ServerLevel level, ServerPlayer player, ItemStack stack, int total) {
        float f = total / (float) limit();
        int stage = f >= 0.9F ? 3 : f >= 0.75F ? 2 : f >= 0.5F ? 1 : 0;
        int warned = stack.getOrCreateTag().getInt(TAG_WARNED);
        if (stage <= warned) {
            return;
        }
        stack.getOrCreateTag().putInt(TAG_WARNED, stage);
        ChatFormatting color = stage == 3 ? ChatFormatting.RED : stage == 2 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
        int integrity = Math.round((1.0F - f) * 100.0F);
        player.sendSystemMessage(Component.translatable("message.livingblackhole.integrity_warning." + stage, integrity)
                .withStyle(color, ChatFormatting.BOLD));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WARNING_BEEP.get(), SoundSource.PLAYERS,
                0.8F + stage * 0.2F, 0.8F + stage * 0.15F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.livingblackhole.aim_at_block").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide || !selected || !(entity instanceof LivingEntity living)) {
            return;
        }
        // The more it has eaten, the more the lattice leaks.
        float f = instability(stack);
        if (level.random.nextFloat() < 0.1F + f * 0.6F) {
            Vec3 hand = handPosition(living);
            double r = 0.25 + f * 0.25;
            level.addParticle(ModParticles.HORIZON_SPARK.get(),
                    hand.x + (level.random.nextDouble() - 0.5) * r, hand.y + (level.random.nextDouble() - 0.5) * r, hand.z + (level.random.nextDouble() - 0.5) * r,
                    (level.random.nextDouble() - 0.5) * 0.04, (level.random.nextDouble() - 0.5) * 0.04, (level.random.nextDouble() - 0.5) * 0.04);
        }
    }

    public static Vec3 handPosition(LivingEntity entity) {
        Vec3 look = entity.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        if (right.lengthSqr() < 1.0E-4) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        return entity.getEyePosition().add(look.scale(0.75)).add(right.scale(0.32)).add(0.0, -0.32, 0.0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getConsumed(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * (1.0F - instability(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Containment integrity: cyan -> violet -> red as the singularity gorges itself.
        float f = instability(stack);
        return Mth.hsvToRgb(Mth.lerp(f, 0.52F, 0.98F), 0.85F, 1.0F);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(Style.EMPTY.withColor(0xB36BFF).withBold(true));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int consumed = getConsumed(stack);
        float integrity = 1.0F - instability(stack);
        ChatFormatting integrityColor = integrity > 0.5F ? ChatFormatting.AQUA : integrity > 0.2F ? ChatFormatting.GOLD : ChatFormatting.RED;

        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.chunks", consumed, limit()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.integrity", Math.round(integrity * 100.0F)).withStyle(integrityColor));
        if (integrity <= 0.2F) {
            tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.critical").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
