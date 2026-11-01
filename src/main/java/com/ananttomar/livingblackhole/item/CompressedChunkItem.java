package com.ananttomar.livingblackhole.item;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.client.render.CompressedChunkItemRenderer;
import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.singularity.ChunkCapture;
import com.ananttomar.livingblackhole.singularity.PendingPlacements;
import com.ananttomar.livingblackhole.singularity.StructureData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A 16x16x16 piece of the world folded into a fist-sized lattice, shown as a live 3D miniature of
 * the blocks inside it. Right-click a block to unfold it on top: the structure grows back to full
 * size with its lowest layer resting on the clicked block, block entities and their contents
 * included. The chunk is used up.
 */
public class CompressedChunkItem extends Item {
    public CompressedChunkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return ChunkCapture.hasStructure(stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return place(serverLevel, serverPlayer, stack, context.getClickedPos()) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * Unfolds the chunk on top of {@code clicked}: its lowest layer rests on the clicked block. The
     * structure leaves the hand as a miniature and grows back to full size; the blocks are set down
     * when it lands ({@link PendingPlacements#GROW_TICKS} later). Public so commands and tests can drive it.
     */
    public static boolean place(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos clicked) {
        CompoundTag data = ChunkCapture.data(stack);
        if (data == null) {
            player.displayClientMessage(Component.translatable("message.livingblackhole.chunk_empty").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        ChunkCapture.Plan plan = ChunkCapture.plan(level, player, data, clicked);
        if (plan.count() == 0) {
            player.displayClientMessage(Component.translatable("message.livingblackhole.chunk_blocked").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        CompoundTag payload = data.copy();
        ModNetwork.sendToTrackingAndSelf(player, new ChunkFxPacket(ChunkFxPacket.PLACE, player.getId(),
                PocketBlackHoleItem.handPosition(player), plan.min(), PendingPlacements.GROW_TICKS, StructureData.fromTag(payload)));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CHUNK_PLACE.get(), SoundSource.PLAYERS, 0.8F, 1.6F);
        PendingPlacements.schedule(level, player, payload, plan.min(), PendingPlacements.GROW_TICKS);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        player.getCooldowns().addCooldown(stack.getItem(), Config.POCKET_COOLDOWN.get());
        player.displayClientMessage(Component.translatable("message.livingblackhole.chunk_unfolding", plan.count())
                .withStyle(ChatFormatting.AQUA), true);
        stack.shrink(1);
        return true;
    }

    /** The live 3D miniature of the stored structure (client only). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CompressedChunkItemRenderer.get();
            }
        });
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(Style.EMPTY.withColor(0x9C7BFF).withBold(true));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        // Somebody's house might be in here - never let it despawn.
        return Integer.MAX_VALUE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.livingblackhole.compressed_chunk.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        CompoundTag data = ChunkCapture.data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.livingblackhole.compressed_chunk.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.compressed_chunk.blocks", data.getInt("Count")).withStyle(ChatFormatting.GRAY));
        ListTag summary = data.getList("Summary", Tag.TAG_COMPOUND);
        for (int i = 0; i < summary.size(); i++) {
            CompoundTag entry = summary.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Block"));
            Block block = id != null ? BuiltInRegistries.BLOCK.get(id) : null;
            if (block != null) {
                tooltip.add(Component.literal("  " + entry.getInt("Count") + "× ").withStyle(ChatFormatting.DARK_AQUA)
                        .append(Component.translatable(block.getDescriptionId()).withStyle(ChatFormatting.AQUA)));
            }
        }
        int[] origin = data.getIntArray("Origin");
        if (origin.length == 3) {
            tooltip.add(Component.translatable("tooltip.livingblackhole.compressed_chunk.origin", origin[0], origin[1], origin[2])
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.livingblackhole.compressed_chunk.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
