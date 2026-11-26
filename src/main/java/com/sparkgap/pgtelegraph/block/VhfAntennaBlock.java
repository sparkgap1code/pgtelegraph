package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * VHF / UHF / SHF antennas - all shorter than a block, so each is one block:
 * WHIP (omni, band on the Create panel), YAGI (12 dBi beam, band on the panel, aimed),
 * DISH (1 m parabolic, 1-30 GHz, gain rises with frequency, aimed). Empty hand on a beam: aim it.
 */
public class VhfAntennaBlock extends Block implements IBE<VhfAntennaBlockEntity> {
    public enum Type {
        WHIP, YAGI, DISH,
        /** normal-mode helical HF whip (1 m, wound to resonate on its band) */
        HELICAL_WHIP,
        /** tuned receiving loop, kHz to 30 MHz, figure-eight pattern */
        LOOP,
        /** axial-mode helix for UHF/SHF, circularly polarised beam */
        HELIX,
        /** phased-array tile: flat patch elements laid side by side, steered electronically */
        ARRAY;

        public boolean aimed() {
            return this == YAGI || this == DISH || this == LOOP || this == HELIX || this == ARRAY;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape WHIP = Shapes.or(Block.box(5, 0, 5, 11, 3, 11), Block.box(7.5, 3, 7.5, 8.5, 14, 8.5));
    private static final VoxelShape FULL = Block.box(1, 0, 1, 15, 15, 15);
    private static final VoxelShape TILE = Block.box(0, 0, 0, 16, 3, 16);

    public final Type type;

    public VhfAntennaBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // a beam points where the player looks
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof VhfAntennaBlockEntity be) {
            be.setAim(placer.getYRot() + 180, 0); // MC yaw 0 = south; compass bearing = yaw + 180
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type == Type.WHIP || type == Type.HELICAL_WHIP ? WHIP : type == Type.ARRAY ? TILE : FULL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!type.aimed() || !(level.getBlockEntity(pos) instanceof VhfAntennaBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) {
            if (type == Type.LOOP && !player.isShiftKeyDown()) ClientAccess.openTune(pos, be.loopHz());
            else ClientAccess.openAim(pos, be.azimuth(), be.elevation());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Class<VhfAntennaBlockEntity> getBlockEntityClass() {
        return VhfAntennaBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends VhfAntennaBlockEntity> getBlockEntityType() {
        return PgTelegraph.VHF_ANTENNA_BE.get();
    }
}
