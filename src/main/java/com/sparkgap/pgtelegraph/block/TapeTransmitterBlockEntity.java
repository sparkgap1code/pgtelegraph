package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.tape.PaperTapeItem;
import com.sparkgap.pgtelegraph.tape.TapeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatic tape transmitter (자동 송신기, Wheatstone type): a star wheel reads the tape and keys
 * the output contact. The tape is pulled by a Create shaft on the back, sides or bottom:
 * <ul>
 *   <li>punched tape: WPM = RPM / 4, held down to the front-panel governor setting (5-24 WPM)</li>
 *   <li>inked tape: replayed at RPM / 48 of the speed it was received (48 RPM = as received)</li>
 *   <li>below 8 RPM the tape stalls; above 128 RPM it tears</li>
 * </ul>
 * Every mark or space lasts at least one tick, so fast tape stretches instead of losing elements.
 */
public class TapeTransmitterBlockEntity extends RatedBlockEntity
        implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final float STALL_RPM = 8, TEAR_RPM = 128, INKED_RPM = 48;
    /** Brass contact keyed by a star wheel: 250 V, 3 A, 100 °C. */
    public static final Spec SPEC = new Spec(100f, 2f, 1f, 250f, Spec.contact(0.05f, 3f, 1));

    private SwitchedWire contact;
    private ScrollValueBehaviour wpm;

    private boolean hasTape;
    private int kind;
    private final List<Integer> runs = new ArrayList<>();
    private String label = "";
    private boolean running;
    private int index;
    private double pos;        // progress through the current run, in its own units
    private boolean keyed;
    private int whine;

    public TapeTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        wpm = new ScrollValueBehaviour(Component.translatable("pgtelegraph.tape_tx.governor"), this,
                ValueBoxes.front(8, 6.5, 6, 0.3f))
                .between(5, 24)
                .withFormatter(v -> v + " WPM");
        wpm.value = 12;
        behaviours.add(wpm);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    protected void guards(Guard g) {
        g.check(contact, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
    }

    @Override
    protected void openAll() {
        if (contact != null && contact.getState()) contact.setState(false);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        contact = builder.connectSwitch(0.05f, builder.terminalNode(0), builder.terminalNode(1), keyed && !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (contact != null) applyPower(contact);
    }

    private int governor() {
        return wpm == null ? 12 : wpm.getValue();
    }

    /**
     * The Create part driving the tape from side {@code d}: it must have a shaft facing into this
     * block. Never the front or the top.
     */
    public com.simibubi.create.content.kinetics.base.KineticBlockEntity driver(Direction d) {
        if (level == null || d == Direction.UP) return null;
        if (getBlockState().hasProperty(DeviceBlock.FACING) && d == getBlockState().getValue(DeviceBlock.FACING)) return null;
        BlockPos np = worldPosition.relative(d);
        BlockState ns = level.getBlockState(np);
        if (!(ns.getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate rot)) return null;
        if (!rot.hasShaftTowards(level, np, ns, d.getOpposite())) return null;
        return level.getBlockEntity(np) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k : null;
    }

    /** Fastest shaft driving the tape. */
    public float driveRpm() {
        float best = 0;
        for (Direction d : Direction.values()) {
            var k = driver(d);
            if (k != null) best = Math.max(best, Math.abs(k.getSpeed()));
        }
        return best;
    }

    /** Tape speed now: run units (dits for punched tape, ticks for inked tape) per tick. */
    private double rate(float rpm) {
        if (rpm < STALL_RPM) return 0;
        if (kind == TapeData.INKED) return rpm / INKED_RPM;
        return Math.min(rpm / 4.0, governor()) / 24.0; // a dit lasts 1.2 s / WPM = 24 / WPM ticks
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!running || isBurnt()) {
            setKeyed(false);
            return;
        }
        float rpm = driveRpm();
        if (rpm > TEAR_RPM) {
            tear();
            return;
        }
        double r = rate(rpm);
        if (r <= 0) return; // stalled: the contact stays where the tape stopped
        if (kind != TapeData.INKED && rpm / 4.0 > governor() + 0.5 && ++whine >= 30) {
            whine = 0; // governor brake slipping
            level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.08f, 1.8f);
        }
        pos += r;
        if (pos < runs.get(index)) return;
        // next mark / space: one per tick at most, the leftover carries over
        pos = Math.min(pos - runs.get(index), 1.0);
        index++;
        if (index >= runs.size()) {
            running = false;
            index = 0;
            pos = 0;
            setKeyed(false);
            updateState();
            return;
        }
        setKeyed(index % 2 == 0);
    }

    /** Shaft too fast: the tape snaps. It is lost; a scrap of paper falls out. */
    private void tear() {
        hasTape = false;
        running = false;
        runs.clear();
        index = 0;
        pos = 0;
        setKeyed(false);
        level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 0.5f);
        level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8f, 1.6f);
        net.minecraft.world.level.block.Block.popResource(level, worldPosition.above(),
                new ItemStack(net.minecraft.world.item.Items.PAPER));
        net.minecraft.world.entity.player.Player p = level.getNearestPlayer(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 8, false);
        if (p != null) p.displayClientMessage(Component.translatable("pgtelegraph.tape_tx.torn"), true);
        updateState();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        float rpm = driveRpm();
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        net.minecraft.ChatFormatting c = rpm > TEAR_RPM ? net.minecraft.ChatFormatting.RED
                : rpm < STALL_RPM ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.GREEN;
        tip.add(Component.literal("    ").append(Component.translatable("pgtelegraph.tape_tx.drive",
                Component.literal(String.format(java.util.Locale.ROOT, "%.0f RPM", rpm)).withStyle(c)))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (rpm < STALL_RPM) {
            tip.add(Component.literal("    ").append(Component.translatable("pgtelegraph.tape_tx.no_drive"))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else if (rpm > TEAR_RPM) {
            tip.add(Component.literal("    ").append(Component.translatable("pgtelegraph.tape_tx.too_fast"))
                    .withStyle(net.minecraft.ChatFormatting.RED));
        } else {
            String sp = String.format(java.util.Locale.ROOT, "%.1f WPM", Math.min(rpm / 4.0, governor()));
            String ink = String.format(java.util.Locale.ROOT, "x%.2f", rpm / INKED_RPM);
            tip.add(Component.literal("    ").append(Component.translatable("pgtelegraph.tape_tx.speed", sp, ink))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return true;
    }

    private void setKeyed(boolean on) {
        keyed = on;
        boolean closed = on && !isBurnt();
        if (contact != null && contact.getState() != closed) {
            contact.setState(closed);
            level.playSound(null, worldPosition, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.12f, closed ? 2.0f : 1.8f);
        }
    }

    public boolean hasTape() {
        return hasTape;
    }

    public boolean isRunning() {
        return running;
    }

    /** Start (or resume) / pause. */
    public void toggle() {
        if (!hasTape || runs.isEmpty()) return;
        running = !running;
        if (running) setKeyed(index % 2 == 0);
        if (!running) setKeyed(false);
        updateState();
    }

    public boolean insert(ItemStack stack) {
        if (hasTape) return false;
        TapeData d = PaperTapeItem.data(stack);
        if (d == null || d.runs().isEmpty()) return false;
        hasTape = true;
        kind = d.kind();
        runs.clear();
        runs.addAll(d.runs());
        label = d.label();
        index = 0;
        pos = 0;
        running = false;
        toggle(); // start right away, like feeding tape into a running machine
        return true;
    }

    public ItemStack extract() {
        ItemStack out = takeTape();
        setKeyed(false);
        updateState();
        return out;
    }

    public ItemStack takeTape() {
        if (!hasTape) return ItemStack.EMPTY;
        ItemStack out = PaperTapeItem.of(new TapeData(kind, runs, label));
        hasTape = false;
        running = false;
        runs.clear();
        index = 0;
        pos = 0;
        setChanged();
        return out;
    }

    private void updateState() {
        BlockState s = getBlockState();
        if (s.hasProperty(TapeTransmitterBlock.HAS_TAPE) && s.getValue(TapeTransmitterBlock.HAS_TAPE) != hasTape) {
            level.setBlock(worldPosition, s.setValue(TapeTransmitterBlock.HAS_TAPE, hasTape), 3);
        }
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket || !hasTape) return;
        CompoundTag t = new CompoundTag();
        t.putInt("Kind", kind);
        t.putIntArray("Runs", TapeData.toArray(runs));
        t.putString("Label", label);
        t.putBoolean("Running", running);
        t.putInt("Index", index);
        t.putDouble("Pos", pos);
        tag.put("Tape", t);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        hasTape = tag.contains("Tape");
        runs.clear();
        if (hasTape) {
            CompoundTag t = tag.getCompound("Tape");
            kind = t.getInt("Kind");
            for (int r : t.getIntArray("Runs")) runs.add(r);
            label = t.getString("Label");
            running = t.getBoolean("Running");
            index = t.getInt("Index");
            pos = t.getDouble("Pos");
        }
    }
}
