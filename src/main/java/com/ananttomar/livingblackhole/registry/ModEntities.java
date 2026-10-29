package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.entity.AntiGravityBombEntity;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.entity.GravitationalDebrisEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LivingBlackHole.MODID);

    public static final RegistryObject<EntityType<BlackHoleEntity>> BLACK_HOLE = ENTITIES.register("black_hole",
            () -> EntityType.Builder.<BlackHoleEntity>of(BlackHoleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(32)
                    .updateInterval(10)
                    .fireImmune()
                    .build(LivingBlackHole.MODID + ":black_hole"));

    public static final RegistryObject<EntityType<GravitationalDebrisEntity>> GRAVITATIONAL_DEBRIS = ENTITIES.register("gravitational_debris",
            () -> EntityType.Builder.<GravitationalDebrisEntity>of(GravitationalDebrisEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSave()
                    .fireImmune()
                    .build(LivingBlackHole.MODID + ":gravitational_debris"));

    public static final RegistryObject<EntityType<AntiGravityBombEntity>> ANTI_GRAVITY_BOMB = ENTITIES.register("anti_gravity_bomb",
            () -> EntityType.Builder.<AntiGravityBombEntity>of(AntiGravityBombEntity::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .fireImmune()
                    .build(LivingBlackHole.MODID + ":anti_gravity_bomb"));

    private ModEntities() {
    }
}
