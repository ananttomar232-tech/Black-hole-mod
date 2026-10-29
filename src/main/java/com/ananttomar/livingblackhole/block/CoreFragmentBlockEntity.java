package com.ananttomar.livingblackhole.block;

import com.ananttomar.livingblackhole.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Carries the sky beam renderer of a Core Fragment. */
public class CoreFragmentBlockEntity extends BlockEntity {
    public CoreFragmentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE_FRAGMENT.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
