package com.ananttomar.livingblackhole.block;

import com.ananttomar.livingblackhole.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Nine units of Compressed Matter fused into a block; its veins pulse with trapped light. */
public class CompressedMatterBlock extends Block {
    public CompressedMatterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) {
            return;
        }
        Direction face = Direction.getRandom(random);
        BlockPos side = pos.relative(face);
        if (level.getBlockState(side).isSolidRender(level, side)) {
            return;
        }
        double x = pos.getX() + 0.5 + face.getStepX() * 0.52 + (face.getStepX() == 0 ? (random.nextDouble() - 0.5) : 0.0);
        double y = pos.getY() + 0.5 + face.getStepY() * 0.52 + (face.getStepY() == 0 ? (random.nextDouble() - 0.5) : 0.0);
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.52 + (face.getStepZ() == 0 ? (random.nextDouble() - 0.5) : 0.0);
        level.addParticle(ModParticles.MATTER_STREAM.get(), x, y, z,
                face.getStepX() * 0.01, face.getStepY() * 0.01, face.getStepZ() * 0.01);
    }
}
