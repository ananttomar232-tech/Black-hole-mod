package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.block.CoreFragmentBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LivingBlackHole.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CoreFragmentBlockEntity>> CORE_FRAGMENT = BLOCK_ENTITIES.register("core_fragment",
            () -> BlockEntityType.Builder.of(CoreFragmentBlockEntity::new, ModBlocks.CORE_FRAGMENT.get()).build(null));

    private ModBlockEntities() {
    }
}
