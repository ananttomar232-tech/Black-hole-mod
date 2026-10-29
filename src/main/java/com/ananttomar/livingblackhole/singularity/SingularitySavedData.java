package com.ananttomar.livingblackhole.singularity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-wide registry of escaped singularities, stored with the overworld. */
public final class SingularitySavedData extends SavedData {
    private static final String NAME = "livingblackhole_singularities";

    private final Map<UUID, SingularityEvent> events = new LinkedHashMap<>();

    public static SingularitySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SingularitySavedData::load, SingularitySavedData::new, NAME);
    }

    public static SingularitySavedData load(CompoundTag tag) {
        SingularitySavedData data = new SingularitySavedData();
        ListTag list = tag.getList("Events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            SingularityEvent ev = SingularityEvent.load(list.getCompound(i));
            data.events.put(ev.id, ev);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SingularityEvent ev : events.values()) {
            list.add(ev.save());
        }
        tag.put("Events", list);
        return tag;
    }

    public Collection<SingularityEvent> all() {
        return Collections.unmodifiableCollection(events.values());
    }

    public List<SingularityEvent> snapshot() {
        return new ArrayList<>(events.values());
    }

    public void add(SingularityEvent ev) {
        events.put(ev.id, ev);
        setDirty();
    }

    @Nullable
    public SingularityEvent remove(UUID id) {
        SingularityEvent ev = events.remove(id);
        if (ev != null) {
            setDirty();
        }
        return ev;
    }

    @Nullable
    public SingularityEvent get(UUID id) {
        return events.get(id);
    }

    @Nullable
    public SingularityEvent byBlackHole(UUID blackHoleId) {
        for (SingularityEvent ev : events.values()) {
            if (ev.blackHoleId.equals(blackHoleId)) {
                return ev;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public void clear() {
        events.clear();
        setDirty();
    }
}
