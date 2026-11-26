package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Band choice (whip, yagi) and aim (yagi, dish) of a VHF/UHF/SHF antenna. */
public class VhfAntennaBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public interface BandOption extends INamedIconOptions {
        double mhz();

        @Override
        default AllIcons getIcon() {
            return AllIcons.I_TARGET;
        }
    }

    /** HF bands a 1 m helical whip can be wound for */
    public enum HfBand implements BandOption {
        B160M(1.83), B80M(3.55), B40M(7.05), B30M(10.12), B20M(14.06), B17M(18.09), B15M(21.06), B12M(24.91), B10M(28.06), B6M(50.1);
        private final double mhz;

        HfBand(double mhz) {
            this.mhz = mhz;
        }

        public double mhz() {
            return mhz;
        }

        @Override
        public String getTranslationKey() {
            return "pgtelegraph.vhf.hfband." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** UHF/SHF bands for an axial helix */
    public enum ShfBand implements BandOption {
        B23CM(1296.1), B13CM(2304.1), B9CM(3400.1), B6CM(5760.1), B3CM(10368.1);
        private final double mhz;

        ShfBand(double mhz) {
            this.mhz = mhz;
        }

        public double mhz() {
            return mhz;
        }

        @Override
        public String getTranslationKey() {
            return "pgtelegraph.vhf.shfband." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Band implements BandOption {
        B6M(50.1), B2M(144.1), B125CM(222.1), B70CM(432.1), B33CM(903), B23CM(1296.1),
        /** FM broadcast band (appended last so saved choices keep their meaning) */
        BFM(98.0);
        public final double mhz;

        Band(double mhz) {
            this.mhz = mhz;
        }

        public double mhz() {
            return mhz;
        }

        @Override
        public String getTranslationKey() {
            return "pgtelegraph.vhf.band." + name().toLowerCase(Locale.ROOT);
        }
    }

    private ScrollOptionBehaviour<?> band;
    private double az, el;
    private long loopHz = 17_200;
    /** automatic tracking: manual, the moon, or a satellite */
    public static final int MANUAL = 0, MOON = 1, SATELLITE = 2;
    private int track = MANUAL;
    /** satellite to follow; empty = whichever is highest */
    private String trackName = "";
    /** synced for the goggles: what is being followed now */
    private String tracking = "";

    public int track() {
        return track;
    }

    public String trackName() {
        return trackName;
    }

    public void setTrack(int mode, String name) {
        track = Math.max(0, Math.min(2, mode));
        trackName = name == null ? "" : name.trim();
        if (track == MANUAL) tracking = "";
        setChanged();
        sendData();
    }

    /** aim set by hand or by a rotator: tracking stops */
    public void manualAim(double azimuthDeg, double elevationDeg) {
        track = MANUAL;
        tracking = "";
        setAim(azimuthDeg, elevationDeg);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || track == MANUAL || !type().aimed()) return;
        if ((level.getGameTime() + worldPosition.asLong()) % 10 != 0) return;
        double tAz, tEl;
        String what;
        if (track == MOON) {
            double a = level.getTimeOfDay(1f) * 2 * Math.PI;
            double x = Math.sin(a), y = -Math.cos(a);
            tEl = Math.toDegrees(Math.asin(y));
            tAz = x >= 0 ? 90 : 270;
            what = tEl > 0 ? "moon" : "moon (below horizon)";
            if (tEl < 0) tEl = 0;
        } else {
            var sat = trackName.isEmpty() ? com.sparkgap.pgtelegraph.sat.SatelliteData.highest(level, worldPosition)
                    : com.sparkgap.pgtelegraph.sat.SatelliteData.named(level, trackName);
            if (sat == null) {
                setTracking(trackName.isEmpty() ? "no satellite up" : trackName + "?");
                return;
            }
            var look = com.sparkgap.pgtelegraph.sat.Orbit.look(sat.orbit, worldPosition, level.getGameTime());
            if (!look.visible()) {
                long rise = com.sparkgap.pgtelegraph.sat.Orbit.nextRise(sat.orbit, worldPosition, level.getGameTime());
                setTracking(sat.name + (rise > 0 ? String.format(Locale.ROOT, " rises in %d:%02d", rise / 1200, rise / 20 % 60) : " below horizon"));
                return;
            }
            tAz = look.azDeg();
            tEl = look.elDeg();
            what = sat.name;
        }
        setTracking(what);
        double dAz = Math.abs(Mth.wrapDegrees(tAz - az));
        if (dAz > 0.5 || Math.abs(tEl - el) > 0.5) setAim(tAz, tEl);
    }

    private void setTracking(String s) {
        if (!s.equals(tracking)) {
            tracking = s;
            sendData();
        }
    }

    public VhfAntennaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private VhfAntennaBlock.Type type() {
        return getBlockState().getBlock() instanceof VhfAntennaBlock b ? b.type : VhfAntennaBlock.Type.WHIP;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // on the outer face of the body: the whip's base is 6 px wide (face at 11), the yagi and the
        // helix fill 1..15 (face at 15), the array tile is a whole block wide (face at 16)
        double faceZ = switch (type()) {
            case WHIP, HELICAL_WHIP -> 11;
            case ARRAY -> 16;
            default -> 15;
        };
        var box = ValueBoxes.baseSide(1.5, faceZ, 0.4f);
        Component label = Component.translatable("pgtelegraph.vhf.band");
        switch (type()) {
            case WHIP, YAGI, ARRAY -> {
                band = new ScrollOptionBehaviour<>(Band.class, label, this, box);
                band.value = Band.B2M.ordinal();
            }
            case HELICAL_WHIP -> {
                band = new ScrollOptionBehaviour<>(HfBand.class, label, this, box);
                band.value = HfBand.B40M.ordinal();
            }
            case HELIX -> {
                band = new ScrollOptionBehaviour<>(ShfBand.class, label, this, box);
                band.value = ShfBand.B13CM.ordinal();
            }
            default -> {
                return;
            }
        }
        behaviours.add(band);
    }

    public double bandMHz() {
        if (type() == VhfAntennaBlock.Type.LOOP) return loopHz / 1e6;
        return band == null ? Band.B2M.mhz : ((BandOption) band.get()).mhz();
    }

    public long loopHz() {
        return loopHz;
    }

    public void setLoopFreq(long hz) {
        loopHz = Math.max(10_000L, Math.min(30_000_000L, hz));
        setChanged();
        sendData();
    }

    public double azimuth() {
        return az;
    }

    public double elevation() {
        return el;
    }

    /** Unit vector of the beam: azimuth 0 = north (-Z), 90 = east (+X). */
    public Vec3 aim() {
        double a = Math.toRadians(az), e = Math.toRadians(el);
        return new Vec3(Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e));
    }

    public void setAim(double azimuthDeg, double elevationDeg) {
        az = Mth.positiveModulo(azimuthDeg, 360);
        el = Mth.clamp(elevationDeg, -10, 90);
        if (level != null && !level.isClientSide) {
            Direction face = Direction.fromYRot(az + 180); // compass bearing -> MC yaw
            BlockState s = getBlockState();
            if (s.hasProperty(VhfAntennaBlock.FACING) && s.getValue(VhfAntennaBlock.FACING) != face) {
                level.setBlock(worldPosition, s.setValue(VhfAntennaBlock.FACING, face), 3);
            }
        }
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        if (type() == VhfAntennaBlock.Type.DISH)
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.vhf.dish_band")).withStyle(ChatFormatting.AQUA));
        if (band != null || type() == VhfAntennaBlock.Type.LOOP) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.vhf.band")).append(": ")
                    .append(Component.literal(RadioMath.formatMHz(bandMHz())).withStyle(ChatFormatting.AQUA)));
        }
        if (type().aimed()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.vhf.aim")).append(": ")
                    .append(Component.literal(String.format(Locale.ROOT, "%.1f° / %.1f°", az, el)).withStyle(ChatFormatting.GOLD)));
            if (track != MANUAL) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.vhf.track." + track))
                    .append(": " + tracking).withStyle(ChatFormatting.AQUA));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("Az", az);
        tag.putDouble("El", el);
        tag.putLong("LoopHz", loopHz);
        tag.putInt("Track", track);
        tag.putString("TrackName", trackName);
        if (clientPacket) tag.putString("Tracking", tracking);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        az = tag.getDouble("Az");
        el = tag.getDouble("El");
        if (tag.contains("LoopHz")) loopHz = tag.getLong("LoopHz");
        track = tag.getInt("Track");
        trackName = tag.getString("TrackName");
        if (clientPacket) tracking = tag.getString("Tracking");
    }
}
