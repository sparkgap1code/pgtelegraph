package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.AimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Antenna rotator: azimuth (compass, 0 = north, 90 = east) and elevation. "Where I look" copies the
 * player's view direction, handy for pointing at a distant tower you can see.
 */
public class AimScreen extends Screen {
    private final BlockPos pos;
    private double az, el;
    private EditBox azBox, elBox, satBox;
    private int track;
    private String satName = "";

    public AimScreen(BlockPos pos, double az, double el) {
        super(Component.translatable("pgtelegraph.vhf.aim_title"));
        this.pos = pos;
        this.az = az;
        this.el = el;
    }

    public AimScreen(BlockPos pos, double az, double el, int track, String satName) {
        this(pos, az, el);
        this.track = track;
        this.satName = satName;
    }

    private Component trackLabel() {
        return Component.translatable("pgtelegraph.vhf.track_btn." + track);
    }

    private void sendTrack() {
        PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.Track(pos, track, satName));
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 50;
        azBox = new EditBox(font, cx - 90, y, 70, 20, Component.empty());
        elBox = new EditBox(font, cx + 20, y, 70, 20, Component.empty());
        azBox.setResponder(s -> parse(s, true));
        elBox.setResponder(s -> parse(s, false));
        refresh();
        addRenderableWidget(azBox);
        addRenderableWidget(elBox);
        double[] steps = {-10, -1, -0.1, 0.1, 1, 10};
        String[] l = {"-10", "-1", "-0.1", "+0.1", "+1", "+10"};
        for (int i = 0; i < steps.length; i++) {
            double st = steps[i];
            addRenderableWidget(Button.builder(Component.literal(l[i]), b -> set(az + st, el))
                    .bounds(cx - 141 + i * 47, y + 26, 45, 20).build());
            addRenderableWidget(Button.builder(Component.literal(l[i]), b -> set(az, el + st))
                    .bounds(cx - 141 + i * 47, y + 50, 45, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.vhf.aim_look"), b -> {
            var p = Minecraft.getInstance().player;
            if (p != null) set(p.getYRot() + 180, -p.getXRot());
        }).bounds(cx - 100, y + 80, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 5, y + 80, 95, 20).build());
        addRenderableWidget(Button.builder(trackLabel(), b -> {
            track = (track + 1) % 3;
            b.setMessage(trackLabel());
            sendTrack();
        }).bounds(cx - 141, y + 108, 110, 20).build());
        satBox = new EditBox(font, cx - 25, y + 108, 166, 20, Component.empty());
        satBox.setHint(Component.translatable("pgtelegraph.vhf.sat_hint"));
        satBox.setMaxLength(24);
        satBox.setValue(satName);
        satBox.setResponder(s -> satName = s);
        addRenderableWidget(satBox);
    }

    private void parse(String s, boolean isAz) {
        try {
            double v = Double.parseDouble(s.trim());
            if (isAz) az = v;
            else el = v;
        } catch (NumberFormatException ignored) {
        }
    }

    private void refresh() {
        azBox.setValue(String.format(Locale.ROOT, "%.1f", az));
        elBox.setValue(String.format(Locale.ROOT, "%.1f", el));
    }

    private void set(double a, double e) {
        track = 0;
        az = Mth.positiveModulo(a, 360);
        el = Mth.clamp(e, -10, 90);
        refresh();
        PacketDistributor.sendToServer(new AimPayload(pos, az, el));
    }

    @Override
    public void onClose() {
        if (track == 0) PacketDistributor.sendToServer(new AimPayload(pos, Mth.positiveModulo(az, 360), Mth.clamp(el, -10, 90)));
        else sendTrack();
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
        g.drawCenteredString(font, title, cx, y - 26, 0xFFFFFF);
        g.drawString(font, Component.translatable("pgtelegraph.vhf.azimuth"), cx - 90, y - 11, 0xA0A0A0);
        g.drawString(font, Component.translatable("pgtelegraph.vhf.elevation"), cx + 20, y - 11, 0xA0A0A0);
    }
}
