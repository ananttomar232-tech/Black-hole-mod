package com.ananttomar.livingblackhole.item;

import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Three Core Fragments wrapped around a Compressed Chunk, phase-inverted. Thrown, it
 * coasts on near-zero gravity; inside a singularity's gravity well it is captured and dragged to
 * the event horizon, where it collapses the black hole.
 */
public class AntiGravityBombItem extends Item {
    public AntiGravityBombItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BOMB_THROW.get(),
                SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        if (!level.isClientSide) {
            AntiGravityBombEntity bomb = new AntiGravityBombEntity(level, player);
            ItemStack thrown = stack.copy();
            thrown.setCount(1);
            bomb.setItem(thrown);
            bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.35F, 0.6F);
            level.addFreshEntity(bomb);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 30);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide || !(entity instanceof LivingEntity living)) {
            return;
        }
        boolean offhand = living.getOffhandItem() == stack;
        if (!selected && !offhand) {
            return;
        }
        // Held trail: sparks shed from the bomb fall *upward* - the field inverts local gravity.
        Vec3 look = living.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        double side = offhand ? -0.38 : 0.38;
        Vec3 hand = living.getEyePosition().add(look.scale(0.55)).add(right.scale(side)).add(0.0, -0.42, 0.0);
        for (int i = 0; i < 2; i++) {
            level.addParticle(ModParticles.ANTIGRAV_SPARK.get(),
                    hand.x + (level.random.nextDouble() - 0.5) * 0.18,
                    hand.y + (level.random.nextDouble() - 0.5) * 0.18,
                    hand.z + (level.random.nextDouble() - 0.5) * 0.18,
                    (level.random.nextDouble() - 0.5) * 0.01, 0.015 + level.random.nextDouble() * 0.02, (level.random.nextDouble() - 0.5) * 0.01);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(Style.EMPTY.withColor(0x4DF0FF).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.livingblackhole.anti_gravity_bomb.lore").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.anti_gravity_bomb.usage").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.livingblackhole.anti_gravity_bomb.inert").withStyle(ChatFormatting.DARK_GRAY));
    }
}
