package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import com.sparkgap.pgtelegraph.network.TunePayload;
import com.sparkgap.pgtelegraph.radio.Bands;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Dial: type kHz (e.g. 7030.5), step buttons, or jump to a band's CW spot. Mouse wheel = 100 Hz. */
public class TuneScreen extends Screen {
    private final java.util.function.LongConsumer sender;
    private long hz;
    /** the box's unit: MHz from 30 MHz up (FM, VHF, TV), kHz below (AM, HF); the button next to it switches */
    private boolean mhz;
    private EditBox box;

    public TuneScreen(BlockPos pos, long hz) {
        this(hz, v -> PacketDistributor.sendToServer(new TunePayload(pos, v)));
        this.pos = pos;
    }

    private BlockPos pos;
    /** CW / FSK / AM / FM switch, shown for plain radios only (null = no switch) */
    Integer mode;
    /** the set's label (name tag), or null */
    String label;
    /** QSO robot operating mode (null = not a robot) */
    Integer robotMode;
    /** CTCSS tone index (null = this set has none) */
    Integer ctcss;
    /** SSTV transmitter mode (null = not an SSTV transmitter) */
    com.sparkgap.pgtelegraph.media.SstvMode sstv;

    /** family button, then for PD resolution and speed, for the others the mode */
    private void sstvButtons(int cx, int y) {
        var M = com.sparkgap.pgtelegraph.media.SstvMode.class;
        var fams = com.sparkgap.pgtelegraph.media.SstvMode.Family.values();
        addRenderableWidget(Button.builder(Component.literal(sstv.family.name()), b -> {
            var f = fams[(sstv.family.ordinal() + 1) % fams.length];
            setSstv(com.sparkgap.pgtelegraph.media.SstvMode.family(f).get(0));
        }).bounds(cx - 141, y + 150, 66, 20).build());
        if (sstv.family == com.sparkgap.pgtelegraph.media.SstvMode.Family.PD) {
            addRenderableWidget(Button.builder(Component.literal(sstv.w + "x" + sstv.h), b -> {
                int[] ws = com.sparkgap.pgtelegraph.media.SstvMode.PD_WIDTHS;
                int i = 0;
                while (ws[i] != sstv.w) i++;
                setSstv(com.sparkgap.pgtelegraph.media.SstvMode.pdAt(ws[(i + 1) % ws.length]).get(0));
            }).bounds(cx - 71, y + 150, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT, "%s %.0fs", sstv.label, sstv.seconds)), b -> {
                var list = com.sparkgap.pgtelegraph.media.SstvMode.pdAt(sstv.w);
                setSstv(list.get((list.indexOf(sstv) + 1) % list.size()));
            }).bounds(cx + 3, y + 150, 90, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT, "%s %dx%d %.0fs", sstv.label, sstv.w, sstv.h, sstv.seconds)), b -> {
                var list = com.sparkgap.pgtelegraph.media.SstvMode.family(sstv.family);
                setSstv(list.get((list.indexOf(sstv) + 1) % list.size()));
            }).bounds(cx - 71, y + 150, 164, 20).build());
        }
    }

    private void setSstv(com.sparkgap.pgtelegraph.media.SstvMode m) {
        sstv = m;
        PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.RadioMode(pos, 300 + m.ordinal()));
        rebuildWidgets();
    }

    /** receiver knobs {att, agc, nb, filter} (null = not a receiver) */
    int[] knobs;
    private static final String[][] KNOB_LABELS = {
            {"ATT 0", "ATT 10", "ATT 20"}, {"AGC F", "AGC S", "AGC OFF"}, {"NB OFF", "NB ON"}, {"FIL 1", "FIL 2", "FIL 3"}};
    private static final String[] MODES = {"CW", "FSK (RTTY)", "AM", "NFM", "SSB", "WFM"};

    /** dial for anything: {@code sender} gets the new frequency */
    public TuneScreen(long hz, java.util.function.LongConsumer sender) {
        super(Component.translatable("pgtelegraph.radio.tune"));
        this.sender = sender;
        this.hz = hz;
        this.mhz = hz >= 30_000_000L;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 50;
        box = new EditBox(font, cx - 50, y, 100, 20, Component.empty());
        box.setValue(fmt(hz));
        box.setResponder(s -> {
            try {
                hz = clamp(Math.round(Double.parseDouble(s.trim().replace(',', '.')) * (mhz ? 1_000_000 : 1000)));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal(mhz ? "MHz" : "kHz"), b -> {
            mhz = !mhz;
            b.setMessage(Component.literal(mhz ? "MHz" : "kHz"));
            box.setValue(fmt(hz));
        }).bounds(cx + 54, y, 34, 20).build());
        long[] steps = {-1_000_000, -1000, -100, -10, 10, 100, 1000, 1_000_000};
        String[] labels = {"-1M", "-1k", "-100", "-10", "+10", "+100", "+1k", "+1M"};
        for (int i = 0; i < steps.length; i++) {
            long st = steps[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> set(hz + st))
                    .bounds(cx - 144 + i * 36, y + 26, 34, 20).build());
        }
        for (int i = 0; i < Bands.NAMES.length; i++) {
            long spot = Bands.CW_KHZ[i] == 17 ? 17_200L : Bands.CW_KHZ[i] * 1000L; // VLF: the Grimeton spot
            addRenderableWidget(Button.builder(Component.literal(Bands.NAMES[i]), b -> set(spot))
                    .bounds(cx - 141 + (i % 6) * 48, y + 54 + (i / 6) * 22, 45, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx - 40, y + 126, 80, 20).build());
        if (mode != null && pos != null) {
            addRenderableWidget(Button.builder(Component.literal(MODES[mode]), b -> {
                mode = (mode + 1) % (knobs != null ? MODES.length : MODES.length - 1); // WFM: receivers only
                b.setMessage(Component.literal(MODES[mode]));
                PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.RadioMode(pos, mode));
            }).bounds(cx + 50, y + 126, 80, 20).build());
        }
        if (sstv != null && pos != null) sstvButtons(cx, y);
        if (robotMode != null && pos != null) {
            addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.robot.mode." + robotMode), b -> {
                robotMode = (robotMode + 1) % com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity.MODES;
                b.setMessage(Component.translatable("pgtelegraph.robot.mode." + robotMode));
                PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.RadioMode(pos, 600 + robotMode));
            }).bounds(cx - 141, y + 150, 140, 20).build());
        }
        if (ctcss != null && pos != null) {
            addRenderableWidget(Button.builder(ctcssLabel(), b -> {
                ctcss = com.sparkgap.pgtelegraph.radio.Ctcss.clamp(ctcss + (hasShiftDown() ? -1 : 1));
                b.setMessage(ctcssLabel());
                PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.RadioMode(pos, 500 + ctcss));
            }).bounds(cx - 130, y + 126, 86, 20).build());
        }
        if (knobs != null && pos != null) {
            for (int k = 0; k < 4; k++) {
                final int knob = k;
                addRenderableWidget(Button.builder(Component.literal(KNOB_LABELS[k][knobs[k]]), b -> {
                    knobs[knob] = (knobs[knob] + 1) % KNOB_LABELS[knob].length;
                    b.setMessage(Component.literal(KNOB_LABELS[knob][knobs[knob]]));
                    PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.RadioMode(pos, 100 + knob * 10 + knobs[knob]));
                }).bounds(cx - 141 + k * 72, y + 150, 68, 20).build());
            }
        }
    }

    private Component ctcssLabel() {
        return Component.literal("CTCSS " + com.sparkgap.pgtelegraph.radio.Ctcss.label(ctcss));
    }

    private static long clamp(long v) {
        return Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, v));
    }

    private String fmt(long hz) {
        return mhz ? String.format(Locale.ROOT, "%.4f", hz / 1e6) : String.format(Locale.ROOT, "%.2f", hz / 1000.0);
    }

    private void set(long v) {
        hz = clamp(v);
        box.setValue(fmt(hz));
        sender.accept(hz);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        set(hz + (long) Math.signum(sy) * 100);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            set(hz);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        sender.accept(hz);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2, y = height / 2 - 50;
        g.drawCenteredString(font, label == null || label.isEmpty() ? title
                : title.copy().append(Component.literal("  「" + label + "」").withStyle(net.minecraft.ChatFormatting.AQUA)), cx, y - 26, 0xFFFFFF);
        String band = Bands.name(hz);
        g.drawString(font, band == null ? Component.translatable("pgtelegraph.radio.out_of_band") : Component.literal(band),
                cx + 92, y + 6, band == null ? 0xFF8060 : 0x80FF80);
    }
}
