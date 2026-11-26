package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tape recorder (녹음기): records what a receiver touching it plays - tone pitch, tone level and
 * noise, tick by tick - onto a magnetic tape, and plays it back through its own speaker. A tape
 * holds 5 minutes. Empty hand: stop → record → play → stop; sneak + empty hand: eject the tape.
 */
public class TapeRecorderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, ToneSource {
    public static final int STOP = 0, RECORD = 1, PLAY = 2, MAX_TICKS = 6000, MAX_RUNS = 4000;

    private ItemStack tape = ItemStack.EMPTY;
    private int state = STOP;
    private List<Integer> rec = new ArrayList<>();
    private int ticks, playIndex, playLeft;
    // synced
    private int hz = 700;
    private float tone, noise;
    private final Object[] sounds = new Object[2];

    public TapeRecorderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public static int pack(int count, int hz, float tone, float noise) {
        return (Math.min(32767, count) << 16) | (Math.max(0, Math.min(255, hz / 10)) << 8)
                | (Math.round(Math.max(0, Math.min(1, tone)) * 15) << 4) | Math.round(Math.max(0, Math.min(1, noise / 0.35f)) * 15);
    }

    public boolean hasTape() {
        return !tape.isEmpty();
    }

    public void insert(ItemStack s) {
        tape = s.copyWithCount(1);
        state = STOP;
        setChanged();
        sendData();
    }

    public ItemStack eject() {
        stopAll();
        ItemStack t = tape;
        tape = ItemStack.EMPTY;
        setChanged();
        sendData();
        return t;
    }

    /** STOP → RECORD → PLAY → STOP; returns the new state */
    public int cycle() {
        if (tape.isEmpty()) return STOP;
        int next = (state + 1) % 3;
        stopAll();
        state = next;
        ticks = 0;
        if (state == RECORD) rec = new ArrayList<>();
        if (state == PLAY) {
            rec = new ArrayList<>(tapeData());
            playIndex = 0;
            playLeft = rec.isEmpty() ? 0 : rec.get(0) >>> 16;
        }
        setChanged();
        sendData();
        return state;
    }

    private List<Integer> tapeData() {
        List<Integer> l = tape.get(PgTelegraph.MAG_TAPE.get());
        return l == null ? List.of() : l;
    }

    private void stopAll() {
        if (state == RECORD && !tape.isEmpty()) tape.set(PgTelegraph.MAG_TAPE.get(), List.copyOf(rec));
        state = STOP;
        set(700, 0, 0);
    }

    private ReceiverBlockEntity source() {
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof ReceiverBlockEntity r) return r;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            if (state == PLAY) com.sparkgap.pgtelegraph.client.ClientAccess.ensureTone(this);
            return;
        }
        if (state == RECORD) {
            ReceiverBlockEntity r = source();
            int p = r == null || !r.isPowered() ? pack(1, 700, 0, 0) : pack(1, r.toneHz, r.toneVol, r.noiseVol);
            int last = rec.isEmpty() ? -1 : rec.get(rec.size() - 1);
            if (last != -1 && (last & 0xFFFF) == (p & 0xFFFF) && (last >>> 16) < 32767) rec.set(rec.size() - 1, last + (1 << 16));
            else rec.add(p);
            if (++ticks >= MAX_TICKS || rec.size() >= MAX_RUNS) {
                stopAll();
                setChanged();
                sendData();
            }
        } else if (state == PLAY) {
            if (playIndex >= rec.size()) {
                state = STOP;
                set(700, 0, 0);
                sendData();
                return;
            }
            int p = rec.get(playIndex);
            set(((p >> 8) & 0xFF) * 10, ((p >> 4) & 15) / 15f, (p & 15) / 15f * 0.35f);
            if (--playLeft <= 0) {
                playIndex++;
                if (playIndex < rec.size()) playLeft = rec.get(playIndex) >>> 16;
            }
        }
    }

    private void set(int h, float t, float n) {
        boolean changed = h != hz || Math.abs(t - tone) > 0.05f || Math.abs(n - noise) > 0.03f;
        hz = h;
        tone = t;
        noise = n;
        if (changed) sendData();
    }

    @Override
    public int soundHz() {
        return hz;
    }

    @Override
    public float soundTone() {
        return tone;
    }

    @Override
    public float soundNoise() {
        return noise;
    }

    @Override
    public boolean soundOn() {
        return state == PLAY;
    }

    @Override
    public Object[] clientSounds() {
        return sounds;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        String s = tape.isEmpty() ? "-" : state == RECORD ? "● REC" : state == PLAY ? "▶ PLAY" : "■ STOP";
        tip.add(Component.literal("     " + s).withStyle(state == RECORD ? ChatFormatting.RED : ChatFormatting.AQUA));
        if (!tape.isEmpty()) {
            int total = 0;
            for (int p : tapeData()) total += p >>> 16;
            tip.add(Component.literal(String.format(Locale.ROOT, "     %d:%02d / 5:00", total / 1200, total / 20 % 60)).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!tape.isEmpty()) tag.put("Tape", tape.save(registries));
        tag.putInt("State", state == RECORD && !clientPacket ? STOP : state);
        if (clientPacket) {
            tag.putInt("Hz", hz);
            tag.putFloat("Tone", tone);
            tag.putFloat("Noise", noise);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tape = tag.contains("Tape") ? ItemStack.parseOptional(registries, tag.getCompound("Tape")) : ItemStack.EMPTY;
        state = clientPacket ? tag.getInt("State") : STOP;
        if (clientPacket) {
            hz = tag.getInt("Hz");
            tone = tag.getFloat("Tone");
            noise = tag.getFloat("Noise");
        }
    }
}
