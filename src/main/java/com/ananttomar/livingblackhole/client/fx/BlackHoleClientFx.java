package com.ananttomar.livingblackhole.client.fx;

import com.ananttomar.livingblackhole.client.particle.ParticleFx;
import com.ananttomar.livingblackhole.client.sound.BlackHoleLoopSound;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Client tick hook of the black hole entity: particles and its looping roar. */
public final class BlackHoleClientFx {
    private static final Map<Integer, BlackHoleLoopSound> LOOPS = new HashMap<>();

    private BlackHoleClientFx() {
    }

    public static void tick(BlackHoleEntity bh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(bh.level() instanceof ClientLevel level)) {
            return;
        }
        double distance = mc.player.distanceTo(bh);
        if (distance < 400.0) {
            ParticleFx.blackHole(level, bh, distance);
        }

        if (bh.getState() < BlackHoleEntity.STATE_SHOCKWAVE) {
            BlackHoleLoopSound loop = LOOPS.get(bh.getId());
            boolean dead = loop == null || loop.isStopped() || loop.blackHole() != bh;
            if (!dead && bh.tickCount % 40 == 0 && !mc.getSoundManager().isActive(loop)) {
                // The sound engine dropped it (reload, channel pressure): start a fresh one.
                mc.getSoundManager().stop(loop);
                dead = true;
            }
            if (dead) {
                loop = new BlackHoleLoopSound(bh);
                LOOPS.put(bh.getId(), loop);
                mc.getSoundManager().play(loop);
            }
        }
    }

    /** Forget sounds of black holes that no longer exist. */
    public static void cleanup() {
        Iterator<Map.Entry<Integer, BlackHoleLoopSound>> it = LOOPS.entrySet().iterator();
        while (it.hasNext()) {
            BlackHoleLoopSound loop = it.next().getValue();
            if (loop.isStopped() || loop.blackHole().isRemoved()) {
                it.remove();
            }
        }
    }

    public static void clear() {
        LOOPS.clear();
    }
}
