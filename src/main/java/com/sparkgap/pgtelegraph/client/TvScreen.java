package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The TV set, close up: the picture as large as the window allows, with power and channel. */
public class TvScreen extends Screen {
    private final BlockPos pos;

    public TvScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.tv_receiver"));
        this.pos = pos;
    }

    private TvReceiverBlockEntity be() {
        var l = Minecraft.getInstance().level;
        return l != null && l.getBlockEntity(pos) instanceof TvReceiverBlockEntity t ? t : null;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.tv.power"), b ->
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.sparkgap.pgtelegraph.network.Batch2Payloads.RadioMode(pos, 199))).bounds(cx - 102, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.tv.channel"), b -> {
            TvReceiverBlockEntity t = be();
            if (t != null) {
                onClose();
                ClientAccess.openTune(pos, t.freqHz());
            }
        }).bounds(cx + 2, height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        TvReceiverBlockEntity t = be();
        if (t == null) {
            onClose();
            return;
        }
        int maxW = width - 20, maxH = height - 60;
        int w = Math.min(maxW, maxH * 4 / 3), h = w * 3 / 4;
        int x = (width - w) / 2, y = 22;
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF202020);
        if (t.isOn()) g.blit(TvRenderer.picture(t), x, y, w, h, 0f, 0f, TvRenderer.W, TvRenderer.H, TvRenderer.W, TvRenderer.H);
        else g.fill(x, y, x + w, y + h, 0xFF050505);
        String st = RadioBlockEntity.formatFreq(t.freqHz()) + (t.tuned ? String.format(Locale.ROOT, "   SNR %.0f dB", t.snr) : "");
        g.drawCenteredString(font, st, width / 2, 8, 0x80C0FF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
