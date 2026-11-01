package com.ananttomar.livingblackhole.singularity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.UUID;

/**
 * The block content of a Compressed Chunk in a compact, network-friendly form: a palette of block
 * state ids and one packed int per stored block ({@code paletteIndex << 12 | x << 8 | y << 4 | z},
 * local coordinates 0-15). Shared by the item NBT, the effect packets and the client renderers.
 */
public final class StructureData {
    public final UUID id;
    /** Block state ids ({@link Block#getId}) indexed by palette slot. */
    public final int[] palette;
    public final int[] blocks;
    /** Biome the chunk was taken from (for grass, foliage and water tints); empty when unknown. */
    public final String biome;

    public StructureData(UUID id, int[] palette, int[] blocks, String biome) {
        this.id = id;
        this.palette = palette;
        this.blocks = blocks;
        this.biome = biome;
    }

    public static int local(int packed) {
        return packed & 0xFFF;
    }

    public static int x(int packed) {
        return (packed >> 8) & 0xF;
    }

    public static int y(int packed) {
        return (packed >> 4) & 0xF;
    }

    public static int z(int packed) {
        return packed & 0xF;
    }

    public BlockState state(int packed) {
        int index = packed >>> 12;
        return index < palette.length ? Block.stateById(palette[index]) : Blocks.AIR.defaultBlockState();
    }

    public boolean isEmpty() {
        return blocks.length == 0;
    }

    /** Local bounding box of the stored blocks: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] bounds() {
        return bounds(blocks);
    }

    public static int[] bounds(int[] blocks) {
        if (blocks.length == 0) {
            return new int[]{0, 0, 0, 15, 15, 15};
        }
        int[] b = {15, 15, 15, 0, 0, 0};
        for (int packed : blocks) {
            int x = x(packed);
            int y = y(packed);
            int z = z(packed);
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x);
            b[4] = Math.max(b[4], y);
            b[5] = Math.max(b[5], z);
        }
        return b;
    }

    /** Stable identity of a stored structure: the id written at capture, or a content hash for older chunks. */
    public static UUID idOf(CompoundTag data) {
        if (data.hasUUID("Id")) {
            return data.getUUID("Id");
        }
        int[] blocks = data.getIntArray("Blocks");
        return new UUID(Arrays.hashCode(blocks) * 31L + data.getList("Palette", Tag.TAG_COMPOUND).hashCode(), blocks.length);
    }

    public static StructureData fromTag(CompoundTag data) {
        ListTag paletteTag = data.getList("Palette", Tag.TAG_COMPOUND);
        int[] palette = new int[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = Block.getId(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i)));
        }
        return new StructureData(idOf(data), palette, data.getIntArray("Blocks"), data.getString("Biome"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeVarIntArray(palette);
        buf.writeVarIntArray(blocks);
        buf.writeUtf(biome, 256);
    }

    public static StructureData read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int[] palette = buf.readVarIntArray(4096);
        int[] blocks = buf.readVarIntArray(4096);
        String biome = buf.readUtf(256);
        return new StructureData(id, palette, blocks, biome);
    }
}
