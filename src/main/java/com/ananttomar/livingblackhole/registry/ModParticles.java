package com.ananttomar.livingblackhole.registry;

import com.ananttomar.livingblackhole.LivingBlackHole;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LivingBlackHole.MODID);

    /** Keplerian plasma orbiting inside the accretion disk. */
    public static final RegistryObject<SimpleParticleType> ACCRETION = register("accretion");
    /** Dust and rubble streaking radially into the hole. */
    public static final RegistryObject<SimpleParticleType> DEBRIS_STREAK = register("debris_streak");
    /** Hot sparks flaring at the photon sphere. */
    public static final RegistryObject<SimpleParticleType> HORIZON_SPARK = register("horizon_spark");
    /** Matter flowing from consumed blocks into the Pocket Black Hole. */
    public static final RegistryObject<SimpleParticleType> MATTER_STREAM = register("matter_stream");
    /** Upward-falling sparks shed by the Anti-Gravity Bomb. */
    public static final RegistryObject<SimpleParticleType> ANTIGRAV_SPARK = register("antigrav_spark");
    /** Crystalline motes orbiting a Core Fragment. Velocity arguments carry the orbit centre. */
    public static final RegistryObject<SimpleParticleType> CORE_GLINT = register("core_glint");
    /** Shockwave sparks hurled outward by the collapse. */
    public static final RegistryObject<SimpleParticleType> SHOCK_SPARK = register("shock_spark");
    /** Relativistic jet plasma. */
    public static final RegistryObject<SimpleParticleType> JET_PLASMA = register("jet_plasma");

    private static RegistryObject<SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {
    }
}
