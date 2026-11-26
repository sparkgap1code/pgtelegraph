package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;

import java.util.List;

/**
 * 100 Ω buzzer, sounds above 10 mA. The tone itself is a client-side looping sound started when
 * the block state shows ENERGIZED; the recorded tone is 700 Hz, pitch = chosen Hz / 700.
 */
public class BuzzerBlockEntity extends CoilDeviceBlockEntity {
    public static final float COIL_OHMS = 100f;
    public static final double PICKUP_AMPS = 0.01;
    public static final float BASE_HZ = 700f;
    /** Small buzzer: 2 W continuous ≈ 14 V, 30 V insulation, 85 °C. */
    public static final Spec SPEC = new Spec(85f, 2f, 0.5f, 30f, Spec.coil(COIL_OHMS, 0.15f));

    private ScrollValueBehaviour tone;
    /** Client only: the playing sound instance (typed Object so the server never loads client classes). */
    public Object clientSound;

    public BuzzerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // tone in units of 50 Hz: 8..20 -> 400..1000 Hz
        tone = new ScrollValueBehaviour(Component.translatable("pgtelegraph.buzzer.tone"), this,
                ValueBoxes.front(8, 4, 4, 0.3f))
                .between(8, 20)
                .withFormatter(v -> (v * 50) + " Hz");
        tone.value = 14;
        behaviours.add(tone);
    }

    public float pitch() {
        int v = tone == null ? 14 : tone.getValue();
        return Math.max(0.5f, Math.min(2.0f, v * 50f / BASE_HZ));
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
        // sound is handled client-side from the block state
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide && getBlockState().getValue(ENERGIZED)) {
            ClientAccess.ensureBuzzerSound(this);
        }
    }
}
