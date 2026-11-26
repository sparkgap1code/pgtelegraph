package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity;
import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** DTMF decoder settings: mode, ON code, OFF code. */
public class DtmfScreen extends Screen {
    private final BlockPos pos;
    private String on, off;
    private int mode;
    private EditBox onBox, offBox, freqBox;
    private long hz;

    public DtmfScreen(BlockPos pos, String on, String off, int mode, long hz) {
        super(Component.translatable("block.pgtelegraph.dtmf_decoder"));
        this.pos = pos;
        this.on = on;
        this.off = off;
        this.mode = mode;
        this.hz = hz;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 40;
        freqBox = new EditBox(font, cx - 20, y - 28, 120, 20, Component.empty());
        freqBox.setValue(String.format(java.util.Locale.ROOT, "%.4f", hz / 1e6));
        freqBox.setResponder(s -> {
            try {
                hz = Math.round(Double.parseDouble(s.trim()) * 1e6);
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(freqBox);
        addRenderableWidget(Button.builder(modeName(), b -> {
            mode = (mode + 1) % DtmfDecoderBlockEntity.MODES;
            b.setMessage(modeName());
            updateBoxes();
        }).bounds(cx - 100, y, 200, 20).build());
        onBox = new EditBox(font, cx - 20, y + 28, 120, 20, Component.empty());
        onBox.setMaxLength(DtmfDecoderBlockEntity.MAX_CODE);
        onBox.setValue(on);
        onBox.setFilter(s -> DtmfDecoderBlockEntity.clean(s).length() == s.length());
        onBox.setResponder(s -> on = s);
        addRenderableWidget(onBox);
        offBox = new EditBox(font, cx - 20, y + 54, 120, 20, Component.empty());
        offBox.setMaxLength(DtmfDecoderBlockEntity.MAX_CODE);
        offBox.setValue(off);
        offBox.setFilter(s -> DtmfDecoderBlockEntity.clean(s).length() == s.length());
        offBox.setResponder(s -> off = s);
        addRenderableWidget(offBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 40, y + 84, 80, 20).build());
        updateBoxes();
    }

    private void updateBoxes() {
        onBox.visible = mode != DtmfDecoderBlockEntity.DIGIT;
        offBox.visible = mode == DtmfDecoderBlockEntity.LATCH;
    }

    private Component modeName() {
        return Component.translatable("pgtelegraph.dtmf.mode." + mode);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new Batch2Payloads.DtmfSet(pos, on, off, mode, hz));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2, y = height / 2 - 40;
        g.drawCenteredString(font, title, cx, y - 56, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("pgtelegraph.dtmf.hint." + mode), cx, y - 42, 0xA0A0A0);
        g.drawString(font, Component.translatable("pgtelegraph.dtmf.freq_label"), cx - 100, y - 22, 0xFFFFFF);
        if (onBox.visible) g.drawString(font, Component.translatable("pgtelegraph.dtmf.on_label"), cx - 100, y + 34, 0xFFFFFF);
        if (offBox.visible) g.drawString(font, Component.translatable("pgtelegraph.dtmf.off_label"), cx - 100, y + 60, 0xFFFFFF);
    }
}
