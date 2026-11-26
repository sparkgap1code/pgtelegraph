package com.sparkgap.pgtelegraph.sat;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import com.sparkgap.pgtelegraph.block.Tunable;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * SSTV receiver and monitor (SSTV 수신기): an FM receiver with a 3 kHz passband and a picture tube.
 * Tuned to a satellite's SSTV frequency (through coax to an antenna) it paints each line as it
 * arrives: 320 × 256, 4 ticks a line (51 s a frame). As on a real SSTV decoder:
 * <ul>
 *   <li>noise grain fades out gradually, gone at about 30 dB SNR; below about 11 dB FM clicks
 *   put short streaks in; below 3 dB nothing is painted</li>
 *   <li>below 30 MHz the picture comes as SSB: brightness is tone pitch (1500 Hz black - 2300 Hz
 *   white), so a tuning error shifts the whole picture brighter or darker (100 Hz = 12 %); past
 *   250 Hz the 1200 Hz sync starts to slip and past 600 Hz the decoder cannot lock at all</li>
 *   <li>from 30 MHz up it is FM: a carrier off frequency does not change the tones, only near the
 *   edge of the 3 kHz passband (past 1.2 kHz) the picture gets noisier</li>
 *   <li>weak signals lose line sync now and then: the picture shears sideways</li>
 *   <li>off the ionosphere (sky wave) the signal flutters (bands of noise) and arrives over two
 *   paths, the later one a faint ghost a few pixels to the right</li>
 * </ul>
 * Empty hand: open the screen.
 */
public class SstvMonitorBlockEntity extends com.sparkgap.pgtelegraph.block.PoweredStationBlockEntity implements IHaveGoggleInformation, Tunable {
    public static final int BW = 3000;
    private long freqHz = 145_800_000L;
    private AntennaLink link;
    private int rescan;
    private final Reception.TerrainCache cache = new Reception.TerrainCache();
    /** the picture, imgW x imgH RGB565 (the size of the mode last received) */
    public byte[] image = new byte[com.sparkgap.pgtelegraph.media.Pic.BYTES];
    public int imgW = com.sparkgap.pgtelegraph.media.Pic.W, imgH = com.sparkgap.pgtelegraph.media.Pic.H;
    /** the SSTV mode the VIS header announced */
    public int mode = com.sparkgap.pgtelegraph.media.SstvMode.PD50.ordinal();

    public void resize(int w, int h) {
        if (w == imgW && h == imgH) return;
        imgW = w;
        imgH = h;
        image = new byte[w * h * 2];
    }
    /** client: bumped whenever the picture changes */
    public int version;
    private long frame = -1;
    // picture defects of the frame being painted
    private int slip, ghostShift;
    private double ghostAmp, beatPhase;
    // the channel, worked out every half second
    private int scan;
    private Reception.Heard heard;
    private double noiseDbm = -130, interfDb = -99;
    private int interfDf;
    private int lastRow = -1, dirtyRows;
    // synced
    public String from = "";
    public float snr;
    public int row = -1;

    public SstvMonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /** 13.8 V DC, like the rest of the station (or a car battery) */
    public static final com.sparkgap.pgtelegraph.block.Spec SPEC = spec(13.8, 60);

    @Override
    public double nominalV() {
        return 13.8;
    }

    @Override
    public double ratedW() {
        return 60;
    }

    @Override
    public com.sparkgap.pgtelegraph.block.Spec spec() {
        return SPEC;
    }

    @Override
    protected void lostPower() {
        snr = 0;
        row = -1;
        sendData();
    }

    @Override
    public long freqHz() {
        return freqHz;
    }

