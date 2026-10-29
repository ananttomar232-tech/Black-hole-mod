package com.ananttomar.livingblackhole.block;

import com.ananttomar.livingblackhole.registry.ModParticles;
import com.ananttomar.livingblackhole.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A shard of the singularity's own lattice, flung out when containment fails. It hums, glows,
 * throws a beacon into the sky so it can be found from far away, and drops itself when mined
 * with anything - losing it would make the black hole unstoppable.
 */
public class CoreFragmentBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.5, 0.0, 2.5, 13.5, 2.5, 13.5),
            Block.box(5.0, 2.0, 5.0, 11.0, 15.0, 11.0),
            Block.box(3.0, 2.0, 3.0, 13.0, 9.0, 13.0));

    public CoreFragmentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreFragmentBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.55;
        double cz = pos.getZ() + 0.5;
        // Orbiting glints: the provider reads the velocity arguments as the orbit centre.
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 0.55 + random.nextDouble() * 0.45;
            level.addParticle(ModParticles.CORE_GLINT.get(),
                    cx + Math.cos(a) * r, cy + (random.nextDouble() - 0.3) * 0.9, cz + Math.sin(a) * r,
                    cx, cy, cz);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.ANTIGRAV_SPARK.get(),
                    cx + (random.nextDouble() - 0.5) * 0.6, cy + 0.4, cz + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.03 + random.nextDouble() * 0.03, 0.0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(cx, cy, cz, ModSounds.FRAGMENT_HUM.get(), SoundSource.BLOCKS,
                    0.6F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }
}
