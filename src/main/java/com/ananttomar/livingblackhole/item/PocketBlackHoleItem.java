package com.ananttomar.livingblackhole.item;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.singularity.PocketBeamLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A singularity held inside a magnetic containment lattice. Hold use to charge, release to fire
 * a consumption beam that swallows every block and non-player entity in its 15 block reach and
 * turns it into Compressed Matter. Every block weakens the lattice; at the limit it breaks.
 */
public class PocketBlackHoleItem extends Item {
    public static final String TAG_CONSUMED = "ConsumedMass";
    public static final String TAG_WARNED = "WarnStage";
    public static final int FULL_CHARGE_TICKS = 30;

    public PocketBlackHoleItem(Properties properties) {
        super(properties);
    }

    public static int limit() {
        try {
            return Config.BLOCK_LIMIT.get();
        } catch (IllegalStateException notLoaded) {
            return 500;
        }
    }

    public static int getConsumed(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(TAG_CONSUMED) : 0;
    }

    public static void setConsumed(ItemStack stack, int consumed) {
        stack.getOrCreateTag().putInt(TAG_CONSUMED, Math.max(0, consumed));
    }

    /** 0 = pristine containment, 1 = about to breach. */
    public static float instability(ItemStack stack) {
        return Mth.clamp(getConsumed(stack) / (float) limit(), 0.0F, 1.0F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.POCKET_CHARGE.get(),
                    SoundSource.PLAYERS, 0.9F, 0.85F + instability(stack) * 0.4F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide) {
            return;
        }
        int used = getUseDuration(stack) - remainingUseDuration;
        float charge = Mth.clamp(used / (float) FULL_CHARGE_TICKS, 0.0F, 1.0F);
        Vec3 hand = handPosition(entity);
        int count = 1 + (int) (charge * 4);
        for (int i = 0; i < count; i++) {
            // Spawn matter on a shrinking shell around the device; velocity aims back at the core,
            // so the cloud visibly converges as the charge builds.
            double radius = 1.6 - charge * 0.8;
            double theta = level.random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(level.random.nextDouble() * 2.0 - 1.0);
            double ox = Math.sin(phi) * Math.cos(theta) * radius;
            double oy = Math.cos(phi) * radius;
            double oz = Math.sin(phi) * Math.sin(theta) * radius;
            double speed = 0.08 + charge * 0.1;
            level.addParticle(ModParticles.HORIZON_SPARK.get(), hand.x + ox, hand.y + oy, hand.z + oz,
                    -ox * speed, -oy * speed, -oz * speed);
        }
        if (used == FULL_CHARGE_TICKS) {
            for (int i = 0; i < 24; i++) {
                double a = i / 24.0 * Math.PI * 2.0;
                level.addParticle(ModParticles.ACCRETION.get(), hand.x, hand.y, hand.z,
                        Math.cos(a) * 0.18, (level.random.nextDouble() - 0.5) * 0.04, Math.sin(a) * 0.18);
            }
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
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int used = getUseDuration(stack) - timeLeft;
        float power = Mth.clamp(used / (float) FULL_CHARGE_TICKS, 0.15F, 1.0F);
        PocketBeamLogic.fire(serverLevel, player, stack, player.getUsedItemHand(), power);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
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
        int limit = limit();
        float integrity = 1.0F - instability(stack);
        ChatFormatting integrityColor = integrity > 0.5F ? ChatFormatting.AQUA : integrity > 0.2F ? ChatFormatting.GOLD : ChatFormatting.RED;

        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.mass", consumed, limit).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.integrity", Math.round(integrity * 100.0F)).withStyle(integrityColor));
        if (integrity <= 0.2F) {
            tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.critical").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.pocket_black_hole.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
