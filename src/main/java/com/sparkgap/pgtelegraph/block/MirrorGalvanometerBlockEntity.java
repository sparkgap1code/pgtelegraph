package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * Mirror galvanometer (거울 검류계, Thomson 1858): a tiny magnet and mirror on a silk thread inside
 * a 50 Ω coil; a lamp's spot of light on a scale swings left or right with the current - a few
 * microamps show. Reads the far end of a submarine cable. The spot is drawn on the scale on top;
 * past the trip level (value box, 0.02 - 5 mA) of the + direction its contact closes, so it can
 * work a local sounder or inker like a relay. Terminals: IN+, IN-, OUT, OUT.
 */
public class MirrorGalvanometerBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final float COIL_OHMS = 50f;
    public static final Spec SPEC = new Spec(80f, 2f, 0.5f, 50f, Spec.coil(COIL_OHMS, 0.05f), Spec.contact(0.05f, 1f, 1));
    static final int[] TRIP_UA = {20, 50, 100, 200, 500, 1000, 2000, 5000};

    private SwitchedWire coil, contact;
    private ScrollValueBehaviour trip;
    private boolean closed;
    /** synced: coil current (A, signed) */
    public float amps;
    /** client: the spot's position, -1..1, lagging like a damped needle */
    public float spot, spotO;

    public MirrorGalvanometerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        trip = new StepScroll.Plain(Component.translatable("pgtelegraph.galvo.trip"), this,
                ValueBoxes.front(8, 2, 2, 0.25f), TRIP_UA).stepFormat(v -> v >= 1000 ? (v / 1000) + " mA" : v + " μA")
                .between(20, 5000)
                .withFormatter(v -> v >= 1000 ? (v / 1000) + " mA" : v + " μA");
        trip.value = 100;
        behaviours.add(trip);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    /** 0 IN+, 1 IN-, 2 / 3 OUT */
    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        coil = builder.connectSwitch(COIL_OHMS, builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
        contact = builder.connectSwitch(0.05f, builder.terminalNode(2), builder.terminalNode(3), false);
    }

    @Override
    protected void guards(Guard g) {
        g.check(coil, 0, 0.05f);
        g.check(contact, SPEC.maxVolts(), 1f);
    }

    @Override
    protected void openAll() {
        if (coil != null && coil.getState()) coil.setState(false);
        if (contact != null && contact.getState()) contact.setState(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) { // the spot swings toward the current, logarithmic scale, damped
            spotO = spot;
            double a = amps;
            double target = Math.signum(a) * Math.min(1, Math.log10(1 + Math.abs(a) / 2e-6) / 4);
            spot += (float) ((target - spot) * 0.3);
            return;
        }
        if (isBurnt() || coil == null) return;
        // current from IN+ to IN- is positive (sign follows the solver's orientation)
        float a = (float) coil.current();
        double t = (trip == null ? 100 : trip.getValue()) * 1e-6;
        boolean c = closed ? a > t * 0.7 : a > t;
        if (c != closed) {
            closed = c;
            contact.setState(c);
            level.playSound(null, worldPosition, PgTelegraph.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.15f, 1.6f);
        }
        if (Math.abs(a - amps) > Math.max(1e-6, Math.abs(amps) * 0.05) || level.getGameTime() % 20 == 0) {
            amps = a;
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        if (clientPacket) tag.putFloat("A", amps);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (clientPacket) amps = tag.getFloat("A");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        double ua = amps * 1e6;
        tip.add(Component.literal(Math.abs(ua) >= 1000 ? String.format(Locale.ROOT, "     %+.2f mA", ua / 1000) : String.format(Locale.ROOT, "     %+.1f μA", ua))
                .withStyle(closed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return true;
    }
}
