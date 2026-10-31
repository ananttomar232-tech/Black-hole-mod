package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LivingBlackHole.MODID);

    public static final RegistryObject<SoundEvent> BLACK_HOLE_AMBIENT = register("black_hole_ambient");
    public static final RegistryObject<SoundEvent> BLACK_HOLE_BIRTH = register("black_hole_birth");
    public static final RegistryObject<SoundEvent> CONTAINMENT_BREACH = register("containment_breach");
    public static final RegistryObject<SoundEvent> ALARM = register("alarm");
    public static final RegistryObject<SoundEvent> WARNING_BEEP = register("warning_beep");
    public static final RegistryObject<SoundEvent> VACUUM = register("vacuum");
    public static final RegistryObject<SoundEvent> CHUNK_PLACE = register("chunk_place");
    public static final RegistryObject<SoundEvent> COLLAPSE_BUILDUP = register("collapse_buildup");
    public static final RegistryObject<SoundEvent> SINGULARITY_EXPLOSION = register("singularity_explosion");
    public static final RegistryObject<SoundEvent> BOMB_THROW = register("bomb_throw");
    public static final RegistryObject<SoundEvent> BOMB_CAPTURED = register("bomb_captured");
    public static final RegistryObject<SoundEvent> BOMB_FIZZLE = register("bomb_fizzle");
    public static final RegistryObject<SoundEvent> FRAGMENT_HUM = register("fragment_hum");
    public static final RegistryObject<SoundEvent> FRAGMENT_COLLECT = register("fragment_collect");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(LivingBlackHole.id(name)));
    }

    private ModSounds() {
    }
}
