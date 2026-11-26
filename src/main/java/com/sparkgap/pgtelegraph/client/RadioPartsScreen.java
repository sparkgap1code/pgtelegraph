package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.parts.RadioPartsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Parts bay drawn with plain panels (no texture asset): labelled slots in a row. */
public class RadioPartsScreen extends AbstractContainerScreen<RadioPartsMenu> {
    public RadioPartsScreen(RadioPartsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2F36);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + 66, 0xFF3A404A);
        g.fill(x + 2, y + 68, x + imageWidth - 2, y + imageHeight - 2, 0xFF353A42);
        for (var s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF1A1D22);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF4A505A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, 0xE0E0E0, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xB0B0B0, false);
        int n = menu.layout.slots.length, x0 = 88 - n * 11;
        for (int i = 0; i < n; i++) {
            String name = Component.translatable(menu.layout.slots[i].langKey()).getString();
            g.pose().pushPose();
            g.pose().translate(x0 + i * 22 + 8, 50, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.drawCenteredString(font, name, 0, 0, 0xC8D0DA);
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
