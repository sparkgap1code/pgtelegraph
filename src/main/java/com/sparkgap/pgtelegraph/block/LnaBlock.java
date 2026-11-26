package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * In-line low-noise amplifier (선로 LNA / 마스트헤드 프리앰프). Goes in the coax run, best right at the
 * antenna: +20 dB, noise figure 0.8 dB - the signal is lifted before the long coax down to the radio
 * can lose it and add its noise, so a weak VHF/UHF station comes up out of the hiss. On HF the sky's
 * own noise is far above any amplifier's, so it buys nothing there (and strong stations get stronger
 * too). It only amplifies towards the radio: transmit through it and it is destroyed (burnt open:
 * 40 dB of loss until replaced).
 */
public class LnaBlock extends Block implements RfPort {
    public static final BooleanProperty BURNT = BooleanProperty.create("burnt");
    public static final double GAIN_DB = 20, NF_DB = 0.8, BURNT_LOSS_DB = 40;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 7, 11);

    public LnaBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BURNT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BURNT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable(state.getValue(BURNT)
                ? "pgtelegraph.lna.burnt" : "pgtelegraph.lna.ok"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
