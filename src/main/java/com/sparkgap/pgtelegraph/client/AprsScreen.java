package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.AprsStationBlockEntity;
import com.sparkgap.pgtelegraph.item.AprsTrackerItem;
import com.sparkgap.pgtelegraph.radio.Aprs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** APRS map: the station in the middle, north up, every decoded report as a dot with its name, and a list. */
public class AprsScreen extends Screen {
    private final BlockPos pos;

    public AprsScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.aprs_station"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof AprsStationBlockEntity be)) return;
        int size = Math.min(height - 70, width / 2 - 20), x0 = width / 2 - size - 10, y0 = 30;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.fill(x0, y0, x0 + size, y0 + size, 0xFF0D1A10);
        // range: the farthest report, at least 100 blocks, rounded up
        double far = 100;
        for (var e : be.heard.values()) far = Math.max(far, Math.hypot(e.x() - pos.getX(), e.z() - pos.getZ()));
        double range = Math.pow(10, Math.ceil(Math.log10(far * 1.1) * 2) / 2);
        int cx = x0 + size / 2, cy = y0 + size / 2;
        for (int r = 1; r <= 2; r++) { // range rings
            int rad = size / 2 * r / 2;
            ring(g, cx, cy, rad, 0xFF2E5E36);
        }
        g.fill(cx - 1, y0, cx, y0 + size, 0xFF1E3E24);
        g.fill(x0, cy - 1, x0 + size, cy, 0xFF1E3E24);
        g.drawString(font, "N", cx + 3, y0 + 2, 0xFF80C080);
        g.drawString(font, String.format(Locale.ROOT, "%.0f m", range), x0 + 3, y0 + size - 10, 0xFF80C080);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFFD000);
        long now = mc.level.getGameTime();
        List<String> lines = new ArrayList<>();
        for (var e : be.heard.values()) {
            double dx = e.x() - pos.getX(), dz = e.z() - pos.getZ();
            int px = cx + (int) Math.round(dx / range * size / 2), py = cy + (int) Math.round(dz / range * size / 2);
            int age = (int) Math.max(0, (now - e.tick()) / 20);
            int col = age > 300 ? 0xFF707070 : e.kind() == Aprs.VEHICLE ? 0xFF40C0FF : e.kind() == Aprs.FIXED ? 0xFFFFA040 : 0xFF60FF60;
            if (px > x0 && px < x0 + size && py > y0 && py < y0 + size) {
                g.fill(px - 2, py - 2, px + 2, py + 2, col);
                g.drawString(font, e.name(), px + 4, py - 4, col);
            }
            double dist = Math.hypot(dx, dz), brg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
            lines.add(String.format(Locale.ROOT, "%s  %.0f m %03.0f°  %s", e.name(), dist, brg, ago(age)));
        }
        int lx = width / 2 + 10, ly = y0;
        g.drawString(font, Component.translatable("pgtelegraph.aprs.key", AprsTrackerItem.keyText(be.aprsKey())), lx, ly, 0xFFD0A040);
        ly += 14;
        if (!be.hasAntenna()) {
            g.drawString(font, Component.translatable("pgtelegraph.radio.no_antenna"), lx, ly, 0xFFFF5050);
            ly += 12;
        }
        if (lines.isEmpty()) g.drawString(font, Component.translatable("pgtelegraph.aprs.nothing"), lx, ly, 0xFF909090);
        for (String s : lines) {
            if (ly > y0 + size) break;
            g.drawString(font, s, lx, ly, 0xFFE0E0E0);
            ly += 11;
        }
    }

    private static String ago(int s) {
        return s < 60 ? s + "s" : (s / 60) + "m";
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int a = 0; a < 360; a += 3) {
            int x = cx + (int) Math.round(r * Math.cos(Math.toRadians(a))), y = cy + (int) Math.round(r * Math.sin(Math.toRadians(a)));
            g.fill(x, y, x + 1, y + 1, col);
        }
    }
}
