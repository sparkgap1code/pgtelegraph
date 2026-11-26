package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

public class TelegraphKeyBlockEntity extends RatedBlockEntity implements TelegraphInput {
    /** Brass contacts on a hard-rubber base: 250 V insulation, 3 A contacts, base softens past 120 °C. */
    public static final Spec SPEC = new Spec(120f, 2f, 0.5f, 250f, Spec.contact(0.05f, 3f, 1));
    /** Contact resistance when closed (Ohm). Same order as Power Grid's LV switch (0.15). */
    public static final float CONTACT_RESISTANCE = 0.05f;
    /** Server releases the key if the holding client stops refreshing for this many ticks. */
    private static final int HOLD_TIMEOUT_TICKS = 10;

    private SwitchedWire wire;
    private long lastHoldTick;

    public TelegraphKeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    protected void guards(Guard g) {
        g.check(wire, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
    }

    @Override
    protected void openAll() {
        if (wire != null && wire.getState()) wire.setState(false);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        boolean pressed = getBlockState().getValue(TelegraphKeyBlock.PRESSED);
        wire = builder.connectSwitch(CONTACT_RESISTANCE, builder.terminalNode(0), builder.terminalNode(1), pressed && !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (wire != null) applyPower(wire);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (isPressed() && level.getGameTime() - lastHoldTick > HOLD_TIMEOUT_TICKS) {
            setPressed(false);
        }
    }

    @Override
    public void input(int channel, boolean down) {
        if (channel == STRAIGHT) setPressed(down);
    }

    public boolean isPressed() {
        return getBlockState().getValue(TelegraphKeyBlock.PRESSED);
    }

    /** Called on the server from the client hold packet (down=true repeats as a keep-alive). */
    public void setPressed(boolean down) {
        if (level == null || level.isClientSide) return;
        if (down) lastHoldTick = level.getGameTime();
        if (down == isPressed()) return;

        level.setBlock(worldPosition, getBlockState().setValue(TelegraphKeyBlock.PRESSED, down), 3);
        if (wire != null) wire.setState(down && !isBurnt());
        level.playSound(null, worldPosition,
                down ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF,
                SoundSource.BLOCKS, 0.4f, down ? 1.8f : 1.5f);
        setChanged();
    }
}
