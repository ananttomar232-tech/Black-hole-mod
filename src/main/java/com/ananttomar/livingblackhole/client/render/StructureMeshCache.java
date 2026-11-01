package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.singularity.ChunkCapture;
import com.ananttomar.livingblackhole.singularity.StructureData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Baked meshes of the structures the client has seen, keyed by structure id: the capture animation,
 * the miniature in the inventory and the grow-in animation all share one mesh per structure.
 * Least recently used meshes are freed beyond {@link #CAPACITY}; everything is dropped on resource
 * reload (the block atlas may have moved) and on logout.
 */
public final class StructureMeshCache {
    private static final int CAPACITY = 48;
    private static final LinkedHashMap<UUID, StructureMesh> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    /** Diagnostics for the CI self-test. */
    public static int builds;
    public static int lastVertices;

    private StructureMeshCache() {
    }

    @Nullable
    public static StructureMesh get(UUID id, Supplier<StructureData> data) {
        StructureMesh mesh = CACHE.get(id);
        if (mesh == null) {
            try {
                mesh = StructureMesh.build(data.get());
            } catch (RuntimeException e) {
                LivingBlackHole.LOGGER.error("Could not build the miniature of compressed chunk {}", id, e);
                return null;
            }
            builds++;
            lastVertices = mesh.vertices;
            CACHE.put(id, mesh);
            trim();
        }
        return mesh;
    }

    public static StructureMesh get(StructureData data) {
        return get(data.id, () -> data);
    }

    @Nullable
    public static StructureMesh forStack(ItemStack stack) {
        CompoundTag data = ChunkCapture.data(stack);
        if (data == null || data.getIntArray("Blocks").length == 0) {
            return null;
        }
        return get(StructureData.idOf(data), () -> StructureData.fromTag(data));
    }

    private static void trim() {
        Iterator<Map.Entry<UUID, StructureMesh>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > CAPACITY && it.hasNext()) {
            StructureMesh evicted = it.next().getValue();
            it.remove();
            evicted.close();
        }
    }

    public static void clear() {
        CACHE.values().forEach(StructureMesh::close);
        CACHE.clear();
    }
}
