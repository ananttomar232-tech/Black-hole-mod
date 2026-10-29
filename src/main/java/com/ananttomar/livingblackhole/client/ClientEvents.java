package com.ananttomar.livingblackhole.client;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.fx.BlackHoleClientFx;
import com.ananttomar.livingblackhole.client.render.BeamEffects;
import com.ananttomar.livingblackhole.client.render.LensingEffect;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = LivingBlackHole.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        ClientEffects.tick(mc);
        BeamEffects.tick();
        if (ClientEffects.ticks() % 100 == 0) {
            BlackHoleClientFx.cleanup();
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            BeamEffects.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            LensingEffect.apply(new Matrix4f(event.getPoseStack().last().pose()), event.getProjectionMatrix(), event.getCamera(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        float shake = ClientEffects.shakeIntensity(pt);
        if (shake <= 0.001F) {
            return;
        }
        float t = ClientEffects.ticks() + pt;
        event.setYaw(event.getYaw() + ClientEffects.noise(t * 1.3F, 1.1F) * shake * 1.6F);
        event.setPitch(event.getPitch() + ClientEffects.noise(t * 1.5F, 4.7F) * shake * 1.3F);
        event.setRoll(event.getRoll() + ClientEffects.noise(t * 0.9F, 8.3F) * shake * 1.1F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        float pt = (float) event.getPartialTick();
        double fov = event.getFOV() * (1.0 + 0.07 * ClientEffects.proximity(pt));
        Player player = Minecraft.getInstance().player;
        if (player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof PocketBlackHoleItem) {
            float charge = Mth.clamp(player.getTicksUsingItem() / (float) PocketBlackHoleItem.FULL_CHARGE_TICKS, 0.0F, 1.0F);
            fov *= 1.0 - 0.12 * charge * charge;
        }
        event.setFOV(fov);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float pt = (float) event.getPartialTick();
        float dark = ClientEffects.skyDarkness(pt);
        float flash = ClientEffects.skyFlash(pt);
        if (dark <= 0.0F && flash <= 0.0F) {
            return;
        }
        float k = dark * 0.85F;
        float r = Mth.lerp(k, event.getRed(), 0.05F);
        float g = Mth.lerp(k, event.getGreen(), 0.0F);
        float b = Mth.lerp(k, event.getBlue(), 0.09F);
        event.setRed(Mth.lerp(flash, r, 1.0F));
        event.setGreen(Mth.lerp(flash, g, 0.96F));
        event.setBlue(Mth.lerp(flash, b, 1.0F));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSingularityState.clear();
        ClientEffects.reset();
        BeamEffects.clear();
        BlackHoleClientFx.clear();
    }
}
