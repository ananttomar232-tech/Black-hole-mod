package com.ananttomar.livingblackhole.client.render;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.singularity.StructureData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks of a Compressed Chunk baked into GPU vertex buffers, one per chunk render layer, with
 * the game's own block models, ambient occlusion, fluids and biome tints, exactly as the terrain
 * renderer would draw them. Hidden faces between stored blocks are culled. Blocks drawn by a block
 * entity renderer (chests, beds, signs...) are kept aside and drawn through their item renderer.
 *
 * <p>Coordinates are the structure's local block coordinates (0-16 on each axis).</p>
 */
public final class StructureMesh implements AutoCloseable {
    private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent()};
    /** Reused for every bake (always on the render thread); a fresh native buffer per bake would leak. */
    private static final BufferBuilder[] SHARED = new BufferBuilder[LAYERS.length];

    private record AnimatedBlock(int x, int y, int z, BlockState state) {
    }

    private final VertexBuffer[] buffers;
    private final List<AnimatedBlock> animated;
    public final int vertices;
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;
    private boolean closed;

    private StructureMesh(VertexBuffer[] buffers, List<AnimatedBlock> animated, int vertices, int[] bounds) {
        this.buffers = buffers;
        this.animated = animated;
        this.vertices = vertices;
        this.minX = bounds[0];
        this.minY = bounds[1];
        this.minZ = bounds[2];
        this.maxX = bounds[3] + 1;
        this.maxY = bounds[4] + 1;
        this.maxZ = bounds[5] + 1;
    }

    public boolean isEmpty() {
        return vertices == 0 && animated.isEmpty();
    }

    public float centerX() {
        return (minX + maxX) * 0.5F;
    }

    public float centerY() {
        return (minY + maxY) * 0.5F;
    }

    public float centerZ() {
        return (minZ + maxZ) * 0.5F;
    }

    /** Largest extent of the stored blocks, in blocks. */
    public float span() {
        return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
    }

    // ------------------------------------------------------------------ building

    public static StructureMesh build(StructureData data) {
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        View view = new View(data, lookupBiome(mc, data.biome));
        boolean[] begun = new boolean[LAYERS.length];
        List<AnimatedBlock> animated = new ArrayList<>();
        PoseStack ps = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int packed : data.blocks) {
            int x = StructureData.x(packed);
            int y = StructureData.y(packed);
            int z = StructureData.z(packed);
            BlockState state = view.at(x, y, z);
            if (state.isAir()) {
                continue;
            }
            pos.set(x, y, z);
            try {
                RenderShape shape = state.getRenderShape();
                if (shape == RenderShape.MODEL) {
                    BakedModel model = dispatcher.getBlockModel(state);
                    long seed = state.getSeed(pos);
                    random.setSeed(seed);
                    for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                        ps.pushPose();
                        ps.translate(x, y, z);
                        random.setSeed(seed);
                        dispatcher.renderBatched(state, pos, view, ps, builder(begun, layer(type)), true, random, ModelData.EMPTY, type);
                        ps.popPose();
                    }
                } else if (shape == RenderShape.ENTITYBLOCK_ANIMATED) {
                    animated.add(new AnimatedBlock(x, y, z, state));
                }
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    // Liquid vertices are emitted at (pos & 15), which is exactly our local space.
                    dispatcher.renderLiquid(pos, view, builder(begun, layer(ItemBlockRenderTypes.getRenderLayer(fluid))), state, fluid);
                }
            } catch (RuntimeException e) {
                LivingBlackHole.LOGGER.debug("Skipping {} in a compressed chunk miniature", state, e);
            }
        }

        VertexBuffer[] buffers = new VertexBuffer[LAYERS.length];
        int vertices = 0;
        try {
            for (int i = 0; i < LAYERS.length; i++) {
                if (!begun[i]) {
                    continue;
                }
                begun[i] = false;
                BufferBuilder.RenderedBuffer rendered = SHARED[i].end();
                int count = rendered.drawState().vertexCount();
                if (count == 0) {
                    rendered.release();
                    continue;
                }
                vertices += count;
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(rendered);
                VertexBuffer.unbind();
                buffers[i] = vb;
            }
        } finally {
            // Never leave a shared builder mid-batch, or the next bake would fail.
            for (int i = 0; i < LAYERS.length; i++) {
                if (begun[i]) {
                    SHARED[i].end().release();
                }
            }
        }
        return new StructureMesh(buffers, animated, vertices, data.bounds());
    }

    private static BufferBuilder builder(boolean[] begun, int layer) {
        if (SHARED[layer] == null) {
            SHARED[layer] = new BufferBuilder(1 << 18);
        }
        if (!begun[layer]) {
            begun[layer] = true;
            SHARED[layer].begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return SHARED[layer];
    }

    private static int layer(RenderType type) {
        if (type == RenderType.solid()) {
            return 0;
        }
        if (type == RenderType.cutoutMipped()) {
            return 1;
        }
        if (type == RenderType.translucent() || type == RenderType.tripwire()) {
            return 3;
        }
        return 2;
    }

    @Nullable
    private static Biome lookupBiome(Minecraft mc, String key) {
        ResourceLocation id = key.isEmpty() ? null : ResourceLocation.tryParse(key);
        if (id == null || mc.level == null) {
            return null;
        }
        return mc.level.registryAccess().registry(Registries.BIOME).flatMap(r -> r.getOptional(id)).orElse(null);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the structure with the current pose (local block units). {@code r, g, b} tint it;
     * {@code light} only affects the block-entity blocks, the baked mesh is pre-lit.
     */
    public void draw(PoseStack ps, MultiBufferSource buffers, int light, float r, float g, float b) {
        if (closed) {
            return;
        }
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ps.last().pose());
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        RenderSystem.setShaderColor(r, g, b, 1.0F);
        for (int i = 0; i < LAYERS.length; i++) {
            VertexBuffer vb = this.buffers[i];
            if (vb == null) {
                continue;
            }
            RenderType type = LAYERS[i];
            type.setupRenderState();
            if (Minecraft.useShaderTransparency()) {
                // Fabulous graphics: draw into the main target, not the level's translucency layer
                // (which is never composited for items in the GUI or in hand).
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            }
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
                }
                vb.bind();
                vb.drawWithShader(modelView, projection, shader);
            }
            type.clearRenderState();
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (!animated.isEmpty()) {
            BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
            for (AnimatedBlock a : animated) {
                ps.pushPose();
                ps.translate(a.x(), a.y(), a.z());
                dispatcher.renderSingleBlock(a.state(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (VertexBuffer vb : buffers) {
            if (vb != null) {
                vb.close();
            }
        }
    }

    // ------------------------------------------------------------------ virtual level

    /** The stored blocks as a tiny stand-alone world, fully lit, tinted by the biome they came from. */
    private static final class View implements BlockAndTintGetter {
        private final BlockState[] states = new BlockState[4096];
        @Nullable
        private final Biome biome;

        View(StructureData data, @Nullable Biome biome) {
            this.biome = biome;
            for (int packed : data.blocks) {
                states[StructureData.local(packed)] = data.state(packed);
            }
        }

        BlockState at(int x, int y, int z) {
            if ((x | y | z) < 0 || x > 15 || y > 15 || z > 15) {
                return Blocks.AIR.defaultBlockState();
            }
            BlockState s = states[(x << 8) | (y << 4) | z];
            return s != null ? s : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return at(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            if (!shade) {
                return 1.0F;
            }
            return switch (direction) {
                case DOWN -> 0.5F;
                case UP -> 1.0F;
                case NORTH, SOUTH -> 0.8F;
                case WEST, EAST -> 0.6F;
            };
        }

        @Override
        public LevelLightEngine getLightEngine() {
            // Brightness is answered directly below; this is only a fallback for unusual callers.
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null ? mc.level.getLightEngine() : null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            if (biome != null) {
                return resolver.getColor(biome, pos.getX(), pos.getZ());
            }
            if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) {
                return GrassColor.get(0.8, 0.4);
            }
            if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
                return FoliageColor.getDefaultColor();
            }
            if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
                return 0x3F76E4;
            }
            return 0xFFFFFF;
        }
    }
}
