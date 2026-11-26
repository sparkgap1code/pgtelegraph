package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** Shared parts of radios: dial frequency, the antenna link (rescanned every 2 s), goggle info. */
public abstract class RadioBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation,
        com.sparkgap.pgtelegraph.parts.PartsHolder, Tunable {
    public static final long MIN_HZ = 10_000L, MAX_HZ = 30_000_000_000L;

    protected long freqHz = 7_030_000L;
    protected AntennaLink link;
    private int rescan;
    /** synced for the goggle overlay */
    protected String linkText = "";
    protected double linkSwr;

    protected RadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private it.unimi.dsi.fastutil.longs.LongSet heldChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    protected void claimChunks(com.sparkgap.pgtelegraph.radio.AntennaLink l) {
        heldChunks = com.sparkgap.pgtelegraph.radio.StationChunks.update(level, worldPosition, heldChunks,
                keepsStationAwake() ? com.sparkgap.pgtelegraph.radio.StationChunks.wanted(worldPosition, l)
                        : new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
    }

    /** whether this station keeps its chunks (itself, coax, antenna) loaded while nobody is near */
    protected boolean keepsStationAwake() {
        return true;
    }

    protected void releaseChunks() {
        if (level != null && !level.isClientSide) com.sparkgap.pgtelegraph.radio.StationChunks.release(level, worldPosition, heldChunks);
        heldChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    }

    // ---------------- parts bay ----------------
    private net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> parts;
    private com.sparkgap.pgtelegraph.parts.Chassis.Stats stats;
    private int fuseOver;

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> parts() {
        // a freshly built radio is an empty case: the parts are fitted by hand
        if (parts == null) parts = net.minecraft.core.NonNullList.withSize(layout().slots.length, net.minecraft.world.item.ItemStack.EMPTY);
        return parts;
    }

    @Override
    public void setParts(java.util.List<net.minecraft.world.item.ItemStack> list) {
        parts = net.minecraft.core.NonNullList.withSize(layout().slots.length, net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < list.size() && i < parts.size(); i++) parts.set(i, list.get(i).copy());
        stats = null;
        onPartsChanged();
        setChanged();
        if (level != null && !level.isClientSide) sendData(); // the goggles show what is still missing
    }

    protected void onPartsChanged() {}

    /** slots a working radio of this kind needs (what used to come fitted) */
    private java.util.EnumSet<com.sparkgap.pgtelegraph.parts.PartSlot> requiredSlots() {
        var S = com.sparkgap.pgtelegraph.parts.PartSlot.class;
        return switch (layout()) {
            case TRANSMITTER -> java.util.EnumSet.of(com.sparkgap.pgtelegraph.parts.PartSlot.FUSE,
                    com.sparkgap.pgtelegraph.parts.PartSlot.PA, com.sparkgap.pgtelegraph.parts.PartSlot.BAND);
            case RECEIVER -> java.util.EnumSet.of(com.sparkgap.pgtelegraph.parts.PartSlot.FUSE,
                    com.sparkgap.pgtelegraph.parts.PartSlot.REGULATOR, com.sparkgap.pgtelegraph.parts.PartSlot.LNA,
                    com.sparkgap.pgtelegraph.parts.PartSlot.IF_FILTER, com.sparkgap.pgtelegraph.parts.PartSlot.BAND);
            default -> java.util.EnumSet.noneOf(S);
        };
    }

    /** synced: bit i = required slot i of the layout is empty (shown on the goggles) */
    private int missingParts;

    /** false for sets that are not built from parts (spark transmitter, creative sets) */
    public boolean needsParts() {
        return !creative();
    }

    private int computeMissing() {
        if (!needsParts()) return 0;
        int m = 0;
        var req = requiredSlots();
        var p = parts();
        for (int i = 0; i < layout().slots.length && i < p.size(); i++)
            if (req.contains(layout().slots[i]) && p.get(i).isEmpty()) m |= 1 << i;
        return m;
    }

    /*
     * The parts stay in the case: broken, the radio drops as an item that still carries them (like a
     * shulker box), and placed again it has them back. Only a freshly crafted radio is empty.
     */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (parts != null && parts.stream().anyMatch(st -> !st.isEmpty()))
            components.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(parts));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var c = input.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (c != null) {
            var list = net.minecraft.core.NonNullList.withSize(layout().slots.length, net.minecraft.world.item.ItemStack.EMPTY);
            c.copyInto(list);
            parts = list;
            stats = null;
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Parts");
    }

    /** parts were changed in place (wear): recompute their numbers */
    protected void invalidateStats() {
        stats = null;
        setChanged();
    }

    /** creative radios: no supply, no parts, no burning */
    public boolean creative() {
        return false;
    }

    public com.sparkgap.pgtelegraph.parts.Chassis.Stats stats() {
        if (creative()) return com.sparkgap.pgtelegraph.parts.Chassis.CREATIVE;
        if (stats == null) stats = com.sparkgap.pgtelegraph.parts.Chassis.stats(parts(), layout());
        return stats;
    }

    /**
     * Fuse in the supply lead: false when there is no (good) fuse. A current above its rating for
     * half a second blows it - the fuse burns instead of the radio.
     */
    protected boolean fuseOk(double amps) {
        if (!needsParts()) return true;
        double rating = stats().fuseA();
        if (rating == Double.MAX_VALUE) return false; // no fuse fitted: no supply
        if (amps > rating) {
            if (++fuseOver >= 10) {
                int i = layout().indexOf(com.sparkgap.pgtelegraph.parts.PartSlot.FUSE);
                java.util.List<net.minecraft.world.item.ItemStack> p = new java.util.ArrayList<>(parts());
                p.set(i, com.sparkgap.pgtelegraph.parts.Wreckage.burnt(p.get(i)));
                setParts(p);
                fuseOver = 0;
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 2f);
                return false;
            }
        } else fuseOver = Math.max(0, fuseOver - 1);
        return true;
    }

    /** FSK (RTTY) mode: the transmitter sends a steady carrier, KEY open shifts it down 170 Hz (space);
     *  the receiver's OUT follows mark vs space instead of carrier on/off. */
    public static final long FSK_SHIFT = 170;
    /** emission mode: CW, FSK (RTTY), AM voice, FM voice, SSB voice (upper sideband, suppressed carrier) */
    public static final int CW = 0, FSK = 1, AM = 2, FM = 3, SSB = 4,
            /** wide (broadcast) FM - receivers only */
            WFM = 5;

    /** the highest mode this set has (receivers also have WFM) */
    protected int maxMode() {
        return SSB;
    }
    protected int mode;

    public boolean fsk() {
        return mode == FSK;
    }

    public int mode() {
        return mode;
    }

    /** CTCSS tone index (0 = off): sent under the signal, or needed to open the squelch */
    protected int ctcss;

    public int ctcss() {
        return ctcss;
    }

    public void setCtcss(int i) {
        ctcss = com.sparkgap.pgtelegraph.radio.Ctcss.clamp(i);
        setChanged();
        sendData();
    }

    public boolean voiceMode() {
        return mode == AM || mode == FM || mode == SSB || mode == WFM;
    }

    public void setMode(int m) {
        mode = Math.max(CW, Math.min(maxMode(), m));
        setChanged();
        sendData();
    }

    public long freqHz() {
        return freqHz;
    }

    public void setFreq(long hz) {
        freqHz = Math.max(MIN_HZ, Math.min(MAX_HZ, hz));
        rescan = 0;
        setChanged();
        sendData();
    }

    /** reference-oscillator drift at this spot's temperature (Hz) */
    public long driftHz() {
        if (level == null) return 0;
        double tempC = com.sparkgap.pgtelegraph.radio.Climate.tempC(level, worldPosition);
        int oi = layout().indexOf(com.sparkgap.pgtelegraph.parts.PartSlot.OSC);
        var osc = oi < 0 ? null : com.sparkgap.pgtelegraph.parts.PartItem.def(parts().get(oi));
        if (osc != null && osc.id().equals("osc_gpsdo") && !gpsLock()) {
            // no sky, no GPS: the disciplined oscillator coasts on its OCXO
            double x = Math.max(-1, Math.min(1, (tempC - 25) / 30));
            return Math.round(freqHz * 0.1e-6 * x);
        }
        return stats().driftHz(freqHz, tempC);
    }

    /** a GPSDO locks when the radio can see the sky (its GPS patch is on the case lid) */
    public boolean gpsLock() {
        return level != null && level.canSeeSky(worldPosition.above());
    }

    public double fMHz() {
        return freqHz / 1e6;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--rescan <= 0) {
            rescan = 40;
            link = AntennaLink.scan(level, worldPosition);
            claimChunks(link);
            String t = link == null ? "" : link.describe() + ", " + link.coaxMeters() + " m coax";
            double swr = link == null ? 0 : com.sparkgap.pgtelegraph.radio.RadioMath.swr(
                    link.reflection(fMHz()));
            if (!t.equals(linkText) || Math.abs(swr - linkSwr) > 0.05) {
                linkText = t;
                linkSwr = swr;
                sendData();
            }
        }
    }

    @Override
    public void remove() {
        releaseChunks();
        super.remove();
    }

    public static String formatFreq(long hz) {
        return com.sparkgap.pgtelegraph.radio.RadioMath.formatMHz(hz / 1e6);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.freq"))
                .append(": ").append(Component.literal(formatFreq(freqHz)).withStyle(ChatFormatting.AQUA)));
        if (linkText.isEmpty()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.no_antenna")
                    .withStyle(ChatFormatting.RED)));
        } else {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.antenna"))
                    .append(": ").append(Component.literal(linkText).withStyle(ChatFormatting.GRAY)));
            ChatFormatting c = linkSwr < 1.5 ? ChatFormatting.GREEN : linkSwr < 3 ? ChatFormatting.YELLOW : ChatFormatting.RED;
            tip.add(Component.literal("     SWR: ").append(Component.literal(
                    linkSwr > 99 ? ">99" : String.format(Locale.ROOT, "%.1f", linkSwr)).withStyle(c)));
        }
        long drift = driftHz();
        if (Math.abs(drift) >= 1) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.drift"))
                .append(String.format(Locale.ROOT, ": %+d Hz", drift)).withStyle(ChatFormatting.DARK_GRAY));
        if (isBurnt()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.burnt")
                .withStyle(ChatFormatting.DARK_RED)));
        if (missingParts != 0) {
            net.minecraft.network.chat.MutableComponent names = Component.empty();
            boolean first = true;
            for (int i = 0; i < layout().slots.length; i++) {
                if ((missingParts >> i & 1) == 0) continue;
                if (!first) names.append(", ");
                names.append(Component.translatable("pgtelegraph.slot." + layout().slots[i].name().toLowerCase(Locale.ROOT)));
                first = false;
            }
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.missing_parts", names)
                    .withStyle(ChatFormatting.GOLD)));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("Freq", freqHz);
        tag.putInt("Mode", mode);
        if (ctcss != 0) tag.putInt("Ctcss", ctcss);
        if (!clientPacket) tag.put("Parts", net.minecraft.world.ContainerHelper.saveAllItems(new net.minecraft.nbt.CompoundTag(),
                (net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack>) parts(), true, registries));
        if (clientPacket) {
            tag.putInt("Missing", computeMissing());
            tag.putString("LinkText", linkText);
            tag.putDouble("LinkSwr", linkSwr);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        mode = tag.contains("Mode") ? tag.getInt("Mode") : tag.getBoolean("Fsk") ? FSK : CW;
        ctcss = com.sparkgap.pgtelegraph.radio.Ctcss.clamp(tag.getInt("Ctcss"));
        if (!clientPacket && !tag.contains("Parts") && tag.contains("Freq") && parts == null) {
            // saved before radios had a parts bay: it keeps the kit it was working with
            parts = com.sparkgap.pgtelegraph.parts.Chassis.factory(layout());
            stats = null;
        }
        if (!clientPacket && tag.contains("Parts")) {
            var list = net.minecraft.core.NonNullList.withSize(layout().slots.length, net.minecraft.world.item.ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("Parts"), list, registries);
            parts = list;
            stats = null;
        }
        if (clientPacket) {
            missingParts = tag.getInt("Missing");
            linkText = tag.getString("LinkText");
            linkSwr = tag.getDouble("LinkSwr");
        }
    }
}
