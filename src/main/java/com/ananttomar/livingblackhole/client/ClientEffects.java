package com.ananttomar.livingblackhole.client;

import com.ananttomar.livingblackhole.Config;
import com.ananttomar.livingblackhole.client.particle.ParticleFx;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.network.CinematicPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side cinematic state: camera shake, flashes, glitch timers, banners and the smoothed
 * "how close is the singularity" factors read by the sky mixin, fog, FOV and the HUD.
 */
public final class ClientEffects {
    public static final int BANNER_NONE = -1;

    private static float shake, shakeO;
    private static float ambientShake, ambientShakeO;
    private static float flash, flashO;
    private static float redFlash, redFlashO;
    private static float skyFlash, skyFlashO;
    private static float darkness, darknessO;
    private static float proximity, proximityO;
    private static int glitchTicks;
    private static int introTicks;

    private static int bannerType = BANNER_NONE;
    private static int bannerTicks;
    private static int bannerDuration = 1;
    private static float bannerValue;

    @Nullable
    private static BlackHoleEntity nearest;
    private static double nearestDistance = Double.MAX_VALUE;
    private static long ticks;

    private ClientEffects() {
    }

    public static void tick(Minecraft mc) {
        ticks++;
        shakeO = shake;
        ambientShakeO = ambientShake;
        flashO = flash;
        redFlashO = redFlash;
        skyFlashO = skyFlash;
        darknessO = darkness;
        proximityO = proximity;

        shake *= 0.91F;
        flash *= 0.915F;
        redFlash *= 0.94F;
        skyFlash *= 0.95F;
        if (glitchTicks > 0) {
            glitchTicks--;
        }
        if (introTicks > 0) {
            introTicks--;
        }
        if (bannerTicks > 0 && --bannerTicks == 0) {
            bannerType = BANNER_NONE;
        }

        nearest = null;
        nearestDistance = Double.MAX_VALUE;
        if (mc.level == null || mc.player == null) {
            darkness = 0.0F;
            proximity = 0.0F;
            ambientShake = 0.0F;
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof BlackHoleEntity bh && !bh.isRemoved() && bh.getState() < BlackHoleEntity.STATE_SHOCKWAVE) {
                double d = bh.position().distanceTo(mc.player.getEyePosition());
                if (d < nearestDistance) {
                    nearestDistance = d;
                    nearest = bh;
                }
            }
        }

