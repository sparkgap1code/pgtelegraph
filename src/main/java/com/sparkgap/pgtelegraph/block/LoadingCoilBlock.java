package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Loading coil (부하 코일). Place it next to the foot of a vertical mast; coax plugs into it. */
public class LoadingCoilBlock extends Block implements IBE<LoadingCoilBlockEntity>, RfPort {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);
    /** unloaded Q of the winding: 800 for solid copper, 3000 for Litz wire (리츠선) */
    public final double coilQ;

    public LoadingCoilBlock(Properties properties) {
        this(properties, 800);
    }

    public LoadingCoilBlock(Properties properties, double coilQ) {
        super(properties);
        this.coilQ = coilQ;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof LoadingCoilBlockEntity be) {
            if (level.isClientSide) ClientAccess.openCoil(pos, be.inductanceMH());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public Class<LoadingCoilBlockEntity> getBlockEntityClass() {
        return LoadingCoilBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LoadingCoilBlockEntity> getBlockEntityType() {
        return PgTelegraph.LOADING_COIL_BE.get();
    }
}
