package com.ananttomar.livingblackhole.selftest;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.entity.GravitationalDebrisEntity;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.ananttomar.livingblackhole.registry.ModBlocks;
import com.ananttomar.livingblackhole.registry.ModEntities;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.item.CompressedChunkItem;
import com.ananttomar.livingblackhole.singularity.ChunkCapture;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import com.ananttomar.livingblackhole.singularity.SingularityEvent;
import com.ananttomar.livingblackhole.singularity.SingularityManager;
import com.ananttomar.livingblackhole.singularity.SingularitySavedData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Headless end-to-end test of the whole singularity event on a dedicated server, enabled only
 * with {@code -Dlivingblackhole.selftest=true} (CI). A fake player swallows ten chunks (round-tripping
 * one with a chest through NBT), then the test verifies the breach, the recipe, devouring, debris,
 * fragment collection, bomb capture and the collapse, writes {@code selftest-server.txt} and stops the server.
 */
@Mod.EventBusSubscriber(modid = LivingBlackHole.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerSelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("livingblackhole.selftest");

    private static final List<String> REPORT = new ArrayList<>();
    private static MinecraftServer server;
    private static int tick = -1;
    private static boolean failed;
    private static boolean finished;
    private static UUID blackHoleId;
    private static Vec3 center;
    private static int maxDebris;
    private static int bombTick = -1;
    private static FakePlayer fake;

    private ServerSelfTest() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!ENABLED || !event.getServer().isDedicatedServer()) {
            return;
        }
        server = event.getServer();
        tick = 0;
        log("dedicated server started, beginning singularity self-test");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (tick < 0 || finished || event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        try {
            step(server.overworld());
        } catch (Throwable t) {
            check(false, "unexpected exception: " + t);
            LivingBlackHole.LOGGER.error("[SELFTEST] exception", t);
            finish();
        }
    }

    private static void step(ServerLevel level) {
        if (tick == 20) {
            BlockPos spawn = level.getSharedSpawnPos();
            fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("8d6f1d4e-55c4-4f6e-9b3a-1f0a5c2e7b11"), "[LBH-SelfTest]"));
            ItemStack pocket = new ItemStack(ModItems.POCKET_BLACK_HOLE.get());
            fake.setItemInHand(InteractionHand.MAIN_HAND, pocket);

            // ---- Chunk 1: a chest full of diamonds rides along inside the NBT.
            BlockPos target = surface(level, spawn.getX() + 64, spawn.getZ() + 8);
            BlockPos chestPos = target.above();
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            }
            int before = solidCount(level, ChunkCapture.regionMin(target));
            fake.moveTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 0.0F, 60.0F);
            boolean ok = PocketBlackHoleItem.consume(level, fake, pocket, InteractionHand.MAIN_HAND, target);
            int after = solidCount(level, ChunkCapture.regionMin(target));
            ItemStack chunk = findChunk(fake);
            check(ok && !chunk.isEmpty(), "right-click on a block produced a Compressed Chunk");
            int stored = ChunkCapture.hasStructure(chunk) ? ChunkCapture.data(chunk).getInt("Count") : 0;
            check(stored > 500 && stored == before - after, "16x16x16 region lifted into NBT (" + stored + " blocks, " + after + " left behind)");
            check(level.getBlockState(chestPos).isAir(), "captured blocks removed from the world");
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(12.0)).isEmpty(), "nothing dropped while capturing (chest contents kept in NBT)");
            check(PocketBlackHoleItem.getConsumed(pocket) == 1, "pocket counts consumed chunks (1)");

            // ---- Paste it 40 blocks away and check the chest and its diamonds came with it.
            BlockPos anchor = surface(level, spawn.getX() + 64, spawn.getZ() + 48);
            fake.getCooldowns().removeCooldown(ModItems.COMPRESSED_CHUNK.get());
            boolean placed = CompressedChunkItem.place(level, fake, chunk, anchor);
            int pasted = solidCount(level, ChunkCapture.regionMin(anchor));
            check(placed && chunk.isEmpty(), "compressed chunk placed and consumed");
            check(pasted >= stored, "structure pasted back (" + pasted + " solid blocks in the region)");
            boolean diamonds = level.getBlockEntity(anchor.above()) instanceof ChestBlockEntity c2 && c2.getItem(0).is(Items.DIAMOND) && c2.getItem(0).getCount() == 7;
            check(diamonds, "chest and its 7 diamonds restored from NBT");

            // ---- Chunks 2..10: the tenth breaks containment.
            for (int i = 1; i < PocketBlackHoleItem.limit(); i++) {
                BlockPos t = surface(level, spawn.getX() + 64 + i * 36, spawn.getZ() - 40);
                fake.moveTo(t.getX() + 0.5, t.getY() + 1, t.getZ() + 0.5, 0.0F, 60.0F);
                fake.getCooldowns().removeCooldown(ModItems.POCKET_BLACK_HOLE.get());
                PocketBlackHoleItem.consume(level, fake, pocket, InteractionHand.MAIN_HAND, t);
                if (i == PocketBlackHoleItem.limit() - 2) {
                    check(SingularitySavedData.get(server).isEmpty(), "no breach before the limit (" + (i + 1) + " chunks)");
                }
            }
            log("consumed " + PocketBlackHoleItem.limit() + " chunks");

            check(fake.getMainHandItem().isEmpty(), "pocket black hole deleted from inventory after the 10th chunk");
            List<SingularityEvent> events = SingularitySavedData.get(server).snapshot();
            check(events.size() == 1, "exactly one singularity event registered (found " + events.size() + ")");
            if (!events.isEmpty()) {
                SingularityEvent ev = events.get(0);
                blackHoleId = ev.blackHoleId;
                center = ev.center;
                check(center.distanceTo(fake.position()) < 3.0, "black hole spawned at the player's location");
                for (int i = 0; i < SingularityEvent.FRAGMENTS; i++) {
                    BlockPos f = ev.fragments[i];
                    double dist = Math.sqrt(f.distToCenterSqr(center.x, f.getY() + 0.5, center.z));
                    check(level.getBlockState(f).is(ModBlocks.CORE_FRAGMENT.get()), "core fragment " + i + " placed at " + f.toShortString());
                    check(dist <= 302 && dist >= 40, "core fragment " + i + " within 300 blocks (" + Math.round(dist) + ")");
                }
                check(level.getEntity(blackHoleId) instanceof BlackHoleEntity, "black hole entity exists");
            }

            // ---- The exact recipe: 3 Core Fragments + 1 Compressed Chunk = Anti-Gravity Bomb.
            ItemStack bombChunk = findChunk(fake);
            check(!bombChunk.isEmpty(), "player still holds compressed chunks for the recipe");
            AbstractContainerMenu dummy = new AbstractContainerMenu(null, -1) {
                @Override
                public ItemStack quickMoveStack(Player player, int index) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(Player player) {
                    return true;
                }
            };
            TransientCraftingContainer grid = new TransientCraftingContainer(dummy, 3, 3);
            grid.setItem(0, new ItemStack(ModItems.CORE_FRAGMENT.get()));
            grid.setItem(4, new ItemStack(ModItems.CORE_FRAGMENT.get()));
            grid.setItem(8, new ItemStack(ModItems.CORE_FRAGMENT.get()));
            grid.setItem(5, bombChunk.copy());
            ItemStack result = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                    .map(r -> r.assemble(grid, level.registryAccess())).orElse(ItemStack.EMPTY);
            check(result.is(ModItems.ANTI_GRAVITY_BOMB.get()), "crafting 3 Core Fragments + 1 Compressed Chunk yields the Anti-Gravity Bomb");
            grid.setItem(5, ItemStack.EMPTY);
            boolean incomplete = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).isPresent();
            check(!incomplete, "recipe needs the Compressed Chunk (3 fragments alone craft nothing)");
        }

        if (tick > 20 && tick < 320 && center != null) {
            int debris = level.getEntitiesOfClass(GravitationalDebrisEntity.class, new AABB(center, center).inflate(40.0)).size();
            maxDebris = Math.max(maxDebris, debris);
        }

        if (tick == 320 && blackHoleId != null) {
            if (level.getEntity(blackHoleId) instanceof BlackHoleEntity bh) {
                log("black hole state=" + bh.getState() + " mass=" + bh.getMass() + " reach=" + bh.getReach());
                check(bh.getMass() > 50, "black hole devoured blocks (mass " + bh.getMass() + ")");
                check(bh.getState() == BlackHoleEntity.STATE_ACTIVE, "black hole finished its birth and is active");
            } else {
                check(false, "black hole still exists after 15 seconds");
            }
            check(maxDebris > 0, "gravitational debris entities spawned (peak " + maxDebris + ")");

            SingularityEvent ev = SingularitySavedData.get(server).snapshot().get(0);
            BlockPos frag = ev.fragments[0];
            level.setBlock(frag, Blocks.AIR.defaultBlockState(), 3);
            SingularityManager.onFragmentBroken(level, frag, fake);
            check(SingularitySavedData.get(server).snapshot().get(0).collected[0], "mining a core fragment marks it collected");
        }

        if (tick == 340 && center != null) {
            AntiGravityBombEntity bomb = new AntiGravityBombEntity(ModEntities.ANTI_GRAVITY_BOMB.get(), level);
            Vec3 from = center.add(26.0, 6.0, 4.0);
            bomb.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
            bomb.setItem(new ItemStack(ModItems.ANTI_GRAVITY_BOMB.get()));
            bomb.setDeltaMovement(center.subtract(from).normalize().scale(0.8));
            level.addFreshEntity(bomb);
            bombTick = tick;
            log("anti-gravity bomb thrown from 26 blocks out");
        }

        if (bombTick > 0 && tick == bombTick + 25 && blackHoleId != null) {
            if (level.getEntity(blackHoleId) instanceof BlackHoleEntity bh) {
                check(bh.getState() >= BlackHoleEntity.STATE_DESTABILIZING, "bomb captured and collapse started (state " + bh.getState() + ")");
            }
        }

        if (bombTick > 0 && tick > bombTick + 25) {
            boolean gone = !(level.getEntity(blackHoleId) instanceof BlackHoleEntity);
            boolean cleared = SingularitySavedData.get(server).isEmpty();
            if (gone && cleared) {
                check(true, "black hole collapsed, despawned and the event was cleared after " + (tick - bombTick) + " ticks");
                finish();
            } else if (tick > bombTick + 600) {
                check(false, "black hole neutralized within 30 seconds (gone=" + gone + ", cleared=" + cleared + ")");
                finish();
            }
        }

        if (tick > 2400 && !finished) {
            check(false, "self-test timed out");
            finish();
        }
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static int solidCount(ServerLevel level, BlockPos min) {
        int n = 0;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (!level.getBlockState(c.set(min.getX() + x, min.getY() + y, min.getZ() + z)).isAir()) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static ItemStack findChunk(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(ModItems.COMPRESSED_CHUNK.get())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failed = true;
        }
        log((ok ? "PASS  " : "FAIL  ") + what);
    }

    private static void log(String message) {
        REPORT.add(message);
        LivingBlackHole.LOGGER.info("[SELFTEST] {}", message);
    }

    private static void finish() {
        if (finished) {
            return;
        }
        finished = true;
        REPORT.add("RESULT: " + (failed ? "FAIL" : "PASS"));
        try {
            Path out = server.getServerDirectory().toPath().resolve("selftest-server.txt");
            Files.write(out, REPORT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LivingBlackHole.LOGGER.error("[SELFTEST] could not write report", e);
        }
        LivingBlackHole.LOGGER.info("[SELFTEST] RESULT: {}", failed ? "FAIL" : "PASS");
        watchdog(failed ? 1 : 0);
        server.halt(false);
    }

    /** Guarantees the test JVM exits even if a stray non-daemon thread survives shutdown. */
    static void watchdog(int status) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException ignored) {
                return;
            }
            Runtime.getRuntime().halt(status);
        }, "livingblackhole-selftest-watchdog");
        t.setDaemon(true);
        t.start();
    }
}
