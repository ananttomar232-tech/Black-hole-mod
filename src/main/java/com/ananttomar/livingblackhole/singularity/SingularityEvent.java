package com.ananttomar.livingblackhole.singularity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One escaped singularity and the fragment hunt attached to it. */
public final class SingularityEvent {
    public static final int FRAGMENTS = 3;

    public static final int PHASE_ACTIVE = 0;
    public static final int PHASE_COLLAPSING = 1;

    public final UUID id;
    public final UUID blackHoleId;
    public final ResourceKey<Level> dimension;
    public final Vec3 center;
    public final BlockPos[] fragments = new BlockPos[FRAGMENTS];
    public final boolean[] collected = new boolean[FRAGMENTS];
    @Nullable
    public final UUID owner;
    public final long startGameTime;
    public int phase = PHASE_ACTIVE;
    public int mass;
    public float reach;
    /** Server bookkeeping: consecutive checks where the black hole could not be found. */
    public transient int missingChecks;

    public SingularityEvent(UUID id, UUID blackHoleId, ResourceKey<Level> dimension, Vec3 center, @Nullable UUID owner, long startGameTime) {
        this.id = id;
        this.blackHoleId = blackHoleId;
        this.dimension = dimension;
        this.center = center;
        this.owner = owner;
        this.startGameTime = startGameTime;
    }

    public int collectedCount() {
        int n = 0;
        for (boolean b : collected) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    public int fragmentIndex(BlockPos pos) {
        for (int i = 0; i < FRAGMENTS; i++) {
            if (pos.equals(fragments[i])) {
                return i;
            }
        }
        return -1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("BlackHole", blackHoleId);
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", center.x);
        tag.putDouble("Y", center.y);
        tag.putDouble("Z", center.z);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong("Start", startGameTime);
        tag.putInt("Phase", phase);
        tag.putInt("Mass", mass);
        tag.putFloat("Reach", reach);
        ListTag list = new ListTag();
        for (int i = 0; i < FRAGMENTS; i++) {
            CompoundTag f = NbtUtils.writeBlockPos(fragments[i] == null ? BlockPos.ZERO : fragments[i]);
            f.putBoolean("Collected", collected[i]);
            list.add(f);
        }
        tag.put("Fragments", list);
        return tag;
    }

    public static SingularityEvent load(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dimension")));
        SingularityEvent ev = new SingularityEvent(tag.getUUID("Id"), tag.getUUID("BlackHole"), dim,
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getLong("Start"));
        ev.phase = tag.getInt("Phase");
        ev.mass = tag.getInt("Mass");
        ev.reach = tag.getFloat("Reach");
        ListTag list = tag.getList("Fragments", Tag.TAG_COMPOUND);
        for (int i = 0; i < FRAGMENTS && i < list.size(); i++) {
            CompoundTag f = list.getCompound(i);
            ev.fragments[i] = NbtUtils.readBlockPos(f);
            ev.collected[i] = f.getBoolean("Collected");
        }
        for (int i = 0; i < FRAGMENTS; i++) {
            if (ev.fragments[i] == null) {
                ev.fragments[i] = BlockPos.containing(ev.center);
            }
        }
        return ev;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeResourceLocation(dimension.location());
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        for (int i = 0; i < FRAGMENTS; i++) {
            buf.writeBlockPos(fragments[i]);
            buf.writeBoolean(collected[i]);
        }
        buf.writeVarInt(phase);
        buf.writeVarInt(mass);
        buf.writeFloat(reach);
        buf.writeLong(startGameTime);
    }

    public static SingularityEvent read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        BlockPos[] frags = new BlockPos[FRAGMENTS];
        boolean[] got = new boolean[FRAGMENTS];
        for (int i = 0; i < FRAGMENTS; i++) {
            frags[i] = buf.readBlockPos();
            got[i] = buf.readBoolean();
        }
        int phase = buf.readVarInt();
        int mass = buf.readVarInt();
        float reach = buf.readFloat();
        long start = buf.readLong();
        SingularityEvent ev = new SingularityEvent(id, id, dim, center, null, start);
        System.arraycopy(frags, 0, ev.fragments, 0, FRAGMENTS);
        System.arraycopy(got, 0, ev.collected, 0, FRAGMENTS);
        ev.phase = phase;
        ev.mass = mass;
        ev.reach = reach;
        return ev;
    }
}
