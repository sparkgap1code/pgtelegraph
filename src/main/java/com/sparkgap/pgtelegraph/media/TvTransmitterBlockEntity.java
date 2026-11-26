package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity;
import com.sparkgap.pgtelegraph.block.TransmitterBlockEntity;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Analog TV transmitter (TV 송신기): sends the live picture of a TV camera touching it, as a
 * television station does - 54-890 MHz (the VHF and UHF TV bands) built in, up to 1 kW of
 * peak sync power from a 250 V supply. The picture is the camera's real view, filmed by a nearby
 * player's client (TvFrames). INHIBIT shorted to COM takes it off the air.
 */
public class TvTransmitterBlockEntity extends TransmitterBlockEntity {
    public static final long LOW_HZ = 54_000_000L, HIGH_HZ = 890_000_000L;
    private BlockPos camera;
    private int camScan;
    /** synced for the goggles: 0 none, 1 TV camera, 2 VCR, 3 bodycam */
    private int shownSource;

    public TvTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        freqHz = 55_250_000L; // channel 2 vision carrier
    }

    public static final com.sparkgap.pgtelegraph.block.Spec TV_SPEC = com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity.BC_SPEC;

    @Override
    public com.sparkgap.pgtelegraph.block.Spec spec() {
        return TV_SPEC;
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
        return 0.55;
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
    protected double[] harmonics() {
        return null;
    }

    @Override
    protected boolean bandOk() {
        return freqHz >= LOW_HZ && freqHz <= HIGH_HZ;
    }

    @Override
    public void setFreq(long hz) {
        super.setFreq(com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity.inBand(hz, LOW_HZ, HIGH_HZ));
    }

    @Override
    public boolean acceptsVoice() {
        return false;
    }

    @Override
    public int airModulation() {
        return RadioWorld.MOD_TV;
    }

    /** on the air while it has a camera, unless INHIBIT is shorted to COM */
    @Override
    protected boolean keyInput() {
        return camera != null && !keyContact();
    }

    /** a picture source that may touch the transmitter: a TV camera, or a VCR that is playing */
    public static boolean isSource(Level level, BlockPos p) {
        return level.getBlockState(p).getBlock() instanceof TvCameraBlock || level.getBlockEntity(p) instanceof VcrBlockEntity v && v.playing();
    }

    public BlockPos camera() {
        return camera;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--camScan <= 0) {
            camScan = 20;
            camera = null;
            for (Direction d : Direction.values()) {
                BlockPos p = worldPosition.relative(d);
                if (isSource(level, p)) {
                    camera = p.immutable();
                    break;
                }
            }
        }
        // a paired bodycam worn nearby feeds it when no camera block touches it
        BlockPos body = BodycamItem.feeding(level, worldPosition);
        if (body != null && (camera == null || !isSource(level, camera))) camera = body;
        else if (body == null && camera != null && !isSource(level, camera)) camera = null;
        if (camera != null && body != camera) TvFrames.want(level, camera);
        int src = camera == null ? 0 : level.getBlockEntity(camera) instanceof VcrBlockEntity ? 2
                : level.getBlockState(camera).getBlock() instanceof TvCameraBlock ? 1 : 3;
        if (src != shownSource) {
            shownSource = src;
            sendData();
        }
        if (isKeyed() && camera != null) {
            TvAir.onAir(level, worldPosition, camera);
            if (link != null) for (BlockPos a : LinearAmplifierBlockEntity.chain(level, link)) TvAir.onAir(level, a, camera);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        // the client never sees the camera itself: the server sends what kind it is
        tip.add(Component.literal("     ").append(Component.translatable(switch (shownSource) {
                    case 1 -> "pgtelegraph.tv.camera";
                    case 2 -> "pgtelegraph.tv.vcr";
                    case 3 -> "pgtelegraph.tv.bodycam";
                    default -> "pgtelegraph.tv.no_camera";
                }))
                .withStyle(shownSource == 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        return true;
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) tag.putInt("Src", shownSource);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) shownSource = tag.getInt("Src");
    }

    /** whose picture a radiating block carries: the key of the camera (a TV camera block, a bodycam, a satellite) */
    public static final class TvAir {
        private TvAir() {}

        private static final Map<Level, Map<BlockPos, BlockPos>> SRC = new WeakHashMap<>();

        public static void onAir(Level level, BlockPos radiating, BlockPos camera) {
            SRC.computeIfAbsent(level, l -> new HashMap<>()).put(radiating.immutable(), camera.immutable());
        }

        public static BlockPos camera(Level level, BlockPos radiating) {
            Map<BlockPos, BlockPos> m = SRC.get(level);
            return m == null ? null : m.get(radiating);
        }
    }
}
