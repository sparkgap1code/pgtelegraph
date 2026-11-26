package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import com.sparkgap.pgtelegraph.sat.SatConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** CubeSat set-up: every payload function of the satellite, toggles plus frequencies (MHz). */
public class CubeSatScreen extends Screen {
    private static final int W = 320;
    private final InteractionHand hand;
    private final SatConfig cfg;

    public CubeSatScreen(InteractionHand hand, String config) {
        super(Component.translatable("item.pgtelegraph.cubesat"));
        this.hand = hand;
        this.cfg = SatConfig.parse(config);
    }

    private int x0, y0;

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = height / 2 - 134;
        int y = y0;
        text("NAME", x0 + 40, y, 90, false);
        num("POWER", x0 + 200, y, 40, 1);
        y += 26;
        toggle("CW", x0, y);
        num("CWHZ", x0 + 100, y, 70, 1e6);
        num("CWWPM", x0 + 210, y, 30, 1);
        num("CWGAP", x0 + 280, y, 30, 1);
        y += 22;
        text("CWTEXT", x0 + 40, y, W - 40, true);
        y += 26;
        toggle("XP", x0, y);
        num("XPUP", x0 + 100, y, 70, 1e6);
        num("XPDOWN", x0 + 210, y, 70, 1e6);
        y += 22;
        num("XPWIDTH", x0 + 100, y, 50, 1e3);
        toggle("XPINV", x0 + 160, y);
        y += 26;
        toggle("RTTY", x0, y);
        num("RTTYUP", x0 + 100, y, 70, 1e6);
        num("RTTYDOWN", x0 + 210, y, 70, 1e6);
        y += 22;
        toggle("IONO", x0, y);
        toggle("BBS", x0 + 100, y);
        num("RTTYGAP", x0 + 260, y, 40, 1);
        y += 22;
        toggle("SSTV", x0, y);
        num("SSTVHZ", x0 + 100, y, 70, 1e6);
        toggle("ISL", x0 + 210, y);
        y += 22;
        toggle("TV", x0, y);
        num("TVUP", x0 + 100, y, 70, 1e6);
        num("TVDOWN", x0 + 210, y, 70, 1e6);
        y += 22;
        toggle("TVCAM", x0, y);
        String[] ants = {"OMNI", "EARTH", "SPOT"};
        addRenderableWidget(Button.builder(antLabel(), b -> {
            int i = java.util.Arrays.asList(ants).indexOf(cfg.get("ANT"));
            cfg.set("ANT", ants[(i + 1) % ants.length]);
            b.setMessage(antLabel());
        }).bounds(x0 + 100, y, 200, 18).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 40, y, 80, 20).build());
    }

    private Component antLabel() {
        return Component.translatable("pgtelegraph.cubesat.ant").append(": ")
                .append(Component.translatable("pgtelegraph.cubesat.ant." + cfg.get("ANT").toLowerCase(Locale.ROOT)));
    }

    private void toggle(String key, int x, int y) {
        addRenderableWidget(Button.builder(label(key), b -> {
            cfg.set(key, cfg.on(key) ? "0" : "1");
            b.setMessage(label(key));
        }).bounds(x, y, 94, 18).build());
    }

    private Component label(String key) {
        return Component.translatable("pgtelegraph.cubesat." + key.toLowerCase(Locale.ROOT))
                .append(": ").append(Component.translatable(cfg.on(key) ? "options.on" : "options.off"));
    }

    /** numeric field shown in units of {@code scale} (1e6 = MHz) */
    private void num(String key, int x, int y, int w, double scale) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        double v = Double.parseDouble(cfg.get(key)) / scale;
        b.setValue(scale >= 1e6 ? String.format(Locale.ROOT, "%.4f", v) : scale > 1 ? String.format(Locale.ROOT, "%.1f", v)
                : cfg.get(key));
        b.setResponder(s -> {
            try {
                cfg.set(key, String.valueOf(Double.parseDouble(s.trim()) * scale));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(b);
    }

    private void text(String key, int x, int y, int w, boolean free) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        b.setMaxLength(free ? 80 : 12);
        b.setValue(cfg.get(key));
        b.setResponder(s -> {
            if (!s.isBlank()) cfg.set(key, s);
        });
        addRenderableWidget(b);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new Batch2Payloads.CubeSat(hand == InteractionHand.OFF_HAND, cfg.serialize()));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, y0 - 16, 0xFFFFFF);
        int c = 0xA0A0A0, y = y0 + 5;
        g.drawString(font, Component.translatable("pgtelegraph.cubesat.name"), x0, y, c);
        g.drawString(font, "W", x0 + 244, y, c);
        g.drawString(font, Component.translatable("pgtelegraph.cubesat.power"), x0 + 150, y, c);
        y += 26;
        g.drawString(font, "MHz", x0 + 172, y, c);
        g.drawString(font, "WPM", x0 + 242, y, c);
        g.drawString(font, "s", x0 + 312, y, c);
        y += 22;
        g.drawString(font, Component.translatable("pgtelegraph.cubesat.text"), x0, y, c);
        y += 26;
        g.drawString(font, "→", x0 + 196, y, c);
        g.drawString(font, "MHz", x0 + 284, y, c);
        y += 22;
        g.drawString(font, Component.translatable("pgtelegraph.cubesat.width"), x0 + 4, y, c);
        g.drawString(font, "kHz", x0 + 152, y + 5, c);
        y += 26;
        g.drawString(font, "→", x0 + 196, y, c);
        g.drawString(font, "MHz", x0 + 284, y, c);
        y += 22;
        g.drawString(font, Component.translatable("pgtelegraph.cubesat.gap"), x0 + 200, y, c);
        y += 22;
        g.drawString(font, "MHz", x0 + 172, y + 5, c);
        g.drawString(font, Component.translatable("pgtelegraph.cubesat.hint"), x0, y0 + 256, 0x808080);
    }
}