        float targetDark = 0.0F;
        float targetProx = 0.0F;
        float targetAmbient = 0.0F;
        if (nearest != null) {
            float scale = Math.min(1.0F, nearest.getVisualScale(1.0F));
            float reach = nearest.getReach();
            float d = (float) nearestDistance;
            targetDark = Mth.clamp(1.0F - (d - reach) / 220.0F, 0.0F, 1.0F) * scale * 0.9F;
            targetProx = Mth.clamp(1.0F - d / (reach * 2.2F), 0.0F, 1.0F) * scale;
            float instability = nearest.getInstability(1.0F);
            targetAmbient = 0.14F * targetProx * targetProx
                    + instability * 0.6F * Mth.clamp(1.0F - d / 220.0F, 0.0F, 1.0F);
        }
        darkness += (targetDark - darkness) * 0.04F;
        proximity += (targetProx - proximity) * 0.08F;
        ambientShake += (targetAmbient - ambientShake) * 0.15F;
    }

    public static void reset() {
        shake = shakeO = ambientShake = ambientShakeO = 0.0F;
        flash = flashO = redFlash = redFlashO = skyFlash = skyFlashO = 0.0F;
        darkness = darknessO = proximity = proximityO = 0.0F;
        glitchTicks = introTicks = 0;
        bannerType = BANNER_NONE;
        bannerTicks = 0;
        nearest = null;
    }

    // ------------------------------------------------------------------ cinematic triggers

    public static void onCinematic(CinematicPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double d = mc.player.position().distanceTo(msg.pos);
        float near = (float) Mth.clamp(1.0 - d / 400.0, 0.15, 1.0);
        switch (msg.type) {
            case CinematicPacket.ESCAPE -> {
                addShake(2.4F * near);
                redFlash(1.0F);
                glitchTicks = 34;
                introTicks = 120;
                showBanner(CinematicPacket.ESCAPE, 120, 0.0F);
            }
            case CinematicPacket.COLLAPSE_START -> {
                addShake(0.8F * near);
                glitchTicks = Math.max(glitchTicks, 12);
                showBanner(CinematicPacket.COLLAPSE_START, BlackHoleEntity.DESTABILIZE_TICKS + BlackHoleEntity.COLLAPSE_TICKS, 0.0F);
            }
            case CinematicPacket.DETONATION -> {
                addShake(4.0F * near);
                flash(d < 600.0 ? 1.0F : 0.55F);
                skyFlash = 1.0F;
                glitchTicks = Math.max(glitchTicks, 8);
                if (d < 320.0) {
                    ParticleFx.detonation(msg.pos, msg.value);
                }
            }
            case CinematicPacket.NEUTRALIZED -> showBanner(CinematicPacket.NEUTRALIZED, 200, 0.0F);
            case CinematicPacket.BOMB_CAPTURED -> {
                addShake(0.35F * near);
                showBanner(CinematicPacket.BOMB_CAPTURED, 70, 0.0F);
            }
            case CinematicPacket.FRAGMENT_SECURED -> showBanner(CinematicPacket.FRAGMENT_SECURED, 90, msg.value);
            case CinematicPacket.VANISHED -> {
                bannerType = BANNER_NONE;
                bannerTicks = 0;
            }
            default -> {
            }
        }
    }

    public static void addShake(float amount) {
        shake = Math.min(5.0F, shake + amount);
    }

    public static void flash(float amount) {
        if (Config.FLASHES.get()) {
            flash = Math.max(flash, amount);
        }
    }

    public static void redFlash(float amount) {
        if (Config.FLASHES.get()) {
            redFlash = Math.max(redFlash, amount);
        }
    }

    private static void showBanner(int type, int duration, float value) {
        bannerType = type;
        bannerTicks = duration;
        bannerDuration = Math.max(1, duration);
        bannerValue = value;
    }

    // ------------------------------------------------------------------ readers

    public static float skyDarkness(float partialTick) {
        if (!Config.SKY_DARKENING.get()) {
            return 0.0F;
        }
        return Mth.lerp(partialTick, darknessO, darkness);
    }

    public static float skyFlash(float partialTick) {
        if (!Config.FLASHES.get()) {
            return 0.0F;
        }
        return Mth.lerp(partialTick, skyFlashO, skyFlash);
    }

    public static float proximity(float partialTick) {
        return Mth.lerp(partialTick, proximityO, proximity);
    }

    public static float shakeIntensity(float partialTick) {
        float s = Mth.lerp(partialTick, shakeO, shake) + Mth.lerp(partialTick, ambientShakeO, ambientShake);
        return s * (float) (double) Config.SCREEN_SHAKE.get();
    }

    public static float flashAlpha(float partialTick) {
        return Mth.lerp(partialTick, flashO, flash);
    }

    public static float redFlashAlpha(float partialTick) {
        return Mth.lerp(partialTick, redFlashO, redFlash);
    }

    public static int glitchTicks() {
        return glitchTicks;
    }

    public static int introTicks() {
        return introTicks;
    }

    public static int bannerType() {
        return bannerType;
    }

    /** 0 at banner start, 1 at its end. */
    public static float bannerProgress(float partialTick) {
        return Mth.clamp(1.0F - (bannerTicks - partialTick) / bannerDuration, 0.0F, 1.0F);
    }

    public static float bannerValue() {
        return bannerValue;
    }

    @Nullable
    public static BlackHoleEntity nearest() {
        return nearest;
    }

    public static double nearestDistance() {
        return nearestDistance;
    }

    public static long ticks() {
        return ticks;
    }

    /** Smooth pseudo-random camera noise in [-1, 1]. */
    public static float noise(float t, float seed) {
        return (Mth.sin(t * 1.7F + seed) * 0.5F + Mth.sin(t * 3.1F + seed * 2.3F) * 0.3F + Mth.sin(t * 7.3F + seed * 0.7F) * 0.2F);
    }

    public static Vec3 nearestCenter() {
        return nearest != null ? nearest.position() : Vec3.ZERO;
    }
}
