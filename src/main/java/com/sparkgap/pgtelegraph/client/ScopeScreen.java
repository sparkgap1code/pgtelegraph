package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.OscilloscopeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Oscilloscope display: two traces, volts/div per channel, time base 2.5 / 5 / 10 s per screen. */
public class ScopeScreen extends Screen {
    private static final double[] VDIV = {0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 50, 100, 200};
    private static final int[] SPAN = {50, 100, 200};
    private static final int W = 260, H = 160, DIVX = 10, DIVY = 8;
    private final BlockPos pos;
    private int v1 = 4, v2 = 4, span = 2;
    private boolean frozen;
    private float[] f1, f2;

    public ScopeScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.oscilloscope"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int x0 = (width - W) / 2, y1 = (height - H) / 2 + H + 6;
        addRenderableWidget(Button.builder(Component.literal("CH1 -"), b -> v1 = Math.max(0, v1 - 1)).bounds(x0, y1, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("CH1 +"), b -> v1 = Math.min(VDIV.length - 1, v1 + 1)).bounds(x0 + 42, y1, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("CH2 -"), b -> v2 = Math.max(0, v2 - 1)).bounds(x0 + 90, y1, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("CH2 +"), b -> v2 = Math.min(VDIV.length - 1, v2 + 1)).bounds(x0 + 132, y1, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("TIME"), b -> span = (span + 1) % SPAN.length).bounds(x0 + 180, y1, 38, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.scope.hold"), b -> frozen = !frozen).bounds(x0 + 220, y1, 40, 18).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity be)) {
            onClose();
            return;
        }
        int n = SPAN[span];
        if (!frozen || f1 == null) {
            f1 = new float[OscilloscopeBlockEntity.SAMPLES];
            f2 = new float[OscilloscopeBlockEntity.SAMPLES];
            for (int i = 0; i < f1.length; i++) {
                f1[i] = be.sample(be.trace1, i);
                f2[i] = be.sample(be.trace2, i);
            }
        }
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF404040);
        g.fill(x0, y0, x0 + W, y0 + H, 0xFF041004);
        for (int i = 1; i < DIVX; i++) g.fill(x0 + i * W / DIVX, y0, x0 + i * W / DIVX + 1, y0 + H, 0xFF103010);
        for (int i = 1; i < DIVY; i++) g.fill(x0, y0 + i * H / DIVY, x0 + W, y0 + i * H / DIVY + 1, 0xFF103010);
        g.fill(x0, y0 + H / 2, x0 + W, y0 + H / 2 + 1, 0xFF206020);
        trace(g, f1, n, VDIV[v1], x0, y0, 0xFFFFFF40);
        trace(g, f2, n, VDIV[v2], x0, y0, 0xFF40E0FF);
        g.drawString(font, String.format(Locale.ROOT, "CH1 %s/div  %.2f V", fmt(VDIV[v1]), f1[0]), x0, y0 - 22, 0xFFFF40);
        g.drawString(font, String.format(Locale.ROOT, "CH2 %s/div  %.2f V", fmt(VDIV[v2]), f2[0]), x0, y0 - 11, 0x40E0FF);
        g.drawString(font, String.format(Locale.ROOT, "%.2f s/div%s", n / 20.0 / DIVX, frozen ? "  HOLD" : ""),
                x0 + W - 110, y0 - 11, 0xC0C0C0);
    }

    private void trace(GuiGraphics g, float[] f, int n, double vdiv, int x0, int y0, int color) {
        int prevY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            float v = f[n - 1 - i]; // oldest on the left
            int x = x0 + (int) ((long) i * (W - 1) / (n - 1));
            int y = (int) Math.round(y0 + H / 2.0 - v / vdiv * (H / (double) DIVY));
            y = Math.max(y0, Math.min(y0 + H - 1, y));
            int x2 = x0 + (int) ((long) (i + 1) * (W - 1) / (n - 1));
            if (prevY != Integer.MIN_VALUE) g.fill(x, Math.min(prevY, y), x + 1, Math.max(prevY, y) + 1, color);
            g.fill(x, y, Math.max(x + 1, x2), y + 1, color);
            prevY = y;
        }
    }

    private static String fmt(double v) {
        return v < 1 ? (int) (v * 1000) + " mV" : (v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v)) + " V";
    }
}
