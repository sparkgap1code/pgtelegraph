package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.BeaconProgram;
import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Beacon program: message, mode, speed, pause. {CALL} in the message becomes the callsign. */
public class BeaconScreen extends Screen {
    private final BlockPos pos;
    private String msg;
    private int wpm, mode, gap;
    private EditBox box;
    private Button modeBtn;

    public BeaconScreen(BlockPos pos, BeaconProgram p) {
        super(Component.translatable("pgtelegraph.beacon.title"));
        this.pos = pos;
        msg = p.message;
        wpm = p.wpm;
        mode = p.mode;
        gap = p.gapSec;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 50;
        box = new EditBox(font, cx - 140, y, 280, 20, Component.empty());
        box.setMaxLength(BeaconProgram.MAX_LEN);
        box.setValue(msg);
        box.setResponder(s -> msg = s);
        addRenderableWidget(box);
        modeBtn = addRenderableWidget(Button.builder(modeName(), b -> {
            mode = (mode + 1) % 3;
            b.setMessage(modeName());
        }).bounds(cx - 140, y + 28, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> wpm = Math.max(5, wpm - 1)).bounds(cx - 10, y + 28, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> wpm = Math.min(40, wpm + 1)).bounds(cx + 50, y + 28, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> gap = Math.max(0, gap - 5)).bounds(cx + 80, y + 28, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> gap = Math.min(600, gap + 5)).bounds(cx + 120, y + 28, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 40, y + 60, 80, 20).build());
    }

    private Component modeName() {
        return Component.translatable("pgtelegraph.beacon.mode." + mode);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new Batch2Payloads.Beacon(pos, msg, wpm, mode, gap));
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
        g.drawString(font, Component.translatable("pgtelegraph.beacon.hint"), cx - 140, y - 12, 0xA0A0A0);
        g.drawCenteredString(font, wpm + " WPM", cx + 30, y + 34, 0xFFFFFF);
        g.drawCenteredString(font, gap + " s", cx + 110, y + 34, 0xFFFFFF);
    }
}
