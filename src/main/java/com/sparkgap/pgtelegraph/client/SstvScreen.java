package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.media.Pic;
import com.sparkgap.pgtelegraph.media.PicNet;
import com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** SSTV picture tube. */
public class SstvScreen extends Screen {
    private final BlockPos pos;
    private ClientPics.Tex tex;
    private int shownVersion = -1;
    private int picW, picH;

    public SstvScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.sstv_monitor"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        float k = Math.min(1f, Math.min((height - 70) / (float) Pic.H, (width - 24) / (float) Pic.W));
        picW = Math.round(Pic.W * k);
        picH = Math.round(Pic.H * k);
        if (tex == null) {
            tex = new ClientPics.Tex();
            PacketDistributor.sendToServer(new PicNet.MonitorWant(pos));
        }
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.radio.tune"), b -> {
            if (Minecraft.getInstance().level.getBlockEntity(pos) instanceof SstvMonitorBlockEntity be) {
                onClose();
                ClientAccess.openTune(pos, be.freqHz());
            }
        }).bounds(width / 2 - 40, (height + picH) / 2 + 18, 80, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!(Minecraft.getInstance().level.getBlockEntity(pos) instanceof SstvMonitorBlockEntity be)) {
            onClose();
            return;
        }
        int x0 = (width - picW) / 2, y0 = (height - picH) / 2;
        g.fill(x0 - 4, y0 - 4, x0 + picW + 4, y0 + picH + 4, 0xFF303030);
        if (tex == null || tex.w != be.imgW || tex.h != be.imgH) {
            if (tex != null) tex.close();
            tex = new ClientPics.Tex(be.imgW, be.imgH);
            shownVersion = -1;
        }
        if (be.version != shownVersion) {
            shownVersion = be.version;
            tex.update(be.image);
        }
        tex.draw(g, x0, y0, picW, picH);
        if (be.row >= 0) {
            int ly = y0 + (be.row + 1) * picH / Math.max(1, be.imgH);
            g.fill(x0, ly, x0 + picW, ly + 1, 0x80FFFFFF);
        }
        String st = be.row >= 0 ? String.format(Locale.ROOT, "%s  %s %d/%d  SNR %.0f dB", be.from,
                com.sparkgap.pgtelegraph.media.SstvMode.of(be.mode).label, be.row + 1, be.imgH, be.snr)
                : (be.from.isEmpty() ? "-" : be.from);
        g.drawCenteredString(font, st, width / 2, y0 - 16, 0xFFFFFF);
        g.drawCenteredString(font, com.sparkgap.pgtelegraph.block.RadioBlockEntity.formatFreq(be.freqHz()), width / 2, y0 + picH + 6, 0x80C0FF);
    }

    @Override
    public void removed() {
        if (tex != null) tex.close();
        super.removed();
    }
}
