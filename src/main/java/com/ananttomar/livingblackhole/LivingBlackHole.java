package com.ananttomar.livingblackhole;

import com.ananttomar.livingblackhole.network.ModNetwork;
import com.ananttomar.livingblackhole.registry.ModBlockEntities;
import com.ananttomar.livingblackhole.registry.ModBlocks;
import com.ananttomar.livingblackhole.registry.ModCreativeTabs;
import com.ananttomar.livingblackhole.registry.ModEntities;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Living Black Hole - a cinematic singularity event for Forge 1.20.1.
 *
 * <p>Loop: feed the Pocket Black Hole 500 blocks, it breaks containment, a living black hole
 * starts eating the world, three Core Fragments crash down within 300 blocks, and the player
 * crafts an Anti-Gravity Bomb and throws it into the event horizon to collapse the singularity.</p>
 */
@Mod(LivingBlackHole.MODID)
public final class LivingBlackHole {
    public static final String MODID = "livingblackhole";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingBlackHole() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            // Black holes keep their own chunk ticking so the world keeps being devoured while
            // the player travels to the fragments. Tickets are owned by the entity and released
            // when the singularity collapses, so nothing needs re-validating on load.
            ForgeChunkManager.setForcedChunkLoadingCallback(MODID, (level, ticketHelper) -> { });
        });
        LOGGER.info("Living Black Hole initialised - containment field online.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
