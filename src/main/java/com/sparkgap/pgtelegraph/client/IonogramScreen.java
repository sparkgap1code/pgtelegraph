package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.IonosondeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Ionogram: virtual height (0-800 km) of the echo against frequency (1-20 MHz). */
public class IonogramScreen extends Screen {
    private static final int W = 300, H = 160;
    private static final double FMAX = 20, HMAX = 800;
    private final BlockPos pos;

    public IonogramScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.ionosonde"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!(Minecraft.getInstance().level.getBlockEntity(pos) instanceof IonosondeBlockEntity be)) {
            onClose();
            return;
        }
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF404040);
        g.fill(x0, y0, x0 + W, y0 + H, 0xFF000810);
        for (int f = 2; f <= 20; f += 2) {
            int x = x0 + (int) (f / FMAX * W);
            g.fill(x, y0, x + 1, y0 + H, 0xFF102030);
            g.drawString(font, String.valueOf(f), x - 4, y0 + H + 3, 0x808080);
        }
        for (int h = 200; h <= 800; h += 200) {
            int y = y0 + H - (int) (h / HMAX * H);
            g.fill(x0, y, x0 + W, y + 1, 0xFF102030);
            g.drawString(font, h + "", x0 - 22, y - 4, 0x808080);
        }
        if (!be.hasAntenna) {
            g.drawCenteredString(font, Component.translatable("pgtelegraph.radio.no_antenna"), width / 2, y0 + H / 2, 0xFF6060);
            return;
        }
        for (int px = 0; px < W; px++) {
            double f = (px + 0.5) / W * FMAX;
            double h = IonosondeBlockEntity.virtualHeight(f, be.foE, be.foF2, be.fMin);
            if (h < 0) continue;
            int y = y0 + H - (int) Math.min(H - 1, h / HMAX * H);
            g.fill(x0 + px, y, x0 + px + 1, y + 2, h < 200 ? 0xFF60FF60 : 0xFFFFD040);
        }
        int xf = x0 + (int) (be.foF2 / FMAX * W);
        g.fill(xf, y0, xf + 1, y0 + H, 0x80FF4040);
        int xm = x0 + (int) Math.min(W - 1, be.muf / FMAX * W);
        g.fill(xm, y0, xm + 1, y0 + H, 0x8040A0FF);
        g.drawCenteredString(font, title, width / 2, y0 - 26, 0xFFFFFF);
        g.drawCenteredString(font, String.format(Locale.ROOT, "foE %.1f   foF2 %.1f   MUF(3000) %.1f MHz   fmin %.1f   Kp %d%s",
                be.foE, be.foF2, be.muf, be.fMin, be.kp, be.flare ? "   FLARE" : ""), width / 2, y0 - 13,
                be.flare || be.kp > 4 ? 0xFF8060 : 0xC0E0FF);
        g.drawString(font, "MHz", x0 + W - 18, y0 + H + 13, 0x808080);
    }
}
