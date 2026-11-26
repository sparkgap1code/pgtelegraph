package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Locale;

/**
 * Field telephone (야전 전화, after the EE-8): two line terminals L1 / L2 - a pair of wires, or one
 * wire with L2 to earth. A hand magneto (sneak + empty hand: crank) puts about 75 V on the line;
 * every phone across that line rings its bell (a 1000 Ω ringer), and a switchboard's drop
 * indicator falls. Lift the handset (empty hand) and talk with push-to-talk: the speech goes along
 * the wires - through switchboards only along their cords - to every other phone off the hook,
 * more hiss the longer the line.
 */
public class FieldPhoneBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final BooleanProperty OFF_HOOK = BooleanProperty.create("off_hook");
    public static final float RINGER_OHMS = 1000f, HANDSET_OHMS = 300f, MAGNETO_V = 75f, MAGNETO_OHMS = 50f;
    public static final double RING_V = 30;
    public static final Spec SPEC = new Spec(80f, 8f, 1f, 250f, Spec.coil(RINGER_OHMS, 0.2f));

    private SwitchedWire ringer, handset, crank;
    private int cranking, ringTick;
    private boolean ringing;

    public FieldPhoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    public boolean offHook() {
        return getBlockState().hasProperty(OFF_HOOK) && getBlockState().getValue(OFF_HOOK);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        ringer = builder.connectSwitch(RINGER_OHMS, builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
        handset = builder.connectSwitch(HANDSET_OHMS, builder.terminalNode(0), builder.terminalNode(1), offHook() && !isBurnt());
        FloatingNode n = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(n, builder.terminalNode(1), MAGNETO_OHMS, MAGNETO_V));
        crank = builder.connectSwitch(1f, n, builder.terminalNode(0), false);
    }

    @Override
    protected void guards(Guard g) {
        g.check(ringer, SPEC.maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : new SwitchedWire[]{ringer, handset, crank}) if (w != null && w.getState()) w.setState(false);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (ringer != null) applyPower(ringer);
    }

    /** sneak + empty hand: a turn of the magneto handle */
    public void crank() {
        if (isBurnt() || level == null) return;
        cranking = 30;
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
    }

    public void setHook(boolean off) {
        if (level == null) return;
        level.setBlock(worldPosition, getBlockState().setValue(OFF_HOOK, off), 3);
        if (handset != null && !isBurnt()) handset.setState(off);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, off ? 1.4f : 0.9f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt() || crank == null) return;
        if (cranking > 0) cranking--;
        boolean c = cranking > 0;
        if (crank.getState() != c) crank.setState(c);
        if (handset != null && handset.getState() != offHook()) handset.setState(offHook());
        boolean r = !c && Math.abs(ringer.potentialDifference()) > RING_V;
        if (r && ++ringTick % 5 == 1)
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.9f + level.random.nextFloat() * 0.1f);
        if (!r) ringTick = 0;
        if (r != ringing) {
            ringing = r;
            sendData();
        }
    }

    /** speech into this phone's handset: out to every other phone off the hook on the line */
    public void speak(byte[] data) {
        if (!(level instanceof ServerLevel sl) || !offHook() || isBurnt()) return;
        for (WireTrace.Hit h : WireTrace.trace(sl, worldPosition, new int[]{0, 1}, be -> be instanceof FieldPhoneBlockEntity)) {
            if (h.kind() != WireTrace.Kind.DEVICE || !(sl.getBlockEntity(h.pos()) instanceof FieldPhoneBlockEntity other)) continue;
            if (other == this || !other.offHook() || other.isBurnt()) continue;
            // line hiss: about 40 dB on a short line, down to 12 dB over several kilometres of wire
            float snr = (float) Math.max(12, 40 - 10 * Math.log10(1 + h.meters() / 100));
            var msg = new com.sparkgap.pgtelegraph.media.VoiceNet.Out(h.pos(), data, snr, false);
            for (ServerPlayer p : sl.players())
                if (p.distanceToSqr(h.pos().getCenter()) < 12 * 12)
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, msg);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        if (clientPacket) tag.putBoolean("Ring", ringing);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (clientPacket) ringing = tag.getBoolean("Ring");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal("     ").append(Component.translatable(offHook() ? "pgtelegraph.phone.off_hook" : "pgtelegraph.phone.on_hook"))
                .withStyle(offHook() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (ringing) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.phone.ringing")).withStyle(ChatFormatting.GOLD));
        if (ringer != null)
            tip.add(Component.literal(String.format(Locale.ROOT, "     %.1f V", Math.abs(ringer.potentialDifference()))).withStyle(ChatFormatting.GRAY));
        return true;
    }
}
