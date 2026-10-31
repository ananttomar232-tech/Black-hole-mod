package com.ananttomar.livingblackhole.selftest;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.client.ClientSingularityState;
import com.ananttomar.livingblackhole.client.particle.ModParticleProviders;
import com.ananttomar.livingblackhole.client.fx.ChunkEffects;
import com.ananttomar.livingblackhole.client.fx.GravityPull;
import com.ananttomar.livingblackhole.client.render.BeamEffects;
import com.ananttomar.livingblackhole.item.CompressedChunkItem;
import com.ananttomar.livingblackhole.client.render.LensingEffect;
import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.ananttomar.livingblackhole.registry.ModBlocks;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.ananttomar.livingblackhole.singularity.SingularityEvent;
import com.ananttomar.livingblackhole.singularity.SingularityManager;
import com.ananttomar.livingblackhole.singularity.SingularitySavedData;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scripted cinematic run of the whole mod in a real client (CI under Xvfb), enabled only with
 * {@code -Dlivingblackhole.selftest=true}. Creates a world, swallows a chunk and pastes it back,
 * breaches the Pocket Black Hole on the tenth chunk, tests the pull on the player, films the
 * singularity, a Core Fragment, the bomb, the collapse and the aftermath, checks
 * every model, texture, particle sprite, sound and the lensing shader, then quits.
 */
