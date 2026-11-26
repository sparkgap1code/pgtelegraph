package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.sparkgap.pgtelegraph.item.AprsTrackerItem;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.Aprs;
import com.sparkgap.pgtelegraph.radio.Reception;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APRS station (APRS 수신국): a 2 m FM receiver and packet decoder on 144.390 MHz with a map
 * screen. Needs an antenna on its coax and 13.8 V (10 W). It plots every report it decodes that
 * carries its own network key (the two items on top); reports older than 20 minutes fade out.
 */
public class AprsStationBlockEntity extends PoweredStationBlockEntity implements IHaveGoggleInformation, AprsKeyed {
    public static final Spec SPEC = spec(13.8, 10);
    public static final int MAX = 40, KEEP_TICKS = 24000, MIN_SNR = 6;

    public record Entry(String name, double x, double y, double z, long tick, int kind, float snr) {}

    private final ItemStack[] keys = {ItemStack.EMPTY, ItemStack.EMPTY};
    /** name -> last report (server: what was decoded; client: synced) */
    public final Map<String, Entry> heard = new LinkedHashMap<>();
    private AntennaLink link;
    private int rescan;
    private boolean dirty, hasAntenna;
    private final Reception.TerrainCache cache = new Reception.TerrainCache();
    /** client: game time of the last sync, to age the entries */
    public long syncedAt;

    public AprsStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public double nominalV() {
        return 13.8;
    }

    @Override
    public double ratedW() {
        return 10;
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public ItemStack[] keySlots() {
        return keys;
    }

    @Override
    public void keyChanged() {
        heard.clear();
        setChanged();
        sendData();
    }

    @Override
    public double slotTopPx() {
        return 6;
    }

    public boolean hasAntenna() {
        return hasAntenna;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!powerTick()) return;
        long t = level.getGameTime();
        if (--rescan <= 0) {
            rescan = 40;
            link = AntennaLink.scan(level, worldPosition);
            boolean a = link != null && link.amplifier() == null;
            if (a != hasAntenna) {
                hasAntenna = a;
                dirty = true;
            }
        }
        if (hasAntenna && link != null) {
            var r = Reception.listen(level, link, Aprs.FREQ, 0, null, worldPosition.asLong(), cache, -125, 12_500);
            if (r.hasSignal() && r.bestEmission() != null && r.best() - r.noiseDbm() >= MIN_SNR) {
                Aprs.Packet p = Aprs.packet(level, r.bestEmission().source());
                if (p != null && p.key().equals(aprsKey())) {
                    Entry old = heard.remove(p.name());
                    heard.put(p.name(), new Entry(p.name(), p.x(), p.y(), p.z(), t, p.kind(), (float) (r.best() - r.noiseDbm())));
                    while (heard.size() > MAX) heard.remove(heard.keySet().iterator().next());
                    if (old == null || t - old.tick() > Aprs.BURST * 2) dirty = true;
                }
            }
        }
        if (t % 200 == 0) {
            int before = heard.size();
            heard.values().removeIf(e -> t - e.tick() > KEEP_TICKS);
            if (heard.size() != before) dirty = true;
        }
        if (dirty && t % 10 == 0) {
            dirty = false;
            setChanged();
            sendData();
        }
    }

    /** reports as plain maps, for computers */
    public List<Map<String, Object>> reports() {
        List<Map<String, Object>> out = new ArrayList<>();
        long t = level == null ? 0 : level.getGameTime();
        for (Entry e : heard.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.name());
            m.put("x", e.x());
            m.put("y", e.y());
            m.put("z", e.z());
            m.put("age", (t - e.tick()) / 20.0);
            m.put("kind", e.kind() == Aprs.VEHICLE ? "vehicle" : e.kind() == Aprs.FIXED ? "fixed" : "person");
            m.put("snr", (double) e.snr());
            out.add(m);
        }
        return out;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        AprsKeyed.save(keys, tag, r);
        ListTag l = new ListTag();
        for (Entry e : heard.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("N", e.name());
            c.putDouble("X", e.x());
            c.putDouble("Y", e.y());
            c.putDouble("Z", e.z());
            c.putLong("T", e.tick());
            c.putInt("K", e.kind());
            c.putFloat("S", e.snr());
            l.add(c);
        }
        tag.put("Heard", l);
        if (clientPacket) {
            tag.putBoolean("Ant", hasAntenna);
            tag.putLong("Now", level == null ? 0 : level.getGameTime());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        AprsKeyed.load(keys, tag, r);
        heard.clear();
        for (Tag x : tag.getList("Heard", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            heard.put(c.getString("N"), new Entry(c.getString("N"), c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z"),
                    c.getLong("T"), c.getInt("K"), c.getFloat("S")));
        }
        if (clientPacket) {
            hasAntenna = tag.getBoolean("Ant");
            syncedAt = tag.getLong("Now");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        if (!isPowered()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.no_power")).withStyle(ChatFormatting.RED));
            return true;
        }
        if (!hasAntenna) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.no_antenna")).withStyle(ChatFormatting.RED));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.aprs.key", AprsTrackerItem.keyText(aprsKey())))
                .withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.aprs.count", heard.size())).withStyle(ChatFormatting.GRAY));
        return true;
    }
}
