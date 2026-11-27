package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Which set does this coax go to: one button per radio touching it, and "any" (the old way). */
public class CoaxScreen extends Screen {
    private final Batch2Payloads.CoaxChoice m;

    public CoaxScreen(Batch2Payloads.CoaxChoice m) {
        super(Component.translatable("pgtelegraph.coax.title"));
        this.m = m;
    }

    @Override
    protected void init() {
        int w = 260, x = width / 2 - w / 2, y = height / 2 - 12 * (m.devices().size() + 1);
        for (int i = 0; i < m.devices().size(); i++) {
            BlockPos d = m.devices().get(i);
            boolean cur = m.hasCurrent() && m.current().equals(d);
            addRenderableWidget(Button.builder(Component.literal((cur ? "\u25b6 " : "") + m.names().get(i)), b -> answer(d, false))
                    .bounds(x, y + i * 24, w, 20).build());
        }
        int yy = y + m.devices().size() * 24 + 6;
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.coax.any_button"), b -> answer(BlockPos.ZERO, true))
                .bounds(x, yy, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + w / 2 + 2, yy, w / 2 - 2, 20).build());
    }

    private void answer(BlockPos d, boolean any) {
        PacketDistributor.sendToServer(new Batch2Payloads.CoaxAnswer(m.coax(), d, any));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int y = height / 2 - 12 * (m.devices().size() + 1);
        g.drawCenteredString(font, title, width / 2, y - 26, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("pgtelegraph.coax.hint"), width / 2, y - 13, 0xA0A0A0);
    }
}
