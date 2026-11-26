package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.media.Photo;
import com.sparkgap.pgtelegraph.media.Pic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Looking at a photo. */
public class PhotoScreen extends Screen {
    private final Photo photo;
    private ClientPics.Tex tex;
    private boolean shown;

    public PhotoScreen(Photo photo) {
        super(Component.translatable("item.pgtelegraph.photo"));
        this.photo = photo;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        float k = Math.min(1f, Math.min((height - 40) / (float) Pic.H, (width - 24) / (float) Pic.W));
        int w = Math.round(Pic.W * k), h = Math.round(Pic.H * k), x0 = (width - w) / 2, y0 = (height - h) / 2 - 6;
        g.fill(x0 - 6, y0 - 6, x0 + w + 6, y0 + h + 18, 0xFFF0EEE6);
        if (!shown) {
            byte[] img = ClientPics.photo(photo);
            if (img != null) {
                if (tex == null) tex = new ClientPics.Tex();
                tex.update(img);
                shown = true;
            }
        }
        if (shown) tex.draw(g, x0, y0, w, h);
        else {
            g.fill(x0, y0, x0 + w, y0 + h, 0xFF202020);
            g.drawCenteredString(font, "...", width / 2, y0 + h / 2 - 4, 0xFFFFFF);
        }
        g.drawCenteredString(font, photo.caption(), width / 2, y0 + h + 5, 0xFF404040);
    }

    @Override
    public void removed() {
        if (tex != null) tex.close();
        super.removed();
    }
}