    @Override
    public void setFreq(long hz) {
        freqHz = Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, hz));
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!powerTick()) return;
        if (--rescan <= 0) {
            rescan = 40;
            link = AntennaLink.scan(level, worldPosition);
        }
        if (link == null || link.amplifier() != null) return;
        long t = level.getGameTime();
        if (--scan <= 0) { // the channel: path loss, noise, and whatever else is on the frequency
            scan = com.sparkgap.pgtelegraph.radio.RadioLoad.every(10);
            int bw = aptBand() ? APT_BW : BW;
            Reception.Channel ch = Reception.channel(level, link, freqHz, null, worldPosition.asLong(), cache, -134, bw, 4);
            noiseDbm = ch.noiseDbmPerHz() + 10 * Math.log10(bw);
            heard = null;
            Reception.Heard other = null;
            for (Reception.Heard h : ch.signals()) {
                RadioWorld.Emission e = h.e();
                boolean pic = e.sat() == null ? com.sparkgap.pgtelegraph.media.SstvAir.get(level, e.source()) != null
                        : (e.sat().sstvLine(t) >= 0 && Math.abs(e.freqHz() - e.sat().cfg.hz("SSTVHZ")) < 1000) || isApt(e);
                if (heard == null && pic) heard = h;
                else if (other == null) other = h;
            }
            if (heard == null) scan = 2; // nothing yet: look again soon, so a picture is caught from its first lines
            interfDb = heard == null || other == null ? -99 : other.dbm() - heard.dbm();
            interfDf = heard == null || other == null ? 0 : other.df() - heard.df();
        }
        Satellite best = null;
        com.sparkgap.pgtelegraph.media.SstvAir.Line ground = null;
        double bestSnr = -99, bestOff = 0;
        boolean bestSky = false;
        if (heard != null) {
            RadioWorld.Emission e = heard.e();
            if (e.sat() == null) ground = com.sparkgap.pgtelegraph.media.SstvAir.get(level, e.source());
            else if (e.sat().sstvLine(t) >= 0 || isApt(e)) best = e.sat();
            // fading as the loudspeakers play it: the same Jakes taps
            double fade = com.sparkgap.pgtelegraph.radio.Fading.carrierGain(heard.fadeSeed(), t / 20.0, heard.dopplerHz(), heard.skyShare());
            bestSnr = heard.dbm() + 10 * Math.log10(Math.max(1e-6, fade)) - noiseDbm;
            bestOff = heard.df();
            bestSky = heard.sky();
        }
        boolean ssb = freqHz < 30_000_000L;
        if (!ssb) { // FM: the offset only costs SNR at the passband edge
            bestSnr -= Math.max(0, (Math.abs(bestOff) - 1200) / 150.0);
            bestOff = 0;
        }
        if ((best == null && ground == null) || bestSnr < 3 || Math.abs(bestOff) > 600) {
            if (snr != 0 || row != -1) {
                snr = 0;
                row = -1;
                sendData();
            }
            return;
        }
        if (best != null && heard != null && isApt(heard.e())) {
            aptTick(best, t, bestSnr);
            return;
        }
        long fid = ground != null ? ground.frame() : best.sstvFrame();
        if (fid != frame) {
            frame = fid;
            lastRow = -1;
            java.util.Arrays.fill(image, (byte) 0);
            from = ground != null ? ground.from() : best.name;
            mode = ground != null ? ground.mode() : com.sparkgap.pgtelegraph.media.SstvMode.PD50.ordinal(); // VIS: lock to the mode
            com.sparkgap.pgtelegraph.media.SstvMode m = com.sparkgap.pgtelegraph.media.SstvMode.of(mode);
            resize(m.w, m.h);
            slip = 0;
            beatPhase = 0;
            // the later sky-wave mode: a ghost as many pixels to the right as its delay lasts
            ghostShift = heard == null ? 0 : Math.max(1, Math.round(heard.delayMs() / (float) m.pixelMs));
            ghostAmp = heard == null || heard.skyShare() < 0.2f ? 0 : 0.35 * heard.skyShare();
        }
        int r = ground != null ? ground.line() : best.sstvLine(t);
        if (r != lastRow && r >= 0) {
            byte[] line = ground != null ? ground.row() : SstvImage.row(best, level, frame, r);
            if (line.length != imgW * 2 || r >= imgH) return; // a different mode mid-frame: wait for the next VIS
            com.sparkgap.pgtelegraph.media.SstvMode m = com.sparkgap.pgtelegraph.media.SstvMode.of(mode);
            byte[] painted = paint(line, bestSnr + m.snrGainDb(), bestOff, ssb, m);
            System.arraycopy(painted, 0, image, r * imgW * 2, imgW * 2);
            com.sparkgap.pgtelegraph.media.PicNet.sendRow((net.minecraft.server.level.ServerLevel) level, worldPosition, r, imgH, painted);
            lastRow = r;
            row = r;
            snr = (float) bestSnr;
            dirtyRows++;
            if (r == imgH - 1) com.sparkgap.pgtelegraph.Awards.near((net.minecraft.server.level.ServerLevel) level, worldPosition, 16,
                    ground != null ? "sstv_ground" : "sstv");
            if (dirtyRows >= 8 || r == imgH - 1) {
                dirtyRows = 0;
                setChanged();
                sendData();
            }
        }
    }

    /** APT: 34 kHz wide FM (the receiver opens its IF for it on 137-138 MHz) */
    public static final int APT_BW = 40_000;
    private Satellite aptSat;
    private long aptLast = -1000, aptNext;
    private int aptRow;

    private boolean aptBand() {
        return freqHz >= 137_000_000L && freqHz <= 138_000_000L;
    }

    private static boolean isApt(RadioWorld.Emission e) {
        return e.sat() != null && e.sat().apt() && Math.abs(e.freqHz() - e.sat().cfg.hz("APTHZ")) < 5000;
    }

    /** a weather satellite overhead: a new line every half second, a new picture each pass (or when the screen is full) */
    private void aptTick(Satellite sat, long t, double snr) {
        if (sat != aptSat || t - aptLast > 200 || aptRow >= com.sparkgap.pgtelegraph.media.Pic.H) {
            aptSat = sat;
            aptRow = 0;
            aptNext = t;
            frame = -1;
            resize(com.sparkgap.pgtelegraph.media.Pic.W, com.sparkgap.pgtelegraph.media.Pic.H);
            java.util.Arrays.fill(image, (byte) 0);
            from = sat.name + " APT";
            mode = com.sparkgap.pgtelegraph.media.SstvMode.PD50.ordinal();
            slip = 0;
            ghostAmp = 0;
        }
        aptLast = t;
        if (t < aptNext) return;
        aptNext = t + AptImage.TICKS_PER_LINE;
        byte[] line = AptImage.row(sat, level, t, aptRow, worldPosition);
        byte[] painted = paint(line, snr, 0, false, com.sparkgap.pgtelegraph.media.SstvMode.of(mode));
        int r = aptRow++;
        System.arraycopy(painted, 0, image, r * imgW * 2, imgW * 2);
        com.sparkgap.pgtelegraph.media.PicNet.sendRow((net.minecraft.server.level.ServerLevel) level, worldPosition, r, imgH, painted);
        row = r;
        this.snr = (float) snr;
        if (++dirtyRows >= 8) {
            dirtyRows = 0;
            setChanged();
            sendData();
        }
    }

    /** one received line with the defects of a real SSTV decoder (see the class comment) */
    private byte[] paint(byte[] line, double snr, double offHz, boolean ssb, com.sparkgap.pgtelegraph.media.SstvMode m) {
        final int W = line.length / 2;
        final boolean yc = m.yc;
        var rnd = level.random;
        // another signal on the frequency beats with ours: bars across the line (FM captures and hides a weaker one)
        double beatAmp = 0, beatStep = 0;
        if (interfDb > -40 && interfDf != 0) {
            beatAmp = 200 * Math.min(1, Math.pow(10, interfDb / 20));
            if (!ssb && interfDb < -6) beatAmp *= Math.pow(10, (interfDb + 6) / 10);
            beatStep = 2 * Math.PI * interfDf * m.pixelMs / 1000;
        }
        double phase = beatPhase;
        beatPhase += beatStep * W * 1.07;
        double pSlip = Math.max(0, Math.min(0.5, (9 - snr) / 18)) + Math.max(0, (Math.abs(offHz) - 250) / 700);
        if (rnd.nextDouble() < pSlip) slip += rnd.nextInt(13) - 6;
        double sigma = 170 * Math.pow(10, -snr / 20);
        if (sigma < 1.5) sigma = 0;
        double pClick = Math.max(0, Math.min(0.08, (11 - snr) / 60));
        double bright = offHz / 800.0 * 255;
        byte[] out = new byte[W * 2];
        int streak = 0, sr = 0, sg = 0, sb = 0;
        for (int x = 0; x < W; x++) {
            int src = Math.floorMod(x - slip, W);
            int c = com.sparkgap.pgtelegraph.media.Pic.rgb(line, src);
            double r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            if (ghostAmp > 0) {
                int gc = com.sparkgap.pgtelegraph.media.Pic.rgb(line, Math.floorMod(src - ghostShift, W));
                r = r * (1 - ghostAmp) + (gc >> 16 & 255) * ghostAmp;
                g = g * (1 - ghostAmp) + (gc >> 8 & 255) * ghostAmp;
                b = b * (1 - ghostAmp) + (gc & 255) * ghostAmp;
            }
            r += bright;
            g += bright;
            b += bright;
            if (beatAmp > 0) {
                double bar = beatAmp * Math.sin(phase + beatStep * x);
                r += bar;
                g += bar;
                b += bar;
            }
            if (sigma > 0) {
                if (yc) { // brightness and colour difference: mostly grey grain, a little colour
                    double n = rnd.nextGaussian() * sigma;
                    r += n + rnd.nextGaussian() * sigma * 0.35;
                    g += n + rnd.nextGaussian() * sigma * 0.35;
                    b += n + rnd.nextGaussian() * sigma * 0.35;
                } else { // R, G and B go out one after another: independent colour grain
                    r += rnd.nextGaussian() * sigma;
                    g += rnd.nextGaussian() * sigma;
                    b += rnd.nextGaussian() * sigma;
                }
            }
            if (streak == 0 && rnd.nextDouble() < pClick) {
                streak = 1 + rnd.nextInt(8);
                sr = rnd.nextInt(256);
                sg = rnd.nextInt(256);
                sb = rnd.nextInt(256);
            }
            if (streak > 0) {
                streak--;
                r = sr;
                g = sg;
                b = sb;
            }
            com.sparkgap.pgtelegraph.media.Pic.put(out, x, (int) r, (int) g, (int) b);
        }
        return out;
    }

    /** the picture scaled to the 320 x 256 of a photo */
    private byte[] toPhoto() {
        if (imgW == com.sparkgap.pgtelegraph.media.Pic.W && imgH == com.sparkgap.pgtelegraph.media.Pic.H) return image.clone();
        byte[] out = new byte[com.sparkgap.pgtelegraph.media.Pic.BYTES];
        for (int y = 0; y < com.sparkgap.pgtelegraph.media.Pic.H; y++)
            for (int x = 0; x < com.sparkgap.pgtelegraph.media.Pic.W; x++) {
                int sx = x * imgW / com.sparkgap.pgtelegraph.media.Pic.W, sy = y * imgH / com.sparkgap.pgtelegraph.media.Pic.H;
                int c = com.sparkgap.pgtelegraph.media.Pic.rgb(image, sy * imgW + sx);
                com.sparkgap.pgtelegraph.media.Pic.put(out, y * com.sparkgap.pgtelegraph.media.Pic.W + x, c >> 16 & 255, c >> 8 & 255, c & 255);
            }
        return out;
    }

    /** print what is on the screen onto a sheet of paper */
    public net.minecraft.world.item.ItemStack print() {
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(com.sparkgap.pgtelegraph.PgTelegraph.PHOTO_ITEM.get());
        String id = com.sparkgap.pgtelegraph.media.PhotoStore.save(level.getServer(), toPhoto());
        s.set(com.sparkgap.pgtelegraph.PgTelegraph.PHOTO.get(), new com.sparkgap.pgtelegraph.media.Photo(id,
                (from.isEmpty() ? "SSTV" : from) + " - " + RadioBlockEntity.formatFreq(freqHz)));
        return s;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.freq")).append(": ")
                .append(Component.literal(RadioBlockEntity.formatFreq(freqHz)).withStyle(ChatFormatting.AQUA)));
        if (row >= 0) tip.add(Component.literal(String.format(Locale.ROOT, "     %s  %s %d/%d  SNR %.0f dB", from,
                        com.sparkgap.pgtelegraph.media.SstvMode.of(mode).label, row + 1, imgH, snr))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("ImgW", imgW);
        tag.putInt("ImgH", imgH);
        tag.putInt("Mode", mode);
        tag.putLong("Freq", freqHz);
        if (!clientPacket) tag.putByteArray("ImageZ", com.sparkgap.pgtelegraph.media.Pic.deflate(image));
        tag.putString("From", from);
        if (clientPacket) {
            tag.putFloat("Snr", snr);
            tag.putInt("Row", row);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        if (tag.contains("ImgW")) resize(Math.max(1, tag.getInt("ImgW")), Math.max(1, tag.getInt("ImgH")));
        if (tag.contains("Mode")) mode = tag.getInt("Mode");
        if (!clientPacket && tag.contains("ImageZ")) {
            byte[] im = com.sparkgap.pgtelegraph.media.Pic.inflate(tag.getByteArray("ImageZ"), image.length);
            if (im != null) System.arraycopy(im, 0, image, 0, Math.min(im.length, image.length));
        } else if (!clientPacket && tag.getByteArray("Image").length == 64 * 64 && image.length == com.sparkgap.pgtelegraph.media.Pic.BYTES) { // saved before 320 x 256
            System.arraycopy(com.sparkgap.pgtelegraph.media.Pic.fromLegacy(tag.getByteArray("Image")), 0, image, 0, image.length);
        }
        from = tag.getString("From");
        if (clientPacket) {
            snr = tag.getFloat("Snr");
            row = tag.getInt("Row");
        }
    }
}
