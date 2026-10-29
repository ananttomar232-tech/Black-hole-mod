package com.ananttomar.livingblackhole.singularity;

import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /blackhole} - operator tools for servers, map makers and testing.
 * <ul>
 *     <li>{@code list} - every active singularity and its fragment status</li>
 *     <li>{@code spawn [pos]} - start a full escape event (fragments included)</li>
 *     <li>{@code neutralize} - collapse the nearest black hole with the full cinematic</li>
 *     <li>{@code clear} - instantly remove all black holes and clear the HUD</li>
 *     <li>{@code charge <blocks>} - set the mass stored in the held Pocket Black Hole</li>
 * </ul>
 */
public final class BlackHoleCommand {
    private BlackHoleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blackhole")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(BlackHoleCommand::list))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx, ctx.getSource().getPosition()))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> spawn(ctx, Vec3Argument.getVec3(ctx, "pos")))))
                .then(Commands.literal("neutralize").executes(BlackHoleCommand::neutralize))
                .then(Commands.literal("clear").executes(BlackHoleCommand::clear))
                .then(Commands.literal("charge")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(ctx -> charge(ctx, IntegerArgumentType.getInteger(ctx, "blocks"))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<SingularityEvent> events = SingularitySavedData.get(source.getServer()).snapshot();
        if (events.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.livingblackhole.none").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (SingularityEvent ev : events) {
            MutableComponent line = Component.literal("◉ ").withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.literal(ev.dimension.location() + " ").withStyle(ChatFormatting.GRAY))
                    .append(SingularityManager.formatPos(net.minecraft.core.BlockPos.containing(ev.center)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("  mass " + ev.mass + "  fragments " + ev.collectedCount() + "/" + SingularityEvent.FRAGMENTS).withStyle(ChatFormatting.WHITE));
            source.sendSuccess(() -> line, false);
            for (int i = 0; i < SingularityEvent.FRAGMENTS; i++) {
                MutableComponent f = Component.literal("   • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(SingularityManager.formatPos(ev.fragments[i]).withStyle(ev.collected[i] ? ChatFormatting.GREEN : ChatFormatting.AQUA))
                        .append(Component.literal(ev.collected[i] ? "  secured" : "").withStyle(ChatFormatting.GREEN));
                source.sendSuccess(() -> f, false);
            }
        }
        return events.size();
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer owner = source.getEntity() instanceof ServerPlayer p ? p : null;
        SingularityManager.startEvent(level, pos, owner);
        source.sendSuccess(() -> Component.translatable("command.livingblackhole.spawned").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int neutralize(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        BlackHoleEntity nearest = level.getEntitiesOfClass(BlackHoleEntity.class, new AABB(at, at).inflate(4096.0), BlackHoleEntity::isAlive)
                .stream().min(Comparator.comparingDouble(bh -> bh.position().distanceToSqr(at))).orElse(null);
        if (nearest == null || !nearest.beginCollapse(source.getEntity())) {
            source.sendFailure(Component.translatable("command.livingblackhole.no_target"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.livingblackhole.neutralizing").withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int n = SingularityManager.clearAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.livingblackhole.cleared", n).withStyle(ChatFormatting.GRAY), true);
        return n;
    }

    private static int charge(CommandContext<CommandSourceStack> ctx, int blocks) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.translatable("command.livingblackhole.not_player"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof PocketBlackHoleItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof PocketBlackHoleItem)) {
            source.sendFailure(Component.translatable("command.livingblackhole.not_holding"));
            return 0;
        }
        int clamped = Math.min(blocks, Math.max(0, PocketBlackHoleItem.limit() - 1));
        PocketBlackHoleItem.setConsumed(stack, clamped);
        stack.getOrCreateTag().remove(PocketBlackHoleItem.TAG_WARNED);
        source.sendSuccess(() -> Component.translatable("command.livingblackhole.charged", clamped, PocketBlackHoleItem.limit()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }
}
