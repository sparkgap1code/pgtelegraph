package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * VNA screen: SWR across the sweep (left, log scale 1-10) and the Smith chart (right), with a
 * marker (click or scroll on the SWR plot) reading frequency, SWR, return loss and R + jX at the port.
 */
public class VnaScreen extends Screen {
    private Batch2Payloads.VnaData d;
    private int marker = -1;
    private int gx, gy, gw, gh, sx, sy, sr;

    public VnaScreen(Batch2Payloads.VnaData d) {
        super(Component.translatable("item.pgtelegraph.nanovna"));
        this.d = d;
    }

    public void update(Batch2Payloads.VnaData nd) {
        d = nd;
        marker = -1;
    }

    private long center() {
        return (d.startHz() + d.stopHz()) / 2;
    }

    private long span() {
        return d.stopHz() - d.startHz();
    }

    private void ask(long c, long span) {
        span = Math.max(2000, span);
        PacketDistributor.sendToServer(new Batch2Payloads.VnaRequest(d.pos(), Math.max(10_000, c - span / 2), c + span / 2));
    }

    @Override
    protected void init() {
        gw = Math.min(260, width / 2 - 20);
        gh = Math.min(150, height - 120);
        gx = width / 2 - gw - 10;
        gy = 40;
        sr = Math.min(75, (width / 2 - 30) / 2);
        sx = width / 2 + 10 + sr;
        sy = gy + gh / 2;
        // one row of buttons under the plots, done at its end
        int by = gy + gh + 32, bx = width / 2 - 165;
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.vna.zoom_in"), b -> ask(markerHz(), span() / 3)).bounds(bx, by, 56, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.vna.zoom_out"), b -> ask(center(), span() * 3)).bounds(bx + 60, by, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<<"), b -> ask(center() - span() / 2, span())).bounds(bx + 120, by, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">>"), b -> ask(center() + span() / 2, span())).bounds(bx + 150, by, 26, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.vna.min"), b -> {
            marker = minIndex();
        }).bounds(bx + 180, by, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(bx + 270, by, 60, 20).build());
    }

    private int n() {
        return d.re().length;
    }

    private double g2(int i) {
        return d.re()[i] * d.re()[i] + d.im()[i] * d.im()[i];
    }

    private double swr(int i) {
        double g = Math.min(0.999, Math.sqrt(g2(i)));
        return (1 + g) / (1 - g);
    }

    private int minIndex() {
        int b = 0;
        for (int i = 1; i < n(); i++) if (g2(i) < g2(b)) b = i;
        return b;
    }

    private long hz(int i) {
        return d.startHz() + (long) ((double) span() * i / Math.max(1, n() - 1));
    }

    private long markerHz() {
        return hz(marker < 0 ? minIndex() : marker);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= gx && mx <= gx + gw && my >= gy && my <= gy + gh) {
            marker = (int) Math.round((mx - gx) / gw * (n() - 1));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        marker = Math.max(0, Math.min(n() - 1, (marker < 0 ? minIndex() : marker) + (int) Math.signum(dy)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String mhz(long hz) {
        return String.format(Locale.ROOT, "%.4f MHz", hz / 1e6);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.drawCenteredString(font, d.info(), width / 2, 20, 0xA0A0A0);
        // ---- SWR plot, log 1..10
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF101418);
        for (double s : new double[]{1.5, 2, 3, 5}) {
            int y = gy + gh - (int) (Math.log10(s) * gh);
            g.fill(gx, y, gx + gw, y + 1, s == 2 ? 0xFF404830 : 0xFF283028);
            g.drawString(font, s + "", gx - 22, y - 4, 0xFF909090);
        }
        int px = -1, py = -1;
        for (int i = 0; i < n(); i++) {
            int x = gx + (int) ((double) gw * i / (n() - 1));
            int y = gy + gh - (int) (Math.min(1, Math.log10(swr(i))) * gh);
            if (px >= 0) line(g, px, py, x, y, 0xFFFFE040);
            px = x;
            py = y;
        }
        g.drawString(font, mhz(d.startHz()), gx, gy + gh + 4, 0xFF909090);
        String stop = mhz(d.stopHz());
        g.drawString(font, stop, gx + gw - font.width(stop), gy + gh + 4, 0xFF909090);
        g.drawString(font, "SWR", gx + 3, gy + 3, 0xFFFFE040);
        // ---- Smith chart: unit circle, r = 1 circle, x = ±1 arcs (drawn as points), real axis
        circle(g, sx, sy, sr, 0xFF606060);
        circle(g, sx + sr / 2, sy, sr / 2, 0xFF404040);
        g.fill(sx - sr, sy, sx + sr, sy + 1, 0xFF404040);
        for (int a = 0; a < 360; a += 2) { // x = +1 and x = -1 circles, centre (1, ±1) radius 1 (clipped to the unit disk)
            for (int sgn : new int[]{1, -1}) {
                double cx = 1 + Math.cos(Math.toRadians(a)), cy = sgn + Math.sin(Math.toRadians(a));
                if (cx * cx + cy * cy <= 1) g.fill(sx + (int) (cx * sr), sy - (int) (cy * sr), sx + (int) (cx * sr) + 1, sy - (int) (cy * sr) + 1, 0xFF404040);
            }
        }
        px = -1;
        for (int i = 0; i < n(); i++) {
            int x = sx + (int) (d.re()[i] * sr), y = sy - (int) (d.im()[i] * sr);
            if (px >= 0) line(g, px, py, x, y, 0xFF40C0FF);
            px = x;
            py = y;
        }
        // ---- marker
        int m = marker < 0 ? minIndex() : marker;
        int mxp = gx + (int) ((double) gw * m / (n() - 1));
        g.fill(mxp, gy, mxp + 1, gy + gh, 0xFFFF5050);
        int ex = sx + (int) (d.re()[m] * sr), ey = sy - (int) (d.im()[m] * sr);
        g.fill(ex - 2, ey - 2, ex + 2, ey + 2, 0xFFFF5050);
        double gr = d.re()[m], gi = d.im()[m];
        double den = (1 - gr) * (1 - gr) + gi * gi;
        double r = 50 * (1 - gr * gr - gi * gi) / den, x = 50 * 2 * gi / den;
        double rl = -10 * Math.log10(Math.max(1e-6, g2(m)));
        String s = String.format(Locale.ROOT, "%s   SWR %.2f   RL %.1f dB   Z = %.1f %s j%.1f Ω", mhz(hz(m)), swr(m), rl, r, x < 0 ? "-" : "+", Math.abs(x));
        g.drawCenteredString(font, s, width / 2, gy + gh + 18, 0xFFFF8080);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int a = 0; a < 360; a += 2) {
            int x = cx + (int) Math.round(r * Math.cos(Math.toRadians(a))), y = cy + (int) Math.round(r * Math.sin(Math.toRadians(a)));
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (steps == 0 ? 0 : (x1 - x0) * i / steps), y = y0 + (steps == 0 ? 0 : (y1 - y0) * i / steps);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }
}
