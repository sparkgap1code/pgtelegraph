package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** Spectrum analyzer: center (MHz) and span; the trace refreshes twice a second; hover reads a bin. */
public class SpectrumScreen extends Screen {
    private static final long[] SPANS = {10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000L, 10_000_000_000L};
    private static final int W = 320, H = 150;
    static long center = 7_030_000L;
    static int spanIdx = 2;
    static float[] data;
    static long dataCenter, dataSpan;
    private int tick;
    private EditBox box;

    public SpectrumScreen() {
        super(Component.translatable("item.pgtelegraph.spectrum_analyzer"));
    }

    @Override
    protected void init() {
        int x0 = (width - W) / 2, y = (height + H) / 2 + 8;
        box = new EditBox(font, x0, y, 90, 18, Component.empty());
        box.setValue(String.format(Locale.ROOT, "%.4f", center / 1e6));
        box.setResponder(s -> {
            try {
                center = Math.max(5_000, Math.round(Double.parseDouble(s.trim()) * 1e6));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("SPAN -"), b -> spanIdx = Math.max(0, spanIdx - 1)).bounds(x0 + 96, y, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SPAN +"), b -> spanIdx = Math.min(SPANS.length - 1, spanIdx + 1)).bounds(x0 + 148, y, 50, 18).build());
    }

    public static void receive(long c, long s, float[] d) {
        dataCenter = c;
        dataSpan = s;
        data = d;
    }

    @Override
    public void tick() {
        if (tick++ % 10 == 0) PacketDistributor.sendToServer(new Batch2Payloads.SpectrumRequest(center, SPANS[spanIdx]));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF404040);
        g.fill(x0, y0, x0 + W, y0 + H, 0xFF000818);
        double top = -20, bottom = -150; // dBm
        for (int db = -140; db <= -20; db += 20) {
            int y = (int) (y0 + (top - db) / (top - bottom) * H);
            g.fill(x0, y, x0 + W, y + 1, 0xFF102030);
            g.drawString(font, db + "", x0 - 26, y - 4, 0x808080);
        }
        for (int i = 1; i < 10; i++) g.fill(x0 + i * W / 10, y0, x0 + i * W / 10 + 1, y0 + H, 0xFF102030);
        if (data != null) {
            int n = data.length;
            for (int i = 0; i < n; i++) {
                int xa = x0 + i * W / n, xb = x0 + (i + 1) * W / n;
                int y = (int) (y0 + (top - Math.max(bottom, Math.min(top, data[i]))) / (top - bottom) * H);
                g.fill(xa, y, xb, y0 + H, 0xFF2080FF);
                g.fill(xa, y, xb, y + 1, 0xFFFFFF80);
            }
            if (mx >= x0 && mx < x0 + W && my >= y0 && my < y0 + H) {
                int i = (mx - x0) * n / W;
                double f = dataCenter - dataSpan / 2.0 + (i + 0.5) * dataSpan / n;
                g.fill(mx, y0, mx + 1, y0 + H, 0x80FFFFFF);
                g.drawString(font, String.format(Locale.ROOT, "%s  %.0f dBm  %s", RadioMath.formatMHz(f / 1e6), data[i],
                        RadioMath.sMeter(data[i])), x0, y0 - 12, 0xFFFFFF);
            }
        }
        long span = SPANS[spanIdx];
        g.drawString(font, RadioMath.formatMHz((center - span / 2.0) / 1e6), x0, y0 + H + 30, 0xA0A0A0);
        String hi = RadioMath.formatMHz((center + span / 2.0) / 1e6);
        g.drawString(font, hi, x0 + W - font.width(hi), y0 + H + 30, 0xA0A0A0);
        g.drawString(font, "MHz   span " + RadioMath.formatMHz(span / 1e6) + "   RBW "
                + RadioMath.formatMHz(Math.max(500, span / (double) Batch2Payloads.BINS) / 1e6), x0 + 204, y0 + H + 13, 0xC0C0C0);
    }
}