@Mod.EventBusSubscriber(modid = LivingBlackHole.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientSelfTest {
    private static final List<String> REPORT = new ArrayList<>();
    private static final List<Runnable> SCHEDULE = new ArrayList<>();
    private static final List<String> PENDING_SHOTS = new ArrayList<>();

    private static int phase;
    private static int clock;
    private static int phaseStart;
    private static boolean failed;
    private static boolean worldCreated;
    private static Vec3 center;
    private static Vec3 vantage;
    private static BlockPos fragment;
    private static BlockPos target;
    private static Vec3 pullStart;
    private static int pullTicksAtStart;
    private static int shots;
    private static int lastState = -1;
    private static int stateSeenAt;
    private static boolean sawHudEvent;
    private static boolean sawLensing;

    private ClientSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ServerSelfTest.ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            tick(mc);
        } catch (Throwable t) {
            check(false, "unexpected exception: " + t);
            LivingBlackHole.LOGGER.error("[SELFTEST] client exception", t);
            finish(mc);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (ServerSelfTest.ENABLED && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && ClientEffects.nearest() != null) {
            sawLensing |= LensingEffect.isReady();
        }
    }

    private static void tick(Minecraft mc) {
        clock++;
        if (clock > 20 * 60 * 8) {
            check(false, "client self-test timed out in phase " + phase);
            finish(mc);
            return;
        }
        if (phase == 0) {
            if (!worldCreated && mc.screen instanceof TitleScreen && mc.getOverlay() == null && clock > 40) {
                worldCreated = true;
                verifyResources(mc);
                String name = "lbh-selftest";
                LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                log("creating world '" + name + "'");
                mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20251029L, true, false),
                        WorldPresets::createNormalWorldDimensions);
            }
            if (mc.level != null && mc.player != null && mc.screen == null) {
                next(1);
            }
            return;
        }

        int t = clock - phaseStart;
        runScheduled(mc);
        BlackHoleEntity bh = findBlackHole(mc);
        if (bh != null && bh.getState() != lastState) {
            lastState = bh.getState();
            stateSeenAt = clock;
            log("client sees black hole state " + lastState);
        }
        if (!ClientSingularityState.events().isEmpty()) {
            sawHudEvent = true;
        }

        switch (phase) {
            case 1 -> {
                if (t == 60) {
                    server(level -> {
                        ServerPlayer p = player();
                        level.setDayTime(6000);
                        level.setWeatherParameters(6000, 0, false, false);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
                        BlockPos spawn = level.getSharedSpawnPos();
                        int x = spawn.getX() + 24;
                        int z = spawn.getZ();
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                        // Hover above the hills looking east and down at the chunk we are about to eat.
                        p.connection.teleport(x + 0.5, y + 10.0, z + 0.5, -90.0F, 28.0F);
                        p.getInventory().setItem(0, pocketAt(PocketBlackHoleItem.limit() - 2));
                        p.getInventory().setItem(1, new ItemStack(ModItems.CORE_FRAGMENT.get(), 3));
                        p.getInventory().setItem(2, new ItemStack(ModItems.ANTI_GRAVITY_BOMB.get(), 2));
                        p.getInventory().selected = 0;
                        target = new BlockPos(x + 22, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 22, z) - 1, z);
                    });
                }
                if (t == 160) {
                    shot(mc, "00_before_capture");
                    server(level -> {
                        ServerPlayer p = player();
                        boolean ok = PocketBlackHoleItem.consume(level, p, p.getMainHandItem(), InteractionHand.MAIN_HAND, target);
                        check(ok, "Pocket Black Hole swallowed a 16x16x16 chunk");
                        check(findChunk(p) >= 0, "Compressed Chunk delivered to the inventory");
                    });
                }
                if (t == 164) {
                    shot(mc, "01_vortex");
                }
                if (t == 176) {
                    check(ChunkEffects.captures > 0, "swirling capture effect played on the client");
                    shot(mc, "02_vortex_drain");
                }
                if (t == 215) {
                    shot(mc, "03_chunk_hole");
                    server(level -> {
                        ServerPlayer p = player();
                        int slot = findChunk(p);
                        if (slot < 0) {
                            check(false, "compressed chunk available to place");
                            return;
                        }
                        // Set it down as a floating island further along the valley.
                        BlockPos anchor = target.offset(24, 14, 6);
                        p.getCooldowns().removeCooldown(ModItems.COMPRESSED_CHUNK.get());
                        boolean ok = CompressedChunkItem.place(level, p, p.getInventory().getItem(slot), anchor);
                        check(ok && findChunk(p) < 0, "Compressed Chunk pasted back into the world and consumed");
                    });
                }
                if (t == 219) {
                    shot(mc, "04_chunk_unfold");
                }
                if (t == 270) {
                    check(ChunkEffects.placements > 0, "placement effect played on the client");
                    shot(mc, "05_floating_chunk");
                }
                if (t == 290) {
                    server(level -> {
                        ServerPlayer p = player();
                        p.getCooldowns().removeCooldown(ModItems.POCKET_BLACK_HOLE.get());
                        BlockPos second = target.offset(-4, 0, -26);
                        second = new BlockPos(second.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, second.getX(), second.getZ()) - 1, second.getZ());
                        PocketBlackHoleItem.consume(level, p, p.getMainHandItem(), InteractionHand.MAIN_HAND, second);
                        List<SingularityEvent> events = SingularitySavedData.get(level.getServer()).snapshot();
                        check(!events.isEmpty(), "10th chunk breached containment and created a singularity event");
                        if (!events.isEmpty()) {
                            center = events.get(0).center;
                            check(center.distanceTo(p.position()) < 4.0, "black hole spawned at the player's location");
                            fragment = events.get(0).fragments[0];
                            for (BlockPos f : events.get(0).fragments) {
                                if (level.canSeeSky(f.above())) {
                                    fragment = f;
                                    break;
                                }
                            }
                        }
                        check(p.getMainHandItem().isEmpty(), "pocket black hole deleted on breach");
                        check(findChunk(p) >= 0, "player keeps the compressed chunk needed for the bomb");
                    });
                }
                if (t == 296) {
                    shot(mc, "06_containment_breach");
                }
                if (t == 312) {
                    check(sawHudEvent, "HUD received the singularity event");
                    shot(mc, "07_breach_hud");
                }
                if (t == 330 && center != null) {
                    vantage = center.add(-26.0, 9.0, 3.0);
                    lookAt(vantage, center);
                }
                if (t == 360) {
                    shot(mc, "08_birth");
                }
                if (t == 460) {
                    shot(mc, "09_living_black_hole");
                }
                if (t == 560) {
                    check(findBlackHole(mc) != null, "black hole entity tracked by the client");
                    shot(mc, "10_devouring");
                    next(5);
                }
            }
            case 5 -> {
                // Gravity pull on the player, on a bedrock walkway the singularity cannot eat.
                if (t == 1 && center != null) {
                    server(level -> {
                        int y0 = (int) Math.floor(center.y) - 3;
                        int cz = (int) Math.floor(center.z);
                        for (int dx = 5; dx <= 30; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                int x = (int) Math.floor(center.x) - dx;
                                level.setBlock(new BlockPos(x, y0, cz + dz), Blocks.BEDROCK.defaultBlockState(), 3);
                                for (int dy = 1; dy <= 3; dy++) {
                                    level.setBlock(new BlockPos(x, y0 + dy, cz + dz), Blocks.AIR.defaultBlockState(), 3);
                                }
                            }
                        }
                        ServerPlayer p = player();
                        p.getAbilities().flying = false;
                        p.onUpdateAbilities();
                        p.connection.teleport(Math.floor(center.x) - 11 + 0.5, y0 + 1.0, cz + 0.5, 90.0F, 15.0F);
                    });
                }
                if (t == 30) {
                    pullStart = mc.player.position();
                    pullTicksAtStart = GravityPull.ticksApplied;
                }
                if (t == 90) {
                    Vec3 now = mc.player.position();
                    double before = horizontal(pullStart, center);
                    double after = horizontal(now, center);
                    check(GravityPull.ticksApplied > pullTicksAtStart && after < before - 0.6,
                            String.format(java.util.Locale.ROOT, "standing player dragged toward the singularity (%.1f -> %.1f blocks)", before, after));
                    shot(mc, "11_gravity_pull");
                    pullStart = now;
                    mc.options.keyUp.setDown(true);
                    mc.options.keySprint.setDown(true);
                }
                if (t == 150) {
                    mc.options.keyUp.setDown(false);
                    mc.options.keySprint.setDown(false);
                    double before = horizontal(pullStart, center);
                    double after = horizontal(mc.player.position(), center);
                    check(after > before + 3.0, String.format(java.util.Locale.ROOT, "sprinting escapes the pull (%.1f -> %.1f blocks)", before, after));
                    server(level -> {
                        ServerPlayer p = player();
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                    });
                    next(2);
                }
            }
            case 2 -> {
                if (t == 10 && fragment != null) {
                    Vec3 f = Vec3.atCenterOf(fragment);
                    lookAt(f.add(-7.0, 3.5, -7.0), f.add(0.0, 0.5, 0.0));
                }
                if (t == 130) {
                    shot(mc, "12_core_fragment");
                    server(level -> {
                        if (level.getBlockState(fragment).is(ModBlocks.CORE_FRAGMENT.get())) {
                            level.setBlock(fragment, Blocks.AIR.defaultBlockState(), 3);
                            SingularityManager.onFragmentBroken(level, fragment, player());
                        }
                    });
                }
                if (t == 145) {
                    shot(mc, "13_fragment_secured");
                }
                if (t == 160 && vantage != null) {
                    lookAt(vantage, center);
                }
                if (t == 250) {
                    server(level -> {
                        ServerPlayer p = player();
                        AntiGravityBombEntity bomb = new AntiGravityBombEntity(level, p);
                        bomb.setItem(new ItemStack(ModItems.ANTI_GRAVITY_BOMB.get()));
                        Vec3 dir = center.subtract(p.getEyePosition()).normalize();
                        bomb.shoot(dir.x, dir.y, dir.z, 1.3F, 0.0F);
                        level.addFreshEntity(bomb);
                    });
                }
                if (t == 262) {
                    shot(mc, "14_bomb_captured");
                    next(3);
                }
            }
            case 3 -> {
                if (lastState == BlackHoleEntity.STATE_DESTABILIZING && clock - stateSeenAt == 45) {
                    shot(mc, "15_destabilizing");
                }
                if (lastState == BlackHoleEntity.STATE_COLLAPSE && clock - stateSeenAt == 8) {
                    shot(mc, "16_implosion");
                }
                if (lastState == BlackHoleEntity.STATE_SHOCKWAVE) {
                    int s = clock - stateSeenAt;
                    if (s == 2) {
                        shot(mc, "17_detonation");
                    } else if (s == 14) {
                        shot(mc, "18_shockwave");
                    } else if (s == 40) {
                        shot(mc, "19_shockwave_expanding");
                    }
                }
                if (bh == null && lastState == BlackHoleEntity.STATE_SHOCKWAVE && ClientSingularityState.events().isEmpty()) {
                    next(4);
                }
                if (t > 1200) {
                    check(false, "collapse finished within 60 seconds");
                    finish(mc);
                }
            }
            case 4 -> {
                if (t == 30) {
                    shot(mc, "20_neutralized");
                    check(ClientSingularityState.events().isEmpty(), "HUD cleared after the black hole was destroyed");
                    check(sawLensing, "gravitational lensing shader compiled and ran");
                    check(shots >= 19, "captured " + shots + " cinematic screenshots");
                }
                if (t >= 60 && PENDING_SHOTS.isEmpty()) {
                    finish(mc);
                }
            }
            default -> {
            }
        }
    }

    private static BlackHoleEntity findBlackHole(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof BlackHoleEntity bh && !bh.isRemoved()) {
                return bh;
            }
        }
        return null;
    }

    private static ItemStack pocketAt(int consumed) {
        ItemStack stack = new ItemStack(ModItems.POCKET_BLACK_HOLE.get());
        PocketBlackHoleItem.setConsumed(stack, consumed);
        return stack;
    }

    private static int findChunk(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.COMPRESSED_CHUNK.get())) {
                return i;
            }
        }
        return -1;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void next(int newPhase) {
        phase = newPhase;
        phaseStart = clock;
        log("phase " + newPhase);
    }

    private static ServerPlayer player() {
        return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().get(0);
    }

    private static void server(Consumer<ServerLevel> action) {
        var srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv != null) {
            srv.execute(() -> {
                try {
                    action.accept(srv.overworld());
                } catch (Throwable t) {
                    check(false, "server action threw " + t);
                    LivingBlackHole.LOGGER.error("[SELFTEST] server action failed", t);
                }
            });
        }
    }

    private static void lookAt(Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        server(level -> {
            ServerPlayer p = player();
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(from.x, from.y - p.getEyeHeight(), from.z, yaw, pitch);
        });
    }

    private static void runScheduled(Minecraft mc) {
        if (!SCHEDULE.isEmpty()) {
            List<Runnable> copy = new ArrayList<>(SCHEDULE);
            SCHEDULE.clear();
            copy.forEach(Runnable::run);
        }
    }

    private static void shot(Minecraft mc, String name) {
        // Captured from the next rendered frame so the image matches this tick's state even when
        // the software renderer runs several ticks per frame.
        PENDING_SHOTS.add(name);
    }

    @SubscribeEvent
    public static void onGuiRendered(RenderGuiEvent.Post event) {
        if (!ServerSelfTest.ENABLED || PENDING_SHOTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (String name : new ArrayList<>(PENDING_SHOTS)) {
            shots++;
            Screenshot.grab(mc.gameDirectory, "lbh_" + name + ".png", mc.getMainRenderTarget(), message -> {
            });
            log("screenshot " + name + " (beam frames drawn so far: " + BeamEffects.framesDrawn + ")");
        }
        PENDING_SHOTS.clear();
    }

    private static void verifyResources(Minecraft mc) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        for (Item item : new Item[]{ModItems.POCKET_BLACK_HOLE.get(), ModItems.COMPRESSED_CHUNK.get(), ModItems.CORE_FRAGMENT.get(),
                ModItems.ANTI_GRAVITY_BOMB.get()}) {
            BakedModel model = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
            check(model != missing, "item model loaded: " + item);
        }
        check(mc.getBlockRenderer().getBlockModel(ModBlocks.CORE_FRAGMENT.get().defaultBlockState()) != missing, "block model loaded: core_fragment");

        String[] sprites = {"item/pocket_casing", "item/pocket_core_stable", "item/pocket_core_unstable", "item/pocket_core_critical",
                "item/pocket_ring_stable", "item/pocket_ring_unstable", "item/pocket_ring_critical",
                "item/chunk_top", "item/chunk_side", "item/chunk_bottom", "item/bomb_shell", "item/bomb_band", "item/bomb_metal",
                "block/core_fragment_crystal", "block/core_fragment_base"};
        for (String s : sprites) {
            TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LivingBlackHole.id(s));
            check(!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), "texture stitched: " + s);
        }
        check(ModParticleProviders.accretion != null && ModParticleProviders.streak != null && ModParticleProviders.spark != null
                && ModParticleProviders.matter != null && ModParticleProviders.antigrav != null && ModParticleProviders.glint != null
                && ModParticleProviders.shock != null && ModParticleProviders.jet != null, "all particle sprite sets registered");
        for (var sound : ModSounds.SOUNDS.getEntries()) {
            ResourceLocation id = sound.getId();
            check(mc.getSoundManager().getSoundEvent(id) != null, "sound defined: " + id);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failed = true;
        }
        log((ok ? "PASS  " : "FAIL  ") + what);
    }

    private static void log(String message) {
        synchronized (REPORT) {
            REPORT.add(message);
        }
        LivingBlackHole.LOGGER.info("[SELFTEST] {}", message);
    }

    private static void finish(Minecraft mc) {
        if (phase == 99) {
            return;
        }
        phase = 99;
        synchronized (REPORT) {
            REPORT.add("RESULT: " + (failed ? "FAIL" : "PASS"));
            try {
                Files.write(mc.gameDirectory.toPath().resolve("selftest-client.txt"), REPORT, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LivingBlackHole.LOGGER.error("[SELFTEST] could not write report", e);
            }
        }
        LivingBlackHole.LOGGER.info("[SELFTEST] RESULT: {}", failed ? "FAIL" : "PASS");
        ServerSelfTest.watchdog(failed ? 1 : 0);
        mc.stop();
    }
}
