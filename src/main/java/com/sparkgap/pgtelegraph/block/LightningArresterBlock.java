package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lightning arrester (피뢰기): a gas-discharge tube in the coax run, coax in from any side. It only
 * protects if it is grounded - a Power Grid grounding rod or a ground mat touching it, or within
 * two blocks below. Passes RF with 0.1 dB loss.
 */
public class LightningArresterBlock extends Block implements RfPort {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public LightningArresterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    public static boolean grounded(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) if (isGround(level, pos.relative(d))) return true;
        return isGround(level, pos.below(2));
    }

    static boolean isGround(Level level, BlockPos p) {
        if (level.getBlockState(p).getBlock() instanceof EquipmentRackBlock) return EquipmentRackBlock.grounded(level, p);
        return isEarth(level, p);
    }

    /** a ground mat or a Power Grid grounding rod (not a rack) */
    static boolean isEarth(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof GroundMatBlock) return true;
        var id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id.getNamespace().equals("powergrid") && id.getPath().contains("grounding");
    }
}
