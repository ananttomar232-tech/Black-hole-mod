package com.ananttomar.livingblackhole.client.sound;

import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** The black hole's endless sub-bass roar, following the entity and tracking its state. */
public class BlackHoleLoopSound extends AbstractTickableSoundInstance {
    private final BlackHoleEntity blackHole;

    public BlackHoleLoopSound(BlackHoleEntity blackHole) {
        super(ModSounds.BLACK_HOLE_AMBIENT.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.blackHole = blackHole;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 1.0F;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = blackHole.getX();
        this.y = blackHole.getY();
        this.z = blackHole.getZ();
    }

    public BlackHoleEntity blackHole() {
        return blackHole;
    }

    @Override
    public void tick() {
        if (blackHole.isRemoved() || blackHole.getState() >= BlackHoleEntity.STATE_SHOCKWAVE) {
            this.stop();
            return;
        }
        this.x = blackHole.getX();
        this.y = blackHole.getY();
        this.z = blackHole.getZ();
        float scale = Mth.clamp(blackHole.getVisualScale(0.0F), 0.0F, 1.2F);
        float instability = blackHole.getInstability(0.0F);
        // LINEAR attenuation reaches 16 blocks per unit of volume: ~110 blocks at full size.
        float target = 7.0F * scale + instability * 2.0F;
        this.volume = Mth.lerp(0.1F, this.volume, target);
        this.pitch = Mth.lerp(0.1F, this.pitch, 0.8F + scale * 0.2F + instability * 0.9F);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
