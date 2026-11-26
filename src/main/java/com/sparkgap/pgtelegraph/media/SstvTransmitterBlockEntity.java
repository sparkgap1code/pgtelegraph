package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TransmitterBlockEntity;
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
import java.util.Locale;

/**
 * SSTV transmitter: a transmitter (same parts, power and antenna as the CW one) that sends a
 * colour picture as audio in a real SSTV mode (SstvMode: Martin, Scottie, Robot 36, PD50-PD290 -
 * resolution and frame time as the real ones, chosen in the tuning screen). The picture is the
 * photo put on it, or else the live view of a TV camera touching it, scanned line by line as it goes
 * out. A frame starts when a photo goes on, on sneak + empty hand, or while KEY is shorted to
 * KEY COM (held: one frame after another). Default 14.230 MHz, the HF SSTV calling spot.
 */
public class SstvTransmitterBlockEntity extends TransmitterBlockEntity {
    public static final int LINE_TICKS = 4;
    private ItemStack photo = ItemStack.EMPTY;
    private long frameStart = -1;
    private int curLine = -1;
    private byte[] curRow = new byte[Pic.ROW];
    private byte[] picture;
    private CameraRender.View camView;
    /** the camera's real (filmed) picture for this frame: {w, h, rgb...}, or null to ray-cast */
    private int[] filmed;
    /** SstvMode ordinal (synced) */
    private int sstvMode = SstvMode.PD50.ordinal();

    public SstvMode sstvMode() {
        return SstvMode.of(sstvMode);
    }

    public void setSstvMode(int ordinal) {
        sstvMode = Math.floorMod(ordinal, SstvMode.values().length);
        frameStart = -1;
        setChanged();
        sendData();
    }
    // synced
    private int shownLine = -1;

    public SstvTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        freqHz = 14_230_000L;
    }

    @Override
    public boolean acceptsVoice() {
        return false;
    }

    public boolean hasPhoto() {
        return !photo.isEmpty();
    }

    public void insert(ItemStack s) {
        photo = s;
        picture = null;
        frameStart = -1;
        startFrame();
        setChanged();
        sendData();
    }

    public ItemStack eject() {
        ItemStack d = photo;
        photo = ItemStack.EMPTY;
        frameStart = -1;
        setChanged();
        sendData();
        return d;
    }

    private BlockPos camera() {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(worldPosition.relative(d)).is(PgTelegraph.TV_CAMERA.get())) return worldPosition.relative(d);
        }
        return null;
    }

    /** start a frame now (true when one is going out) */
    public boolean startFrame() {
        if (level == null) return false;
        if (frameStart >= 0) return true;
        if (photo.isEmpty() && camera() == null) return false;
        frameStart = level.getGameTime();
        curLine = -1;
        picture = null;
        filmed = null;
        BlockPos cam = camera();
        if (photo.isEmpty() && cam != null) { // the camera's real view, if a player is filming it
            TvFrames.Frame f = TvFrames.frame(level, cam);
            if (f != null && f.real()) filmed = TvFrames.rgb(level, cam);
        }
        return true;
    }

    private int line() {
        if (frameStart < 0 || level == null) return -1;
        return sstvMode().lineAt(level.getGameTime() - frameStart);
    }

    @Override
    protected boolean keyInput() {
        if (frameStart >= 0 && line() < 0) frameStart = -1; // frame done
        if (keyContact()) startFrame();
        return frameStart >= 0;
    }

    private Photo ph() {
        return photo.get(PgTelegraph.PHOTO.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        int l = line();
        if (l != shownLine) {
            shownLine = l;
            sendData();
        }
        BlockPos camNow = ph() == null ? camera() : null;
        if (camNow != null) TvFrames.want(level, camNow); // ask a nearby player to film it
        if (!isKeyed() || l < 0) return;
        if (l != curLine) {
            curLine = l;
            SstvMode m = sstvMode();
            int[] px;
            Photo ph = ph();
            if (ph != null) {
                if (picture == null) picture = PhotoStore.image(level.getServer(), ph);
                px = new int[m.w];
                int sy = l * Pic.H / m.h;
                for (int x = 0; x < m.w; x++) px[x] = picture == null ? 0 : Pic.rgb(picture, sy * Pic.W + x * Pic.W / m.w);
            } else {
                if (camNow == null) {
                    frameStart = -1;
                    return;
                }
                if (filmed != null) {
                    int fw = filmed[0], fh = filmed[1], sy = l * fh / m.h;
                    px = new int[m.w];
                    for (int x = 0; x < m.w; x++) px[x] = filmed[2 + sy * fw + x * fw / m.w];
                } else {
                    if (l == 0 || camView == null) camView = TvCameraBlock.view(level, camNow, level.getBlockState(camNow));
                    px = CameraRender.line(level, camView, l, m.w, m.h);
                }
            }
            curRow = new byte[m.w * 2];
            for (int x = 0; x < m.w; x++) Pic.put(curRow, x, px[x] >> 16 & 255, px[x] >> 8 & 255, px[x] & 255);
        }
        String from = ph() != null ? ph().caption() : "TV CAM";
        SstvAir.Line ln = new SstvAir.Line(frameStart * 31 + worldPosition.asLong(), l, curRow, from, level.getGameTime(), sstvMode);
        SstvAir.put(level, worldPosition, ln);
        for (BlockPos a : com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity.chain(level, link)) SstvAir.put(level, a, ln);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        Component src = !photo.isEmpty() ? Component.translatable("pgtelegraph.sstv_tx.photo")
                : Component.translatable("pgtelegraph.sstv_tx.camera");
        SstvMode m = sstvMode();
        tip.add(Component.literal("     SSTV " + m.label + String.format(Locale.ROOT, " (%dx%d, %.0f s): ", m.w, m.h, m.seconds))
                .append(src).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (shownLine >= 0) tip.add(Component.literal(String.format(Locale.ROOT, "     > %d/%d", shownLine + 1, m.h)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!photo.isEmpty()) tag.put("Photo", photo.save(registries));
        if (clientPacket) tag.putInt("Line", shownLine);
        tag.putInt("SstvMode", sstvMode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        photo = tag.contains("Photo") ? ItemStack.parseOptional(registries, tag.getCompound("Photo")) : ItemStack.EMPTY;
        if (clientPacket) shownLine = tag.getInt("Line");
        if (tag.contains("SstvMode")) sstvMode = tag.getInt("SstvMode");
    }
}
