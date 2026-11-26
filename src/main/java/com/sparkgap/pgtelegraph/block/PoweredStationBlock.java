package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord;

import java.util.function.Supplier;

/**
 * A station box that runs off the mains: like {@link StationBlock} (horizontal facing, coax on any
 * side, one block entity, an empty-hand action) with PWR+ / PWR- terminals on top at the back - wire
 * them, or plug a Power Grid power cord into the box.
 */
public class PoweredStationBlock<T extends BlockEntity> extends ElectricBlock implements IBE<T>, RfPort, IAcceptCord {
    private final VoxelShape[] shapes;
    private final Class<T> cls;
    private final Supplier<? extends BlockEntityType<? extends T>> type;
    private final StationBlock.Use<T> use;

    public PoweredStationBlock(Properties p, double[] box, Class<T> cls, Supplier<? extends BlockEntityType<? extends T>> type,
                               StationBlock.Use<T> use) {
        super(p);
        // two terminals on top of the box, at the back corners
        double y = box[4], z0 = box[5] - 3.5, z1 = box[5] - 0.5;
        double[][] terms = {
                {box[3] - 3.5, y, z0, box[3] - 0.5, y + 2, z1},
                {box[0] + 0.5, y, z0, box[0] + 3.5, y + 2, z1}};
        this.shapes = Geometry.shapes(new double[][]{box, terms[0], terms[1]});
        this.cls = cls;
        this.type = type;
        this.use = use;
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(s -> Geometry.terminals(Rack.racked(s) ? Rack.rearTerminals(terms) : terms, new Component[]{
                        Component.translatable("pgtelegraph.terminal.pwr_pos"),
                        Component.translatable("pgtelegraph.terminal.pwr_neg")}, s.getValue(DeviceBlock.FACING)))
                .withShapeMapper(s -> shapes[s.getValue(DeviceBlock.FACING).get2DDataValue()])
                .build());
        registerDefaultState(defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH).setValue(Rack.RACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DeviceBlock.FACING, Rack.RACKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(DeviceBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape s = shapes[state.getValue(DeviceBlock.FACING).get2DDataValue()];
        if (Rack.racked(state)) return net.minecraft.world.phys.shapes.Shapes.or(s, Rack.POSTS);
        return s;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(DeviceBlock.FACING, rotation.rotate(state.getValue(DeviceBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(DeviceBlock.FACING)));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (use == null || !cls.isInstance(be)) return InteractionResult.PASS;
        return use.use((T) be, player, level, pos) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    @Override
    public Class<T> getBlockEntityClass() {
        return cls;
    }

    @Override
    public BlockEntityType<? extends T> getBlockEntityType() {
        return type.get();
    }
}
