package com.ananttomar.livingblackhole.client;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.hud.SingularityHudOverlay;
import com.ananttomar.livingblackhole.client.particle.ModParticleProviders;
import com.ananttomar.livingblackhole.client.render.AntiGravityBombRenderer;
import com.ananttomar.livingblackhole.client.render.BlackHoleRenderer;
import com.ananttomar.livingblackhole.client.render.CoreFragmentRenderer;
import com.ananttomar.livingblackhole.client.render.GravitationalDebrisRenderer;
import com.ananttomar.livingblackhole.client.render.LensingEffect;
import com.ananttomar.livingblackhole.item.PocketBlackHoleItem;
import com.ananttomar.livingblackhole.registry.ModBlockEntities;
import com.ananttomar.livingblackhole.registry.ModEntities;
import com.ananttomar.livingblackhole.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = LivingBlackHole.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.POCKET_BLACK_HOLE.get(), LivingBlackHole.id("instability"),
                    (stack, level, entity, seed) -> PocketBlackHoleItem.instability(stack));
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BLACK_HOLE.get(), BlackHoleRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAVITATIONAL_DEBRIS.get(), GravitationalDebrisRenderer::new);
        event.registerEntityRenderer(ModEntities.ANTI_GRAVITY_BOMB.get(), AntiGravityBombRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CORE_FRAGMENT.get(), CoreFragmentRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        ModParticleProviders.register(event);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("singularity_hud", SingularityHudOverlay.OVERLAY);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> LensingEffect.reset());
    }
}
