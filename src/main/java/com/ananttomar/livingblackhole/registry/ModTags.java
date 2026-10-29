package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocks no singularity (pocket or escaped) can swallow. */
    public static final TagKey<Block> SINGULARITY_IMMUNE = TagKey.create(Registries.BLOCK, LivingBlackHole.id("singularity_immune"));
    /** Entity types the black hole will never drag or devour. */
    public static final TagKey<EntityType<?>> SINGULARITY_IMMUNE_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, LivingBlackHole.id("singularity_immune"));

    private ModTags() {
    }
}
