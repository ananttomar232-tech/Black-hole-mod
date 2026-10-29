package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> SINGULARITY = ResourceKey.create(Registries.DAMAGE_TYPE, LivingBlackHole.id("singularity"));

    public static DamageSource singularity(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SINGULARITY));
    }

    private ModDamageTypes() {
    }
}
