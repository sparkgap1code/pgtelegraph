package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

/** Paddle = two momentary contacts sharing a common terminal. No timing logic here. */
public class PaddleBlockEntity extends RatedBlockEntity implements TelegraphInput {
    /** Fine silver contacts meant for keyer inputs: 50 V, 1 A each, 100 °C. */
    public static final Spec SPEC = new Spec(100f, 1f, 0.5f, 50f, Spec.contact(0.05f, 1f, 2));
    private static final float CONTACT_RESISTANCE = 0.05f;
    private static final int HOLD_TIMEOUT_TICKS = 10;

    private SwitchedWire ditContact, dahContact;
    private long ditSeen, dahSeen;

    public PaddleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    protected void guards(Guard g) {
        float a = SPEC.part("contact").maxAmps();
        g.check(ditContact, SPEC.maxVolts(), a);
        g.check(dahContact, SPEC.maxVolts(), a);
    }

    @Override
    protected void openAll() {
        if (ditContact != null && ditContact.getState()) ditContact.setState(false);
        if (dahContact != null && dahContact.getState()) dahContact.setState(false);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(3);
        BlockState s = getBlockState();
        ditContact = builder.connectSwitch(CONTACT_RESISTANCE, builder.terminalNode(0), builder.terminalNode(1), s.getValue(PaddleBlock.DIT) && !isBurnt());
        dahContact = builder.connectSwitch(CONTACT_RESISTANCE, builder.terminalNode(2), builder.terminalNode(1), s.getValue(PaddleBlock.DAH) && !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (ditContact != null) applyPower(ditContact);
        if (dahContact != null) applyPower(dahContact);
    }

    @Override
    public void input(int channel, boolean down) {
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        if (channel == DIT) {
            if (down) ditSeen = now;
            set(PaddleBlock.DIT, down);
        } else if (channel == DAH) {
            if (down) dahSeen = now;
            set(PaddleBlock.DAH, down);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        BlockState s = getBlockState();
        if (s.getValue(PaddleBlock.DIT) && now - ditSeen > HOLD_TIMEOUT_TICKS) set(PaddleBlock.DIT, false);
        if (s.getValue(PaddleBlock.DAH) && now - dahSeen > HOLD_TIMEOUT_TICKS) set(PaddleBlock.DAH, false);
    }

    private void set(net.minecraft.world.level.block.state.properties.BooleanProperty side, boolean down) {
        BlockState s = getBlockState();
        if (s.getValue(side) == down) return;
        level.setBlock(worldPosition, s.setValue(side, down), 3);
        SwitchedWire contact = side == PaddleBlock.DIT ? ditContact : dahContact;
        if (contact != null) contact.setState(down && !isBurnt());
        level.playSound(null, worldPosition, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.15f, down ? 2.0f : 1.7f);
    }
}
