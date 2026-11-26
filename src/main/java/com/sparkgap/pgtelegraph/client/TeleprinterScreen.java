package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.TeleprinterBlockEntity;
import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Teleprinter: the paper roll above, a keyboard line below (Enter sends the line). */
public class TeleprinterScreen extends Screen {
    private static final int W = 300, LINES = 14;
    private final BlockPos pos;
    private EditBox input;

    public TeleprinterScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.teleprinter"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int x0 = (width - W) / 2, y = height / 2 + 60;
        input = new EditBox(font, x0, y, W - 90, 20, Component.empty());
        input.setMaxLength(200);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.teleprinter.send"), b -> send()).bounds(x0 + W - 86, y, 42, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.teleprinter.clear"), b ->
                PacketDistributor.sendToServer(new Batch2Payloads.Teleprinter(pos, 1, ""))).bounds(x0 + W - 42, y, 42, 20).build());
    }

    private void send() {
        String t = input.getValue();
        if (t.isEmpty()) return;
        PacketDistributor.sendToServer(new Batch2Payloads.Teleprinter(pos, 0, t + "\n"));
        input.setValue("");
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(pos) instanceof TeleprinterBlockEntity be)) {
            onClose();
            return;
        }
        int x0 = (width - W) / 2, y0 = height / 2 - 100;
        g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + LINES * 11 + 4, 0xFFF4F0E0);
        List<String> lines = new ArrayList<>();
        for (String para : be.paper().split("\n", -1)) {
            if (para.isEmpty()) {
                lines.add("");
                continue;
            }
            for (var l : font.split(Component.literal(para), W)) {
                StringBuilder sb = new StringBuilder();
                l.accept((i, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                lines.add(sb.toString());
            }
        }
        int from = Math.max(0, lines.size() - LINES);
        for (int i = from; i < lines.size(); i++) g.drawString(font, lines.get(i), x0, y0 + (i - from) * 11, 0x202020, false);
        g.drawString(font, Component.translatable("pgtelegraph.teleprinter.hint"), x0, height / 2 + 48, 0xA0A0A0);
    }
}
