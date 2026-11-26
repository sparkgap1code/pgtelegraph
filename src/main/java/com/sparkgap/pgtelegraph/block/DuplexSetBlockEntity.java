package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Locale;

/**
 * Bridge duplex set (이중 전신기): both ends send at once over one wire with earth return. The key
 * puts the 60 V battery on the apex of a Wheatstone bridge: one 500 Ω arm goes out to the LINE,
 * the other to an artificial line (a rheostat to EARTH). The receiving relay sits across the
 * bridge, between the line and the artificial line. When the rheostat matches what the real line
 * looks like, your own sending splits evenly and your relay stays still - only the far end's
 * current moves it. Balance it (value box on top) until your own key no longer clicks your relay.
 * Terminals: LINE, EARTH (back), KEY / KEY COM (key), OUT / OUT (relay contact for a local sounder).
 */
public class DuplexSetBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final float ARM_OHMS = 500f, COIL_OHMS = 200f, BATTERY_V = 60f;
    public static final double PICKUP_A = 0.004;
    public static final Spec SPEC = new Spec(90f, 20f, 2f, 250f, Spec.coil(COIL_OHMS, 0.2f), Spec.contact(0.05f, 2f, 1), Spec.pullup(1000f, 1));
    static final int[] ART_STEPS = {50, 100, 200, 300, 500, 700, 1000, 1500, 2000, 3000, 5000, 7000, 10000, 15000, 20000};

    private SwitchedWire armLine, armArt, art, coil, toBattery, toEarth, pullup, contact;
    private ScrollValueBehaviour artOhms;
    private boolean keyed, pulled;
    private float shownCoilMa;

    public DuplexSetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        artOhms = new StepScroll.Plain(Component.translatable("pgtelegraph.duplex.artificial"), this,
                ValueBoxes.top(8, 4, 8, 0.3f), ART_STEPS).stepFormat(v -> v + " Ω")
                .between(10, 20000)
                .withFormatter(v -> v + " Ω");
        artOhms.value = 1000;
        artOhms.withCallback(v -> {
            if (art != null) art.setResistance(v);
        });
        behaviours.add(artOhms);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    /** terminals: 0 LINE, 1 EARTH, 2 KEY, 3 KEY COM, 4 / 5 OUT */
    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(6);
        var line = builder.terminalNode(0);
        var earth = builder.terminalNode(1);
        FloatingNode bat = builder.addInternalNode(), apex = builder.addInternalNode(), artNode = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(bat, earth, 2f, BATTERY_V));
        boolean ok = !isBurnt();
        toBattery = builder.connectSwitch(5f, bat, apex, false);
        toEarth = builder.connectSwitch(5f, apex, earth, ok);
        armLine = builder.connectSwitch(ARM_OHMS, apex, line, ok);
        armArt = builder.connectSwitch(ARM_OHMS, apex, artNode, ok);
        art = builder.connectSwitch(artOhms == null ? 1000f : artOhms.getValue(), artNode, earth, ok);
        coil = builder.connectSwitch(COIL_OHMS, line, artNode, ok);
        FloatingNode rail = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(rail, builder.terminalNode(3), 1f, 5f));
        pullup = builder.connectSwitch(1000f, rail, builder.terminalNode(2), ok);
        contact = builder.connectSwitch(0.05f, builder.terminalNode(4), builder.terminalNode(5), false);
    }

    @Override
    protected void guards(Guard g) {
        g.check(coil, 0, 0.2f);
        g.check(contact, SPEC.maxVolts(), 2f);
        g.check(pullup, SPEC.maxVolts(), 0.05f);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : new SwitchedWire[]{armLine, armArt, art, coil, toBattery, toEarth, pullup, contact})
            if (w != null && w.getState()) w.setState(false);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (coil != null) applyPower(coil);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt() || coil == null) return;
        boolean k = Math.abs(pullup.current()) > 0.0025;
        if (k != keyed) {
            keyed = k;
            toBattery.setState(k);
            toEarth.setState(!k);
        }
        double a = Math.abs(coil.current());
        boolean p = pulled ? a > PICKUP_A * 0.7 : a > PICKUP_A;
        if (p != pulled) {
            pulled = p;
            contact.setState(p);
            level.playSound(null, worldPosition, PgTelegraph.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.4f, p ? 1.1f : 0.9f);
        }
        float ma = (float) (a * 1000);
        if (Math.abs(ma - shownCoilMa) > 0.2f || level.getGameTime() % 20 == 0) {
            shownCoilMa = ma;
            sendData();
        }
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        if (clientPacket) {
            tag.putFloat("Coil", shownCoilMa);
            tag.putBoolean("Keyed", keyed);
            tag.putBoolean("Pulled", pulled);
        }
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (clientPacket) {
            shownCoilMa = tag.getFloat("Coil");
            keyed = tag.getBoolean("Keyed");
            pulled = tag.getBoolean("Pulled");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %s  %.1f mA", Component.translatable("pgtelegraph.duplex.coil").getString(), shownCoilMa))
                .withStyle(pulled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (keyed) tip.add(Component.literal("     ").append(Component.translatable(pulled ? "pgtelegraph.duplex.unbalanced" : "pgtelegraph.duplex.balanced"))
                .withStyle(pulled ? ChatFormatting.RED : ChatFormatting.GREEN));
        return true;
    }
}
