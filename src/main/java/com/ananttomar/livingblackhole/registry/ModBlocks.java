package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.block.CoreFragmentBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LivingBlackHole.MODID);

    public static final RegistryObject<Block> CORE_FRAGMENT = BLOCKS.register("core_fragment",
            () -> new CoreFragmentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5F, 1200.0F)
                    .lightLevel(state -> 15)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .emissiveRendering((state, level, pos) -> true)
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    private ModBlocks() {
    }
}
