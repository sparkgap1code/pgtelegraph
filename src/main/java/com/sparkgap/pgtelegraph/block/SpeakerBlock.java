package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Cone speaker or horn loudspeaker: two terminals for the amplifier's speaker line. Horn: empty hand tilts it. */
public class SpeakerBlock extends DeviceBlock<SpeakerBlockEntity> implements org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    static final double[][] CONE_TERMINALS = {{4, 2, 13, 7, 4, 15}, {9, 2, 13, 12, 4, 15}};
    static final double[][] HORN_TERMINALS = {{3, 1, 0, 6, 3, 2}, {10, 1, 0, 13, 3, 2}};
    public final boolean horn;

    public SpeakerBlock(Properties properties, boolean horn) {
        super(properties, horn ? HORN_TERMINALS : CONE_TERMINALS,
                new Component[]{Component.literal("SPK 1"), Component.literal("SPK 2")},
                horn ? new double[][]{{2, 0, 0, 14, 1, 16}, {5, 1, 2, 11, 9, 7}, {1, 1, 7, 15, 15, 16}, HORN_TERMINALS[0], HORN_TERMINALS[1]}
                        : new double[][]{{2, 0, 3, 14, 14, 13}, CONE_TERMINALS[0], CONE_TERMINALS[1]},
                SpeakerBlockEntity.class, horn ? PgTelegraph.HORN_SPEAKER_BE : PgTelegraph.SPEAKER_BE);
        this.horn = horn;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!horn || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            be.cycleTilt();
            player.displayClientMessage(Component.translatable("pgtelegraph.speaker.tilt",
                    String.format(java.util.Locale.ROOT, "%+d°", be.tiltDeg())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
