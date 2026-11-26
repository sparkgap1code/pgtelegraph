package com.sparkgap.pgtelegraph.media;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * VCR (비디오 녹화기). Takes a video tape. Empty hand cycles STOP / PLAY / LOOP / REC.
 * <ul>
 *   <li>PLAY / LOOP: plays the tape at 15 frames a second. Its RF output puts the picture on channel 3
 *   (61.25 MHz) at a microwatt - a TV set on the same table picks it up - and a TV transmitter
 *   touching it sends it out instead of a camera.</li>
 *   <li>REC: records a TV set touching it (the picture as that set receives it: snow, washed-out
 *   colour and ghosts burnt in) or, with no set, a TV camera touching it. Recording goes on after
 *   what is already on the tape (nothing is lost by cycling past REC); it starts after a second of
 *   pre-roll, so clicking through REC records nothing. Wipe a tape by hand (sneak + use it twice).</li>
 * </ul>
 */
public class VcrBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int STOP = 0, PLAY = 1, LOOP = 2, REC = 3;
    public static final long RF_HZ = 61_250_000L;
    private static final String[] MODES = {"STOP", "PLAY", "LOOP", "REC"};
    private ItemStack tape = ItemStack.EMPTY;
    private int mode;
    private int pos;
    private double acc;
    private int recSeq = -1;
    private long lastRec, recFrom;
    private boolean onAir;
    // synced
    private int frames;

    public VcrBlockEntity(BlockEntityType<?> type, BlockPos p, BlockState state) {
        super(type, p, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean hasTape() {
        return !tape.isEmpty();
    }

    public boolean playing() {
        return (mode == PLAY || mode == LOOP) && hasTape();
    }

    public void insert(ItemStack s) {
        tape = s.copyWithCount(1);
        mode = STOP;
        pos = 0;
        frames = level != null && level.getServer() != null ? VideoStore.frames(level.getServer(), VideoTapeItem.id(tape)) : 0;
        setChanged();
        sendData();
    }

    public ItemStack eject() {
        stopRec();
        ItemStack t = tape;
        tape = ItemStack.EMPTY;
        mode = STOP;
        quiet();
        setChanged();
        sendData();
        return t;
    }

    public String cycle() {
        if (!hasTape()) return null;
        stopRec();
        mode = (mode + 1) % 4;
        if (mode == PLAY || mode == STOP) pos = 0;
        if (mode == REC) { // record on after what is there, after a second of pre-roll
            String id = VideoTapeItem.ensureId(tape);
            frames = VideoStore.frames(level.getServer(), id);
            recSeq = -1;
            recFrom = level.getGameTime() + 20;
        }
        setChanged();
        sendData();
        return MODES[mode];
    }

    private void stopRec() {
        if (mode == REC && hasTape() && level != null && level.getServer() != null)
            VideoTapeItem.setInfo(tape, null, VideoStore.frames(level.getServer(), VideoTapeItem.id(tape)));
    }

    private void quiet() {
        if (onAir && level != null) {
            RadioWorld.silence(level, worldPosition);
            onAir = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long t = level.getGameTime();
        if (playing()) {
            String id = VideoTapeItem.id(tape);
            frames = VideoStore.frames(level.getServer(), id);
            if (frames > 0) {
                acc += VideoStore.FPS / 20.0;
                boolean next = pos == 0 && acc <= VideoStore.FPS / 20.0;
                while (acc >= 1) {
                    acc--;
                    pos++;
                    next = true;
                }
                if (pos >= frames) {
                    if (mode == LOOP) pos = 0;
                    else {
                        mode = STOP;
                        pos = 0;
                        quiet();
                        sendData();
                        return;
                    }
                }
                if (next) {
                    byte[] f = VideoStore.read(level.getServer(), id, pos);
                    if (f != null) TvFrames.put(level, worldPosition, f);
                }
                // RF modulator: channel 3 at a microwatt, for the set on the same table
                RadioWorld.emit(level, new RadioWorld.Emission(worldPosition, worldPosition, RF_HZ, RadioMath.dbm(1e-6), 1, t, null, 360, 0));
                RadioWorld.setModulation(level, worldPosition, RadioWorld.MOD_TV);
                TvTransmitterBlockEntity.TvAir.onAir(level, worldPosition, worldPosition);
                onAir = true;
                if (t % 20 == 0) sendData();
                return;
            }
        }
        quiet();
        if (mode == REC && hasTape()) record(t);
    }

    private void record(long t) {
        if (t < recFrom) return; // pre-roll
        String id = VideoTapeItem.ensureId(tape);
        int n = VideoStore.frames(level.getServer(), id);
        if (n >= VideoStore.MAX_FRAMES) {
            stopRec();
            mode = STOP;
            sendData();
            return;
        }
        BlockPos key = null;
        double snr = 99, ghost = 0;
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockEntity(p) instanceof TvReceiverBlockEntity rx && rx.tuned && rx.picKey() != null) {
                key = rx.picKey();
                snr = rx.snr;
                ghost = rx.ghost;
                break;
            }
        }
        if (key == null)
            for (Direction d : Direction.values()) {
                BlockPos p = worldPosition.relative(d);
                if (level.getBlockState(p).getBlock() instanceof TvCameraBlock) {
                    key = p.immutable();
                    TvFrames.want(level, key);
                    break;
                }
            }
        if (key == null || snr < -6) return;
        TvFrames.Frame f = TvFrames.frame(level, key);
        // 15 frames a second at most: take the newest frame each time one is due
        if (f == null || f.seq() == recSeq || System.nanoTime() - lastRec < 1_000_000_000L / VideoStore.FPS - 2_000_000) return;
        recSeq = f.seq();
        lastRec = System.nanoTime();
        BlockPos k = key.immutable();
        java.util.List<TvFrames.Frame> g = TvFrames.gop(level, k); // copied here, on the server thread
        VideoStore.appendReceived(level.getServer(), id, () -> TvFrames.rgb(k, g), snr, ghost);
        frames = n + 1;
        if (t % 20 == 0) {
            VideoTapeItem.setInfo(tape, null, frames);
            setChanged();
            sendData();
        }
    }

    @Override
    public void remove() {
        quiet();
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        if (!hasTape()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.vcr.no_tape")).withStyle(ChatFormatting.GRAY));
            return true;
        }
        String title = VideoTapeItem.tag(tape).getString("Title");
        if (!title.isEmpty()) tip.add(Component.literal("     " + title).withStyle(ChatFormatting.WHITE));
        int sec = mode == REC ? frames / VideoStore.FPS : pos / VideoStore.FPS;
        tip.add(Component.literal("     " + MODES[mode] + "  " + VideoTapeItem.time(sec) + " / " + VideoTapeItem.time(frames / VideoStore.FPS))
                .withStyle(mode == REC ? ChatFormatting.RED : mode == STOP ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        tip.add(Component.literal("     RF CH3 61.25 MHz").withStyle(ChatFormatting.DARK_AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (!tape.isEmpty()) tag.put("Tape", tape.save(reg));
        tag.putInt("Mode", mode);
        tag.putInt("Pos", pos);
        tag.putInt("Frames", frames);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        tape = tag.contains("Tape") ? ItemStack.parseOptional(reg, tag.getCompound("Tape")) : ItemStack.EMPTY;
        mode = tag.getInt("Mode");
        pos = tag.getInt("Pos");
        frames = tag.getInt("Frames");
    }
}
