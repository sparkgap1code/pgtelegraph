package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import net.minecraft.network.chat.Component;

/** Glass insulator (유리 애자) or wooden peg (나무 못): an overhead-wire tie point that leaks to earth in rain. */
public class InsulatorBlock extends DeviceBlock<InsulatorBlockEntity> {
    public enum Kind {
        GLASS(50_000_000, 50_000), PEG(50_000, 400);
        public final double dryOhms, wetOhms;

        Kind(double dry, double wet) {
            dryOhms = dry;
            wetOhms = wet;
        }
    }

    static final double[][] TERMINALS = {{6.5, 6, 6.5, 9.5, 8, 9.5}};
    public final Kind kind;

    public InsulatorBlock(Properties properties, Kind kind) {
        super(properties, TERMINALS, new Component[]{IDecoratedTerminal.CONNECTOR},
                new double[][]{{5.5, 0, 5.5, 10.5, 6, 10.5}, TERMINALS[0]},
                InsulatorBlockEntity.class, PgTelegraph.INSULATOR_BE);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
}
