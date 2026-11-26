package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 19-inch equipment rack (장비 랙): a steel frame that joins up with racks above and below. The
 * frame is one bonded piece of metal: ground any rack of a connected group (a Power Grid grounding
 * rod or a ground mat touching it) and every rack in the group is ground - a lightning arrester
 * touching any of them is grounded.
 */
public class EquipmentRackBlock extends Block {
    public static final BooleanProperty UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;
    private static final int MAX_GROUP = 128;
    private static final VoxelShape POSTS = Shapes.or(
            Block.box(0, 0, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16), Block.box(14, 0, 14, 16, 16, 16),
            Block.box(0, 0, 2, 1, 16, 14), Block.box(15, 0, 2, 16, 16, 14));
    private static final VoxelShape TOP = Block.box(0, 15, 0, 16, 16, 16), BOTTOM = Block.box(0, 0, 0, 16, 1, 16);

    public EquipmentRackBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(DeviceBlock.FACING, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level l = ctx.getLevel();
        BlockPos p = ctx.getClickedPos();
        return defaultBlockState().setValue(DeviceBlock.FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(UP, l.getBlockState(p.above()).getBlock() instanceof EquipmentRackBlock)
                .setValue(DOWN, l.getBlockState(p.below()).getBlock() instanceof EquipmentRackBlock);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos np) {
        if (dir == Direction.UP) return state.setValue(UP, neighbor.getBlock() instanceof EquipmentRackBlock);
        if (dir == Direction.DOWN) return state.setValue(DOWN, neighbor.getBlock() instanceof EquipmentRackBlock);
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape v = POSTS;
        if (!s.getValue(UP)) v = Shapes.or(v, TOP);
        if (!s.getValue(DOWN)) v = Shapes.or(v, BOTTOM);
        return v;
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(DeviceBlock.FACING, r.rotate(s.getValue(DeviceBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(DeviceBlock.FACING)));
    }

    /** is the rack group containing {@code start} bonded to ground somewhere */
    public static boolean grounded(Level level, BlockPos start) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && seen.size() <= MAX_GROUP) {
            BlockPos c = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (!level.isLoaded(n)) continue;
                if (LightningArresterBlock.isEarth(level, n)) return true;
                if (level.getBlockState(n).getBlock() instanceof EquipmentRackBlock && seen.add(n)) q.add(n);
            }
            if (LightningArresterBlock.isEarth(level, c.below(2)) && !(level.getBlockState(c.below()).getBlock() instanceof EquipmentRackBlock))
                return true;
        }
        return false;
    }
}
