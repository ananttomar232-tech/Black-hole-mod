package com.ananttomar.livingblackhole.mixin.client;

import com.ananttomar.livingblackhole.client.ClientEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Near a singularity, light itself is being swallowed: the sky dims toward a bruised violet,
 * clouds go dark and the stars come out - even at noon.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void livingblackhole$darkenSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float dark = ClientEffects.skyDarkness(partialTick);
        float flash = ClientEffects.skyFlash(partialTick);
        if (dark <= 0.0F && flash <= 0.0F) {
            return;
        }
        Vec3 c = cir.getReturnValue();
        double r = c.x + (0.035 - c.x) * dark;
        double g = c.y + (0.0 - c.y) * dark;
        double b = c.z + (0.07 - c.z) * dark;
        r += (1.0 - r) * flash;
        g += (0.95 - g) * flash;
        b += (1.0 - b) * flash;
        cir.setReturnValue(new Vec3(r, g, b));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void livingblackhole$darkenClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float dark = ClientEffects.skyDarkness(partialTick);
        if (dark <= 0.0F) {
            return;
        }
        Vec3 c = cir.getReturnValue();
        double k = 1.0 - dark * 0.85;
        cir.setReturnValue(new Vec3(c.x * k + 0.04 * dark, c.y * k, c.z * k + 0.08 * dark));
    }

    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void livingblackhole$revealStars(float partialTick, CallbackInfoReturnable<Float> cir) {
        float dark = ClientEffects.skyDarkness(partialTick);
        if (dark > 0.0F) {
            cir.setReturnValue(Math.max(cir.getReturnValueF(), dark * 0.95F));
        }
    }
}
