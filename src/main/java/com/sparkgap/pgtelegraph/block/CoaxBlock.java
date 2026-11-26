package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Coaxial cable laid like redstone dust: flat on the ground, joins neighbouring cable, climbs one
 * block up the side of a solid block and drops down ledges. 1 block = 1 m of cable.
 * It connects to radios and antennas next to it.
 * <p>
 * Placed against the side of a block it is clipped to the wall instead, running straight up and down:
 * it joins cable above and below it (on the wall or on the floor), cable beside it, and goes over the
 * top of the wall onto cable lying there - a feeder up the side of a tower or a house.
 */
public class CoaxBlock extends Block {
    public enum Grade {
        /** thin flexible cable (RG-58 class) */
        THIN(0.046, 200),
        /** thick cable (RG-213 class) */
        THICK(0.022, 1500),
        /** rigid low-loss hardline */
        HARDLINE(0.011, 3000),
        /** large-diameter hardline for kilowatt stations */
        HEAVY(0.006, 20000),
        /** broadcast rigid line (6-1/8 inch class), copper pipe in copper pipe */
        RIGID(0.003, 500000);

        /** dB per metre at 10 MHz (scales with √f) */
        public final double lossDbPerM10;
        public final double maxWatts;

        Grade(double loss, double maxWatts) {
            this.lossDbPerM10 = loss;
            this.maxWatts = maxWatts;
        }
    }

    public static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH_REDSTONE,
            Direction.EAST, BlockStateProperties.EAST_REDSTONE,
            Direction.SOUTH, BlockStateProperties.SOUTH_REDSTONE,
            Direction.WEST, BlockStateProperties.WEST_REDSTONE);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1, 14);

    /** which wall the cable is clipped to (NONE: lying on the floor) */
    public enum WallSide implements net.minecraft.util.StringRepresentable {
        NONE("none", null), NORTH("north", Direction.NORTH), EAST("east", Direction.EAST), SOUTH("south", Direction.SOUTH),
        WEST("west", Direction.WEST);
        private final String name;
        public final Direction dir;

        WallSide(String name, Direction dir) {
            this.name = name;
            this.dir = dir;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        static WallSide of(Direction d) {
            for (WallSide w : values()) if (w.dir == d) return w;
            return NONE;
        }
    }

    public static final EnumProperty<WallSide> WALL = EnumProperty.create("wall", WallSide.class);
    private static final Map<WallSide, VoxelShape> WALL_SHAPES = Map.of(
            WallSide.NORTH, Block.box(5, 0, 0, 11, 16, 2), WallSide.SOUTH, Block.box(5, 0, 14, 11, 16, 16),
            WallSide.WEST, Block.box(0, 0, 5, 2, 16, 11), WallSide.EAST, Block.box(14, 0, 5, 16, 16, 11));

    static Direction wall(BlockState s) {
        return s.getBlock() instanceof CoaxBlock && s.hasProperty(WALL) ? s.getValue(WALL).dir : null;
    }

    public final Grade grade;

    public CoaxBlock(Properties properties, Grade grade) {
        super(properties);
        this.grade = grade;
        BlockState s = defaultBlockState();
        for (EnumProperty<RedstoneSide> p : SIDES.values()) s = s.setValue(p, RedstoneSide.NONE);
        registerDefaultState(s.setValue(WALL, WallSide.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH_REDSTONE, BlockStateProperties.EAST_REDSTONE,
                BlockStateProperties.SOUTH_REDSTONE, BlockStateProperties.WEST_REDSTONE, WALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        WallSide w = state.getValue(WALL);
        return w == WallSide.NONE ? SHAPE : WALL_SHAPES.get(w);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    /** Blocks the cable plugs into directly. */
    public static boolean attaches(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CoaxBlock || b instanceof AntennaRodBlock || b instanceof ShortAntennaBlock || b instanceof RfPort;
    }

    private static boolean conductor(BlockGetter level, BlockPos p) {
        return level.getBlockState(p).isRedstoneConductor(level, p);
    }

    private static boolean isCoax(BlockGetter level, BlockPos p) {
        return level.getBlockState(p).getBlock() instanceof CoaxBlock;
    }

    private static RedstoneSide side(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        if (attaches(level.getBlockState(n))) return RedstoneSide.SIDE;
        boolean nSolid = conductor(level, n);
        if (nSolid && !conductor(level, pos.above()) && isCoax(level, n.above())) return RedstoneSide.UP;
        if (!nSolid && isCoax(level, n.below())) return RedstoneSide.SIDE;
        return RedstoneSide.NONE;
    }

    private BlockState compute(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(WALL) != WallSide.NONE) return state; // on a wall: a straight vertical run
        for (var e : SIDES.entrySet()) state = state.setValue(e.getValue(), side(level, pos, e.getKey()));
        return state;
    }

    /** Cable blocks this one is joined to (same rules as the visuals). */
    public static List<BlockPos> links(BlockGetter level, BlockPos c) {
        List<BlockPos> out = new ArrayList<>(6);
        Direction w = wall(level.getBlockState(c));
        if (w != null) { // clipped to a wall: up, down, beside it, and over the top of the wall
            if (isCoax(level, c.above())) out.add(c.above());
            else if (isCoax(level, c.above().relative(w))) out.add(c.above().relative(w));
            if (isCoax(level, c.below())) out.add(c.below());
            for (Direction d : Direction.Plane.HORIZONTAL)
                if (d != w && isCoax(level, c.relative(d))) out.add(c.relative(d));
            return out;
        }
        // a wall run starting from or ending at this floor cable
        if (wall(level.getBlockState(c.above())) != null) out.add(c.above());
        if (wall(level.getBlockState(c.below())) != null) out.add(c.below());
        boolean aboveFree = !conductor(level, c.above());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = c.relative(d);
            if (isCoax(level, n)) out.add(n);
            else if (conductor(level, n)) {
                if (aboveFree && isCoax(level, n.above())) out.add(n.above());
            } else if (isCoax(level, n.below())) out.add(n.below());
        }
        return out;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isHorizontal()) { // clicked the side of a block: clip it to that wall
            BlockState s = defaultBlockState().setValue(WALL, WallSide.of(face.getOpposite()));
            if (canSurvive(s, ctx.getLevel(), ctx.getClickedPos())) return s;
        }
        return compute(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction w = state.getValue(WALL).dir;
        if (w != null) {
            BlockPos on = pos.relative(w);
            return level.getBlockState(on).isFaceSturdy(level, on, w.getOpposite());
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return compute(level, pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock())) refreshDiagonals(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) refreshDiagonals(level, pos);
    }

    private static void refreshDiagonals(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (BlockPos q : new BlockPos[]{pos.relative(d).above(), pos.relative(d).below()}) {
                BlockState s = level.getBlockState(q);
                if (s.getBlock() instanceof CoaxBlock c) {
                    BlockState n = c.compute(level, q, s);
                    if (n != s) level.setBlock(q, n, 2);
                }
            }
        }
    }
}
