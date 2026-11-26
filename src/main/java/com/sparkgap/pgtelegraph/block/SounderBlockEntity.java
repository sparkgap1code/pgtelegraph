package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;

public class SounderBlockEntity extends CoilDeviceBlockEntity {
    /** Coil resistance (Ω). A 12 V cell drives ~0.6 A; pull-in needs 0.1 A. */
    public static final float COIL_OHMS = 20f;
    public static final double PICKUP_AMPS = 0.1;
    /** Class A coil insulation (105 °C); 15 W continuous ≈ 17 V; enamel good for 250 V. */
    public static final Spec SPEC = new Spec(105f, 15f, 1.5f, 250f, Spec.coil(COIL_OHMS, 1.0f));

    public SounderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        coil = builder.connectSwitch(COIL_OHMS, builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
    }

    @Override
    protected double pickupAmps() {
        return PICKUP_AMPS;
    }

    @Override
    protected void onArmature(boolean energized) {
        level.playSound(null, worldPosition,
                energized ? PgTelegraph.SOUNDER_DOWN.get() : PgTelegraph.SOUNDER_UP.get(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
