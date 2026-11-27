package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.KeyPressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only entry points. Only ever called from code paths guarded by level.isClientSide. */
public final class ClientAccess {
    private ClientAccess() {}

    public static void openFocus(BlockPos pos, boolean paddle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new FocusScreen(pos, paddle));
    }

    public static void ensureBuzzerSound(com.sparkgap.pgtelegraph.block.BuzzerBlockEntity buzzer) {
        if (buzzer.clientSound instanceof BuzzerSound s && !s.isStopped()) return;
        BuzzerSound sound = new BuzzerSound(buzzer);
        buzzer.clientSound = sound;
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public static void openTape(com.sparkgap.pgtelegraph.tape.TapeData tape) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new TapeScreen(tape));
    }

    public static void openPerforator(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new PerforateScreen(pos));
    }

    public static void openCoil(BlockPos pos, double mH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new CoilScreen(pos, mH));
    }

    public static void openHandheld(net.minecraft.world.InteractionHand hand, com.sparkgap.pgtelegraph.radio.HandheldData d, int batteryPct, boolean manpack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new HandheldScreen(hand, d, batteryPct, manpack));
    }

    public static void handheldState(com.sparkgap.pgtelegraph.network.HandheldStatePayload s) {
        HandheldClient.update(s);
    }

    public static void openAim(BlockPos pos, double az, double el) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity be) {
            mc.setScreen(new AimScreen(pos, az, el, be.track(), be.trackName()));
        } else mc.setScreen(new AimScreen(pos, az, el));
    }

    public static void openTune(BlockPos pos, long hz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        TuneScreen s = new TuneScreen(pos, hz);
        if (mc.level != null && mc.level.getBlockEntity(pos) != null) s.label = com.sparkgap.pgtelegraph.block.Labels.get(mc.level.getBlockEntity(pos));
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.sparkgap.pgtelegraph.block.RadioBlockEntity r
                && (r.getClass() == com.sparkgap.pgtelegraph.block.TransmitterBlockEntity.class
                || r.getClass() == com.sparkgap.pgtelegraph.block.ReceiverBlockEntity.class || r.creative())) s.mode = r.mode();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.sparkgap.pgtelegraph.block.RadioBlockEntity r
                && (r instanceof com.sparkgap.pgtelegraph.block.ReceiverBlockEntity || r.getClass() == com.sparkgap.pgtelegraph.block.TransmitterBlockEntity.class
                || r instanceof com.sparkgap.pgtelegraph.block.RepeaterBlockEntity || r.creative())) s.ctcss = r.ctcss();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity q)
            s.robotMode = q.opMode();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.sparkgap.pgtelegraph.media.SstvTransmitterBlockEntity sx)
            s.sstv = sx.sstvMode();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.sparkgap.pgtelegraph.block.ReceiverBlockEntity rx)
            s.knobs = new int[]{rx.att, rx.agc, rx.nb, rx.filterSel};
        mc.setScreen(s);
    }

    public static void takePhoto() {
        ClientCamera.shoot();
    }

    public static void openPhoto(com.sparkgap.pgtelegraph.media.Photo p) {
        open(new PhotoScreen(p));
    }

    private static void open(net.minecraft.client.gui.screens.Screen s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(s);
    }

    public static void vna(com.sparkgap.pgtelegraph.network.Batch2Payloads.VnaData d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VnaScreen s) s.update(d);
        else if (mc.screen == null) mc.setScreen(new VnaScreen(d));
    }

    public static void coaxChoice(com.sparkgap.pgtelegraph.network.Batch2Payloads.CoaxChoice m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof CoaxScreen) mc.setScreen(new CoaxScreen(m));
    }

    public static void openAprs(BlockPos pos) {
        open(new AprsScreen(pos));
    }

    public static void openDtmf(BlockPos pos, com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity be) {
        open(new DtmfScreen(pos, be.onCode(), be.offCode(), be.mode(), be.freqHz()));
    }

    public static void openBeacon(BlockPos pos, com.sparkgap.pgtelegraph.block.BeaconProgram p) {
        open(new BeaconScreen(pos, p));
    }

    public static void openScope(BlockPos pos) {
        open(new ScopeScreen(pos));
    }

    public static void openTeleprinter(BlockPos pos) {
        open(new TeleprinterScreen(pos));
    }

    public static void openItemTune(net.minecraft.world.InteractionHand hand, long hz) {
        boolean off = hand == net.minecraft.world.InteractionHand.OFF_HAND;
        open(new TuneScreen(hz, v -> PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.ItemTune(off, v))));
    }

    public static void openSpectrum() {
        open(new SpectrumScreen());
    }

    public static void spectrum(long center, long span, float[] dbm) {
        SpectrumScreen.receive(center, span, dbm);
    }

    public static void openLogbook(net.minecraft.world.InteractionHand hand, java.util.List<String> entries) {
        open(new LogbookScreen(hand, entries));
    }

    public static void importAudio(net.minecraft.world.InteractionHand hand) {
        ClientAudio.importFile(hand);
    }

    public static void radioTick(com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity r) {
        ClientAudio.radioTick(r);
    }

    public static void ensureTone(com.sparkgap.pgtelegraph.block.ToneSource s) {
        ToneSound.ensure(s);
    }

    public static void openIonogram(BlockPos pos) {
        open(new IonogramScreen(pos));
    }

    public static void openSstv(BlockPos pos) {
        open(new SstvScreen(pos));
    }

    public static void openCubeSat(net.minecraft.world.InteractionHand hand, String cfg) {
        open(new CubeSatScreen(hand, cfg));
    }

    public static void openCipher(net.minecraft.world.InteractionHand hand, String key) {
        open(new CipherScreen(hand, key));
    }

    /** the receiver's loudspeaker: its channel rebuilt and demodulated in real time (ClientRadio) */
    public static void ensureReceiverSound(com.sparkgap.pgtelegraph.block.ReceiverBlockEntity rx) {
        for (var g : rx.channel.signals) if (g.key().startsWith("a:")) ClientAudio.get(g.key().substring(2));
        if (rx.clientTone instanceof ClientRadio.RxSound t && t.playing()) return;
        if (rx.clientTone instanceof ClientRadio.RxSound t) t.drop();
        var s = new ClientRadio.RxSound(rx.channel, rx.getBlockPos(), false,
                () -> !rx.isRemoved() && rx.isPowered() && !rx.isBurnt(), 0.8f);
        rx.clientTone = s;
        Minecraft.getInstance().getSoundManager().play(s);
    }

    public static void tvFilm(com.sparkgap.pgtelegraph.media.TvNet.Film m) {
        TvCapture.film(m);
    }

    /** a frame for a TV set: decoded in the background, then handed to the set */
    private static final java.util.concurrent.ExecutorService TV_DEC = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pgtelegraph-tv-decode");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.Map<BlockPos, com.sparkgap.pgtelegraph.media.TvCodec.Dec> TV_DECODERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void tvDown(com.sparkgap.pgtelegraph.media.TvNet.Down m) {
        Minecraft mc = Minecraft.getInstance();
        if (TV_DECODERS.size() > 64) TV_DECODERS.clear();
        var dec = TV_DECODERS.computeIfAbsent(m.rx().immutable(), k -> new com.sparkgap.pgtelegraph.media.TvCodec.Dec());
        TV_DEC.execute(() -> {
            int[] px = dec.decode(m.jpeg(), m.seq());
            if (px == null) return;
            mc.execute(() -> {
                var t = m.rx().equals(com.sparkgap.pgtelegraph.media.FpvGogglesItem.GOGGLE_POS) ? FpvClient.set()
                        : mc.level != null && mc.level.getBlockEntity(m.rx()) instanceof com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity r ? r : null;
                if (t != null) {
                    t.picture = px;
                    t.picW = px[0];
                    t.picH = px[1];
                    t.picSeq = m.seq();
                }
            });
        });
    }

    public static void fpv(com.sparkgap.pgtelegraph.media.TvNet.Goggles m) {
        FpvClient.state(m);
    }

    public static void speaker(com.sparkgap.pgtelegraph.media.SpeakerNet.State m) {
        SpeakerClient.update(m);
    }

    public static void openTv(BlockPos pos) {
        open(new TvScreen(pos));
    }

    public static void handheldRx(net.minecraft.nbt.CompoundTag tag) {
        HandheldClient.channel.load(tag);
    }

    static void send(BlockPos pos, int channel, boolean down) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new KeyPressPayload(pos, channel, down));
        }
    }
}
