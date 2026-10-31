package com.ananttomar.livingblackhole;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // Pocket Black Hole
    public static final ForgeConfigSpec.IntValue CHUNK_LIMIT;
    public static final ForgeConfigSpec.IntValue POCKET_COOLDOWN;

    // Escaped singularity
    public static final ForgeConfigSpec.IntValue SUCTION_RADIUS;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_DEBRIS;
    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue KEEP_CHUNK_LOADED;
    public static final ForgeConfigSpec.BooleanValue PULL_MOBS;
    public static final ForgeConfigSpec.DoubleValue PLAYER_PULL;

    // Quest
    public static final ForgeConfigSpec.IntValue FRAGMENT_RADIUS;
    public static final ForgeConfigSpec.IntValue FRAGMENT_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue BOMB_CAPTURE_RADIUS;
    public static final ForgeConfigSpec.IntValue NEUTRALIZE_XP;

    // Client visuals
    public static final ForgeConfigSpec.BooleanValue LENSING;
    public static final ForgeConfigSpec.DoubleValue LENSING_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ForgeConfigSpec.BooleanValue SKY_DARKENING;
    public static final ForgeConfigSpec.BooleanValue FLASHES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("The Pocket Black Hole - swallows 16x16x16 chunks of the world into Compressed Chunks.").push("pocket_black_hole");
        CHUNK_LIMIT = b.comment("Chunks the Pocket Black Hole can swallow before its containment fails.")
                .defineInRange("chunkLimit", 10, 1, 10_000);
        POCKET_COOLDOWN = b.comment("Cooldown after consuming or placing a chunk, in ticks.")
                .defineInRange("cooldownTicks", 20, 0, 1200);
        b.pop();

        b.comment("The escaped, living black hole.").push("singularity");
        SUCTION_RADIUS = b.comment("Radius the escaped black hole devours blocks and drags entities in.")
                .defineInRange("suctionRadius", 30, 4, 96);
        BLOCKS_PER_TICK = b.comment("Blocks devoured per tick once fully grown.")
                .defineInRange("blocksPerTick", 7, 0, 256);
        MAX_DEBRIS = b.comment("Maximum flying debris entities per black hole (performance).")
                .defineInRange("maxDebris", 140, 0, 2000);
        DESTROY_BLOCKS = b.comment("If false the black hole is purely visual and never removes blocks.")
                .define("destroyBlocks", true);
        KEEP_CHUNK_LOADED = b.comment("Keep the black hole's chunk ticking while players are away.")
                .define("keepChunkLoaded", true);
        PULL_MOBS = b.comment("Drag and devour mobs, items and other non-player entities.")
                .define("pullEntities", true);
        PLAYER_PULL = b.comment("Strength of the gentle drag on players (1.0 = default: walking slowly loses ground near the core, sprinting always escapes; 0 disables).")
                .defineInRange("playerPull", 1.0, 0.0, 3.0);
        b.pop();

        b.comment("The Core Fragment hunt.").push("quest");
        FRAGMENT_RADIUS = b.comment("Maximum distance from the breach where Core Fragments crash down.")
                .defineInRange("fragmentRadius", 300, 32, 5000);
        FRAGMENT_MIN_DISTANCE = b.comment("Minimum distance from the breach for Core Fragments (keeps them out of the devour zone).")
                .defineInRange("fragmentMinDistance", 80, 8, 4000);
        BOMB_CAPTURE_RADIUS = b.comment("Radius in which the black hole's gravity grabs a thrown Anti-Gravity Bomb.")
                .defineInRange("bombCaptureRadius", 40, 4, 256);
        NEUTRALIZE_XP = b.comment("Experience released when the singularity collapses.")
                .defineInRange("neutralizeExperience", 750, 0, 100_000);
        b.pop();

        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.comment("Cinematic rendering options.").push("visuals");
        LENSING = c.comment("Screen-space gravitational lensing shader around black holes.")
                .define("gravitationalLensing", true);
        LENSING_STRENGTH = c.comment("Strength multiplier of the lensing distortion.")
                .defineInRange("lensingStrength", 1.0, 0.0, 3.0);
        SCREEN_SHAKE = c.comment("Camera shake multiplier (0 disables).")
                .defineInRange("screenShake", 1.0, 0.0, 3.0);
        PARTICLE_DENSITY = c.comment("Particle density multiplier.")
                .defineInRange("particleDensity", 1.0, 0.0, 4.0);
        SKY_DARKENING = c.comment("Darken the sky and reveal the stars near a black hole.")
                .define("skyDarkening", true);
        FLASHES = c.comment("Full-screen flashes during the breach and collapse (disable for photosensitivity).")
                .define("screenFlashes", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private Config() {
    }
}
