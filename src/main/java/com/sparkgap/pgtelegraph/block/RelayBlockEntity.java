package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;

public class RelayBlockEntity extends CoilDeviceBlockEntity {
    public static final float COIL_OHMS = 150f;
    private static final float CONTACT_OHMS = 0.05f;
    /** 150 Ω line relay: coil 4 W continuous (≈0.16 A), contacts 2 A, 250 V, 105 °C. */
    public static final Spec SPEC = new Spec(105f, 4f, 1f, 250f,
            Spec.coil(COIL_OHMS, 0.2f), Spec.contact(CONTACT_OHMS, 2f, 1));

    private SwitchedWire contact;
    private ScrollValueBehaviour sensitivity;

    public RelayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // pull-in current in mA (real relays: adjustable spring tension)
        sensitivity = new StepScroll.Plain(Component.translatable("pgtelegraph.relay.pickup"), this,
                ValueBoxes.top(8, 2, 2.5, 0.3f), new int[]{5, 7, 10, 15, 20, 25, 30, 40, 50, 70, 100, 150, 200})
                .stepFormat(v -> v + " mA")
                .between(5, 200)
                .withFormatter(v -> v + " mA");
        sensitivity.value = 20;
        behaviours.add(sensitivity);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    protected void guards(Guard g) {
        super.guards(g);
        g.check(contact, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
    }

    @Override
    protected void openAll() {
        super.openAll();
        if (contact != null && contact.getState()) contact.setState(false);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        coil = builder.connectSwitch(COIL_OHMS, builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
        contact = builder.connectSwitch(CONTACT_OHMS, builder.terminalNode(2), builder.terminalNode(3), isEnergized() && !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (contact != null) applyPower(contact);
    }

    @Override
    protected double pickupAmps() {
        return (sensitivity == null ? 20 : sensitivity.getValue()) / 1000.0;
    }

    @Override
    protected void onArmature(boolean energized) {
        if (contact != null) contact.setState(energized && !isBurnt());
        level.playSound(null, worldPosition, PgTelegraph.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.5f,
                energized ? 1.1f : 0.9f);
    }
}
