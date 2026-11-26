package com.sparkgap.pgtelegraph.broadcast;

import com.sparkgap.pgtelegraph.block.TransmitterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * AM broadcast transmitter (AM 방송 송신기), 150 kHz - 30 MHz: a plate-modulated tube transmitter, up to 1 kW of
 * carrier, 70 % efficient, that plays records on the air - imported recordings or music discs, up to
 * {@value #RACK} in its rack, one after another and round again (a playlist). The record keeps turning from the moment it went on (startTick): every radio plays
 * it from where the station is, so a radio switched on half a minute in starts half a minute in. Its carrier is on whenever a record is in and it is powered; KEY shorted to KEY COM
 * takes it off the air. Through a broadcast amplifier the programme goes out at full power too.
 */
public class BroadcastTransmitterBlockEntity extends TransmitterBlockEntity {
    public static final int RACK = 9;
    /** the playlist, in the order the records went in; {@code disc} is the one playing */
    private final java.util.ArrayList<ItemStack> rack = new java.util.ArrayList<>();
    private int idx;
    private ItemStack disc = ItemStack.EMPTY;
    private long start;
    private String title = "";

    public BroadcastTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        freqHz = 1_000_000L;
    }

    public static final com.sparkgap.pgtelegraph.block.Spec BC_SPEC = new com.sparkgap.pgtelegraph.block.Spec(150f, 600f, 20f, 600f,
            new com.sparkgap.pgtelegraph.block.Spec.Part("rf_out", 1000, 0, 1), com.sparkgap.pgtelegraph.block.Spec.pullup(1000f, 1),
            com.sparkgap.pgtelegraph.block.Spec.rail(5f));

    @Override
    public com.sparkgap.pgtelegraph.block.Spec spec() {
        return BC_SPEC;
    }

    @Override
    protected int maxPanelW() {
        return 1000;
    }

    @Override
    protected double paMaxW() {
        return 1000;
    }

    @Override
    protected double paEff() {
        return 0.7;
    }

    @Override
    protected double nominalV() {
        return 250;
    }

    @Override
    protected double minV() {
        return 120;
    }

    @Override
    protected boolean keyInput() {
        return (!disc.isEmpty() || com.sparkgap.pgtelegraph.media.VoiceAir.talking(level, worldPosition)) && !keyContact();
    }

    /** a microphone next to it goes out live (over the record) */
    @Override
    public boolean acceptsVoice() {
        return true;
    }

    /** AM only: long, medium and short wave (the FM broadcast transmitter does VHF) */
    @Override
    public boolean voiceFm() {
        return false;
    }

    public static final long AM_LOW_HZ = 150_000L, AM_HIGH_HZ = 29_999_000L;

    @Override
    protected boolean bandOk() {
        return freqHz >= lowHz() && freqHz <= highHz();
    }

    /** the band this transmitter is built for */
    protected long lowHz() {
        return AM_LOW_HZ;
    }

    protected long highHz() {
        return AM_HIGH_HZ;
    }

    @Override
    public void setFreq(long hz) {
        super.setFreq(inBand(hz, lowHz(), highHz()));
    }

    /** into the band (the nearest edge) */
    public static long inBand(long hz, long low, long high) {
        return Math.max(low, Math.min(high, hz));
    }

    @Override
    protected double[] harmonics() {
        return null;
    }

    public boolean hasDisc() {
        return !rack.isEmpty();
    }

    public boolean full() {
        return rack.size() >= RACK;
    }

    public int records() {
        return rack.size();
    }

    /** add a record to the end of the playlist (the first one starts playing at once) */
    public boolean insert(ItemStack s) {
        if (full() || program(s, 0) == null) return false;
        rack.add(s.copyWithCount(1));
        if (rack.size() == 1) play(0);
        if (level instanceof net.minecraft.server.level.ServerLevel sl) com.sparkgap.pgtelegraph.Awards.near(sl, worldPosition, 16, "broadcast");
        setChanged();
        sendData();
        return true;
    }

    private void play(int i) {
        idx = rack.isEmpty() ? 0 : Math.floorMod(i, rack.size());
        disc = rack.isEmpty() ? ItemStack.EMPTY : rack.get(idx);
        start = level == null ? 0 : level.getGameTime();
        Broadcasts.Program p = disc.isEmpty() ? null : program(disc, start);
        title = p == null ? "" : p.title();
    }

    /** take out the last record put in (the playing one goes on unless it was that one) */
    public ItemStack eject() {
        if (rack.isEmpty()) return ItemStack.EMPTY;
        ItemStack d = rack.remove(rack.size() - 1);
        if (rack.isEmpty()) {
            disc = ItemStack.EMPTY;
            title = "";
            idx = 0;
        } else if (idx >= rack.size()) play(0);
        setChanged();
        sendData();
        return d;
    }

    /** everything in the rack (the block is broken) */
    public java.util.List<ItemStack> ejectAll() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>(rack);
        rack.clear();
        disc = ItemStack.EMPTY;
        title = "";
        idx = 0;
        setChanged();
        return out;
    }

    private Broadcasts.Program program(ItemStack s, long tick) {
        RecordItems.Audio a = RecordItems.Audio.of(s);
        if (a != null) return new Broadcasts.Program(a.id(), null, start, Math.max(20, a.seconds() * 20), a.title(), tick);
        if (level == null) return null;
        var song = JukeboxSong.fromStack(level.registryAccess(), s);
        if (song.isEmpty()) return null;
        JukeboxSong js = song.get().value();
        return new Broadcasts.Program(null, js.soundEvent().value().getLocation().toString(), start, js.lengthInTicks(),
                js.description().getString(), tick);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || disc.isEmpty()) return;
        // the next record when this one is done (round again after the last)
        Broadcasts.Program cur = program(disc, level.getGameTime());
        if (cur != null && level.getGameTime() - start >= cur.lengthTicks() && rack.size() > 1) {
            play(idx + 1);
            setChanged();
            sendData();
        }
        if (!isKeyed()) return;
        Broadcasts.Program p = program(disc, level.getGameTime());
        if (p == null) return;
        Broadcasts.put(level, worldPosition, p);
        for (BlockPos a : com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity.chain(level, link)) Broadcasts.put(level, a, p);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        Broadcasts.Program p = disc.isEmpty() || level == null ? null : program(disc, level.getGameTime());
        String at = p == null ? "" : "  " + Broadcasts.position(level.getGameTime(), start, p.lengthTicks());
        tip.add(Component.literal("     ♫ ").append(title.isEmpty() ? Component.translatable("pgtelegraph.broadcast.no_disc")
                : Component.literal(title + at)).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (rack.size() > 1) tip.add(Component.literal(String.format(java.util.Locale.ROOT, "     %d / %d", idx + 1, rack.size()))
                .withStyle(ChatFormatting.DARK_PURPLE));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (ItemStack d : rack) l.add(d.save(registries));
        tag.put("Rack", l);
        tag.putInt("Idx", idx);
        tag.putLong("Start", start);
        tag.putString("Title", title);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rack.clear();
        if (tag.contains("Rack")) {
            for (net.minecraft.nbt.Tag t : tag.getList("Rack", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                ItemStack d = ItemStack.parseOptional(registries, (CompoundTag) t);
                if (!d.isEmpty()) rack.add(d);
            }
        } else if (tag.contains("Disc")) { // saved before playlists
            ItemStack d = ItemStack.parseOptional(registries, tag.getCompound("Disc"));
            if (!d.isEmpty()) rack.add(d);
        }
        idx = rack.isEmpty() ? 0 : Math.floorMod(tag.getInt("Idx"), rack.size());
        disc = rack.isEmpty() ? ItemStack.EMPTY : rack.get(idx);
        start = tag.getLong("Start");
        title = tag.getString("Title");
    }
}
