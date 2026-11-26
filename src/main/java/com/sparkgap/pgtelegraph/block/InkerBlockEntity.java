package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.tape.PaperTapeItem;
import com.sparkgap.pgtelegraph.tape.TapeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Morse inker (잉커): a coil pulls an inked wheel onto moving paper tape. Like the real clockwork
 * register, the tape starts moving at the first mark and stops after {@value #STOP_AFTER} ticks of
 * silence, so idle time does not waste tape. Recording is tick-exact; tape holds 10 minutes of motion.
 */
public class InkerBlockEntity extends CoilDeviceBlockEntity {
    public static final float COIL_OHMS = 50f;
    public static final double PICKUP_AMPS = 0.04;
    /** Register coil: 6 W continuous (~17 V), 0.4 A, 250 V, 105 °C. */
    public static final Spec SPEC = new Spec(105f, 6f, 1.5f, 250f, Spec.coil(COIL_OHMS, 0.4f));

    public static final int STOP_AFTER = 60;
    public static final int CAPACITY_TICKS = 12000;

    private boolean hasTape;
    private final List<Integer> runs = new ArrayList<>();
    private String label = "";
    private boolean running;
    private boolean curMark;
    private int curLen;
    private int total;

    public InkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        if (hasTape) {
            level.playSound(null, worldPosition, PgTelegraph.SOUNDER_DOWN.get(), SoundSource.BLOCKS,
                    0.25f, energized ? 1.6f : 1.4f);
        }
    }

    public boolean hasTape() {
        return hasTape;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !hasTape || isBurnt()) return;
        boolean mark = isEnergized();
        if (!running) {
            if (!mark || total >= CAPACITY_TICKS || runs.size() >= TapeData.MAX_RUNS - 2) return;
            // clockwork starts; keep the mark/space alternation
            if (runs.size() % 2 == 1) {
                runs.add(STOP_AFTER);
                total += STOP_AFTER;
            }
            running = true;
            curMark = true;
            curLen = 1;
            return;
        }
        if (total + curLen >= CAPACITY_TICKS || runs.size() >= TapeData.MAX_RUNS - 1) {
            endRun();
            running = false;
            return;
        }
        if (mark == curMark) {
            curLen++;
            if (!curMark && curLen >= STOP_AFTER) {
                endRun();
                running = false;
            }
        } else {
            endRun();
            curMark = mark;
            curLen = 1;
        }
        if (level.getGameTime() % 20 == 0) setChanged();
    }

    private void endRun() {
        if (curLen <= 0) return;
        runs.add(curLen);
        total += curLen;
        curLen = 0;
    }

    /** Insert a blank or previously inked tape. Returns false if rejected. */
    public boolean insert(ItemStack stack) {
        if (hasTape) return false;
        TapeData d = PaperTapeItem.data(stack);
        if (d != null && d.kind() != TapeData.INKED) return false;
        hasTape = true;
        runs.clear();
        total = 0;
        label = "";
        if (d != null) {
            runs.addAll(d.runs());
            total = d.total();
            label = d.label();
        }
        running = false;
        curLen = 0;
        updateState();
        return true;
    }

    public ItemStack extract() {
        ItemStack out = takeTape();
        if (!out.isEmpty()) updateState();
        return out;
    }

    /** Remove the tape without touching the block state (used when the block is broken). */
    public ItemStack takeTape() {
        if (!hasTape) return ItemStack.EMPTY;
        if (running) {
            endRun();
            running = false;
        }
        // a tape always ends on a mark or a space; drop a trailing space so the next session re-adds it
        if (runs.size() % 2 == 0 && !runs.isEmpty()) {
            total -= runs.remove(runs.size() - 1);
        }
        ItemStack out = PaperTapeItem.of(new TapeData(TapeData.INKED, runs, label));
        hasTape = false;
        runs.clear();
        total = 0;
        setChanged();
        return out;
    }

    private void updateState() {
        BlockState s = getBlockState();
        if (s.hasProperty(InkerBlock.HAS_TAPE) && s.getValue(InkerBlock.HAS_TAPE) != hasTape) {
            level.setBlock(worldPosition, s.setValue(InkerBlock.HAS_TAPE, hasTape), 3);
        }
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket || !hasTape) return;
        CompoundTag t = new CompoundTag();
        t.putIntArray("Runs", TapeData.toArray(runs));
        t.putString("Label", label);
        t.putBoolean("Running", running);
        t.putBoolean("CurMark", curMark);
        t.putInt("CurLen", curLen);
        tag.put("Tape", t);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        hasTape = tag.contains("Tape");
        runs.clear();
        total = 0;
        if (hasTape) {
            CompoundTag t = tag.getCompound("Tape");
            for (int r : t.getIntArray("Runs")) {
                runs.add(r);
                total += r;
            }
            label = t.getString("Label");
            running = t.getBoolean("Running");
            curMark = t.getBoolean("CurMark");
            curLen = t.getInt("CurLen");
        }
    }
}
