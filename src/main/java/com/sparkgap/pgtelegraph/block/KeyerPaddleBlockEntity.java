package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;

/**
 * Paddle with a built-in iambic keyer. The levers are read directly (no external keyer, no
 * supply needed: a cell inside runs the logic); the keyed output is one contact between the two
 * OUT terminals, rated like the keyer block's output.
 */
public class KeyerPaddleBlockEntity extends RatedBlockEntity implements TelegraphInput {
    /** Small reed relay output: 30 V, 0.5 A, 85 °C. */
    public static final Spec SPEC = new Spec(85f, 1f, 0.5f, 30f, Spec.contact(0.05f, 0.5f, 1));
    private static final float OUTPUT_RESISTANCE = 0.05f;
    private static final int HOLD_TIMEOUT_TICKS = 10;

    private SwitchedWire output;
    private ScrollValueBehaviour wpm;
    private IambicKeyer keyer;
    private boolean keyed;
    private long ditSeen, dahSeen;

    public KeyerPaddleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        wpm = new ScrollValueBehaviour(Component.translatable("pgtelegraph.keyer.speed"), this, new BackBox())
                .between(5, 40)
                .withFormatter(v -> v + " WPM");
        wpm.value = 15;
        behaviours.add(wpm);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    protected void guards(Guard g) {
        g.check(output, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
    }

    @Override
    protected void openAll() {
        if (output != null && output.getState()) output.setState(false);
        keyed = false;
        if (keyer != null) keyer.reset();
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        output = builder.connectSwitch(OUTPUT_RESISTANCE, builder.terminalNode(0), builder.terminalNode(1), keyed && !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (output != null) applyPower(output);
    }

    @Override
    public void input(int channel, boolean down) {
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        if (channel == DIT) {
            if (down) ditSeen = now;
            setLever(KeyerPaddleBlock.DIT, down);
        } else if (channel == DAH) {
            if (down) dahSeen = now;
            setLever(KeyerPaddleBlock.DAH, down);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        BlockState s = getBlockState();
        if (s.getValue(KeyerPaddleBlock.DIT) && now - ditSeen > HOLD_TIMEOUT_TICKS) setLever(KeyerPaddleBlock.DIT, false);
        if (s.getValue(KeyerPaddleBlock.DAH) && now - dahSeen > HOLD_TIMEOUT_TICKS) setLever(KeyerPaddleBlock.DAH, false);
        if (keyer == null) keyer = new IambicKeyer();
        s = getBlockState();
        boolean on = !isBurnt() && keyer.tick(s.getValue(KeyerPaddleBlock.DIT), s.getValue(KeyerPaddleBlock.DAH),
                wpm == null ? 15 : wpm.getValue());
        if (on != keyed) setKeyed(on);
    }

    private void setLever(BooleanProperty side, boolean down) {
        BlockState s = getBlockState();
        if (s.getValue(side) == down) return;
        level.setBlock(worldPosition, s.setValue(side, down), 3);
        level.playSound(null, worldPosition, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.15f, down ? 2.0f : 1.7f);
    }

    private void setKeyed(boolean on) {
        keyed = on;
        boolean closed = on && !isBurnt();
        if (output != null && output.getState() != closed) output.setState(closed);
        BlockState s = getBlockState();
        if (s.getValue(KeyerPaddleBlock.KEYED) != on) level.setBlock(worldPosition, s.setValue(KeyerPaddleBlock.KEYED, on), 2);
    }

    /** Speed setting on the back face of the keyer case. */
    private static class BackBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8 / 16.0, 2.5 / 16.0, 15.05 / 16.0);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == state.getValue(KeyerPaddleBlock.FACING).getOpposite();
        }
    }
}
