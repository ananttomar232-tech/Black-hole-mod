package com.ananttomar.livingblackhole.singularity;

import com.ananttomar.livingblackhole.network.ChunkFxPacket;
import com.ananttomar.livingblackhole.registry.ModItems;
import com.ananttomar.livingblackhole.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lifts a 16x16x16 region of the world into item NBT and puts it back.
 *
 * <p>Storage layout under {@value #TAG} on a Compressed Chunk stack:</p>
 * <ul>
 *     <li>{@code Palette} - list of serialized block states</li>
 *     <li>{@code Blocks} - int array, one entry per stored block: {@code paletteIndex << 12 | x << 8 | y << 4 | z}</li>
 *     <li>{@code BlockEntities} - list of {@code {P: packedPos, Nbt: fullBlockEntityData}} (chest contents, signs...)</li>
 *     <li>{@code Count}, {@code Origin}, {@code Dim}, {@code Summary} - metadata for the tooltip</li>
 * </ul>
 * Positions are relative to the region corner; the region is anchored so the clicked block sits at
 * offset (8, 8, 8), which makes capture and placement exact inverses of each other.
 */
public final class ChunkCapture {
    public static final String TAG = "LBHChunk";
    public static final int SIZE = 16;
    public static final int ANCHOR = 8;
    /** No neighbour shape updates and no drops while blocks are lifted or set down in bulk. */
    private static final int BULK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public record Captured(ItemStack chunk, BlockPos min, List<ChunkFxPacket.Entry> fx) {
    }

    public record Placed(int count, BlockPos min, List<ChunkFxPacket.Entry> fx) {
    }

    private ChunkCapture() {
    }

    public static BlockPos regionMin(BlockPos anchor) {
        return anchor.offset(-ANCHOR, -ANCHOR, -ANCHOR);
    }

    private static int pack(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }

    private static boolean untouchable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(ModTags.SINGULARITY_IMMUNE) || state.getDestroySpeed(level, pos) < 0.0F;
    }

    /** True when the player is allowed to remove or overwrite this block (spawn protection, claim mods...). */
    private static boolean permitted(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        return state.isAir() || !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
    }

    // ------------------------------------------------------------------ capture

    /** Swallows the region around {@code target}. Returns null when nothing in it could be taken. */
    @Nullable
    public static Captured capture(ServerLevel level, ServerPlayer player, BlockPos target) {
        BlockPos min = regionMin(target);
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        List<Integer> blocks = new ArrayList<>();
        List<BlockPos> taken = new ArrayList<>();
        ListTag blockEntities = new ListTag();
        Map<Block, Integer> census = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    cursor.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    if (level.isOutsideBuildHeight(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || untouchable(level, cursor, state) || !permitted(level, player, cursor, state)) {
                        continue;
                    }
                    int index = palette.computeIfAbsent(state, s -> palette.size());
                    int packed = pack(x, y, z);
                    blocks.add((index << 12) | packed);
                    taken.add(cursor.immutable());
                    census.merge(state.getBlock(), 1, Integer::sum);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be != null) {
                        CompoundTag entry = new CompoundTag();
                        entry.putInt("P", packed);
                        entry.put("Nbt", be.saveWithFullMetadata());
                        blockEntities.add(entry);
                    }
                }
            }
        }
        if (taken.isEmpty()) {
            return null;
        }

        List<ChunkFxPacket.Entry> fx = sampleFx(level, taken);

        // Empty containers first so nothing spills, then lift every block without cascades.
        for (BlockPos pos : taken) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                Clearable.tryClear(be);
            }
        }
        for (BlockPos pos : taken) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), BULK_FLAGS);
        }
        // Let the surrounding world react at the seams (sand falls, water flows in).
        for (BlockPos pos : taken) {
            if (onShell(pos, min)) {
                level.blockUpdated(pos, Blocks.AIR);
            }
        }

        ListTag paletteTag = new ListTag();
        for (BlockState state : palette.keySet()) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        int[] packedBlocks = blocks.stream().mapToInt(Integer::intValue).toArray();
        CompoundTag data = new CompoundTag();
        data.put("Palette", paletteTag);
        data.put("Blocks", new IntArrayTag(packedBlocks));
        data.put("BlockEntities", blockEntities);
        data.putInt("Count", packedBlocks.length);
        data.putIntArray("Origin", new int[]{target.getX(), target.getY(), target.getZ()});
        data.putString("Dim", level.dimension().location().toString());
        data.put("Summary", summary(census));

        ItemStack chunk = new ItemStack(ModItems.COMPRESSED_CHUNK.get());
        chunk.getOrCreateTag().put(TAG, data);
        return new Captured(chunk, min, fx);
    }

    private static boolean onShell(BlockPos pos, BlockPos min) {
        int x = pos.getX() - min.getX();
        int y = pos.getY() - min.getY();
        int z = pos.getZ() - min.getZ();
        return x == 0 || y == 0 || z == 0 || x == SIZE - 1 || y == SIZE - 1 || z == SIZE - 1;
    }

    private static ListTag summary(Map<Block, Integer> census) {
        List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(census.entrySet());
        sorted.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            CompoundTag t = new CompoundTag();
            t.putString("Block", BuiltInRegistries.BLOCK.getKey(sorted.get(i).getKey()).toString());
            t.putInt("Count", sorted.get(i).getValue());
            list.add(t);
        }
        return list;
    }

    private static List<ChunkFxPacket.Entry> sampleFx(ServerLevel level, List<BlockPos> positions) {
        List<ChunkFxPacket.Entry> fx = new ArrayList<>();
        int step = Math.max(1, positions.size() / ChunkFxPacket.MAX_ENTRIES);
        int offset = level.random.nextInt(step);
        for (int i = offset; i < positions.size() && fx.size() < ChunkFxPacket.MAX_ENTRIES; i += step) {
            BlockPos pos = positions.get(i);
            fx.add(new ChunkFxPacket.Entry(pos, Block.getId(level.getBlockState(pos))));
        }
        return fx;
    }

    // ------------------------------------------------------------------ placement

    public static boolean hasStructure(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG, Tag.TAG_COMPOUND);
    }

    @Nullable
    public static CompoundTag data(ItemStack stack) {
        return hasStructure(stack) ? stack.getTag().getCompound(TAG) : null;
    }

    /** Sets the stored structure down so the original clicked block lands on {@code anchor}. */
    public static Placed place(ServerLevel level, ServerPlayer player, ItemStack chunk, BlockPos anchor) {
        CompoundTag data = data(chunk);
        BlockPos min = regionMin(anchor);
        if (data == null) {
            return new Placed(0, min, List.of());
        }
        ListTag paletteTag = data.getList("Palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
        }
        Map<Integer, CompoundTag> blockEntities = new HashMap<>();
        ListTag beList = data.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < beList.size(); i++) {
            CompoundTag e = beList.getCompound(i);
            blockEntities.put(e.getInt("P"), e.getCompound("Nbt"));
        }

        List<BlockPos> placedPositions = new ArrayList<>();
        List<BlockState> placedStates = new ArrayList<>();
        List<CompoundTag> placedEntities = new ArrayList<>();
        for (int packed : data.getIntArray("Blocks")) {
            int index = packed >>> 12;
            if (index >= palette.length) {
                continue;
            }
            int local = packed & 0xFFF;
            BlockPos pos = min.offset((local >> 8) & 0xF, (local >> 4) & 0xF, local & 0xF);
            if (level.isOutsideBuildHeight(pos)) {
                continue;
            }
            BlockState existing = level.getBlockState(pos);
            if (untouchable(level, pos, existing) || !permitted(level, player, pos, existing)) {
                continue;
            }
            placedPositions.add(pos);
            placedStates.add(palette[index]);
            placedEntities.add(blockEntities.get(local));
        }
        if (placedPositions.isEmpty()) {
            return new Placed(0, min, List.of());
        }

        for (int i = 0; i < placedPositions.size(); i++) {
            level.setBlock(placedPositions.get(i), placedStates.get(i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (int i = 0; i < placedPositions.size(); i++) {
            CompoundTag nbt = placedEntities.get(i);
            if (nbt == null) {
                continue;
            }
            BlockPos pos = placedPositions.get(i);
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                CompoundTag copy = nbt.copy();
                copy.putInt("x", pos.getX());
                copy.putInt("y", pos.getY());
                copy.putInt("z", pos.getZ());
                be.load(copy);
                be.setChanged();
                level.sendBlockUpdated(pos, placedStates.get(i), placedStates.get(i), Block.UPDATE_ALL);
            }
        }
        // Wake the structure up: redstone, fluids and connections to the surrounding terrain.
        for (int i = 0; i < placedPositions.size(); i++) {
            BlockPos pos = placedPositions.get(i);
            level.blockUpdated(pos, placedStates.get(i).getBlock());
            if (onShell(pos, min)) {
                placedStates.get(i).updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
            }
        }
        liftEntitiesOutOfBlocks(level, min);

        return new Placed(placedPositions.size(), min, sampleFx(level, placedPositions));
    }

    /** Anything (players included) the structure was set down on top of is lifted clear instead of suffocating. */
    private static void liftEntitiesOutOfBlocks(ServerLevel level, BlockPos min) {
        AABB box = new AABB(min, min.offset(SIZE, SIZE, SIZE));
        for (Entity e : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (level.noCollision(e)) {
                continue;
            }
            double x = e.getX();
            double z = e.getZ();
            for (int dy = 1; dy <= SIZE + 4; dy++) {
                double y = e.getY() + dy;
                if (level.noCollision(e, e.getBoundingBox().move(0.0, dy, 0.0))) {
                    e.teleportTo(x, y, z);
                    break;
                }
            }
        }
    }
}
