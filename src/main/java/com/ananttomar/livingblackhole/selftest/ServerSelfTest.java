package com.ananttomar.livingblackhole.selftest;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.entity.GravitationalDebrisEntity;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.ananttomar.livingblackhole.registry.ModBlocks;
import com.ananttomar.livingblackhole.registry.ModEntities;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.singularity.PocketBeamLogic;
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
 * with {@code -Dlivingblackhole.selftest=true} (CI). A fake player fires an almost-full Pocket
 * Black Hole, then the test verifies the breach, devouring, debris, fragment collection, bomb
 * capture and the collapse, writes {@code selftest-server.txt} and stops the server.
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
            int x = spawn.getX() + 48;
            int z = spawn.getZ() + 8;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("8d6f1d4e-55c4-4f6e-9b3a-1f0a5c2e7b11"), "[LBH-SelfTest]"));
            fake.moveTo(x + 0.5, y, z + 0.5, 0.0F, 55.0F);
            ItemStack pocket = new ItemStack(ModItems.POCKET_BLACK_HOLE.get());
            PocketBlackHoleItem.setConsumed(pocket, PocketBlackHoleItem.limit() - 12);
            fake.setItemInHand(InteractionHand.MAIN_HAND, pocket);
            log("firing near-critical Pocket Black Hole at " + fake.blockPosition());
            PocketBeamLogic.fire(level, fake, pocket, InteractionHand.MAIN_HAND, 1.0F);

            check(fake.getMainHandItem().isEmpty(), "pocket black hole deleted from inventory after breach");
            check(fake.getInventory().countItem(ModItems.COMPRESSED_MATTER.get()) > 0, "compressed matter delivered to the inventory");
            List<SingularityEvent> events = SingularitySavedData.get(server).snapshot();
            check(events.size() == 1, "exactly one singularity event registered (found " + events.size() + ")");
            if (!events.isEmpty()) {
                SingularityEvent ev = events.get(0);
                blackHoleId = ev.blackHoleId;
                center = ev.center;
                for (int i = 0; i < SingularityEvent.FRAGMENTS; i++) {
                    BlockPos f = ev.fragments[i];
                    double dist = Math.sqrt(f.distToCenterSqr(center.x, f.getY() + 0.5, center.z));
                    check(level.getBlockState(f).is(ModBlocks.CORE_FRAGMENT.get()), "core fragment " + i + " placed at " + f.toShortString());
                    check(dist <= 302 && dist >= 40, "core fragment " + i + " within 300 blocks (" + Math.round(dist) + ")");
                }
                check(level.getEntity(blackHoleId) instanceof BlackHoleEntity, "black hole entity spawned at the player's position");
            }
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
        server.halt(false);
    }
}
