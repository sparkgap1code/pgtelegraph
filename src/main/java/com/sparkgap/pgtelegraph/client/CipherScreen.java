package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.item.RotorCipher;
import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rotor cipher machine: key line ("rotors start plugs", e.g. "152 KQX AB CD"), text in, text out.
 * The output can be copied to the clipboard and typed into a perforator, beacon or teleprinter.
 */
public class CipherScreen extends Screen {
    private static final int W = 300;
    private final InteractionHand hand;
    private String key;
    private EditBox keyBox, in;
    private String out = "";

    public CipherScreen(InteractionHand hand, String key) {
        super(Component.translatable("item.pgtelegraph.cipher_machine"));
        this.hand = hand;
        this.key = key;
    }

    @Override
    protected void init() {
        int x0 = (width - W) / 2, y = height / 2 - 60;
        keyBox = new EditBox(font, x0, y, W, 18, Component.empty());
        keyBox.setMaxLength(100);
        keyBox.setValue(key);
        keyBox.setResponder(s -> {
            key = s;
            update();
        });
        addRenderableWidget(keyBox);
        in = new EditBox(font, x0, y + 40, W, 18, Component.empty());
        in.setMaxLength(256);
        in.setResponder(s -> update());
        addRenderableWidget(in);
        setInitialFocus(in);
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.cipher.copy"), b ->
                Minecraft.getInstance().keyboardHandler.setClipboard(out)).bounds(x0, y + 110, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x0 + W - 80, y + 110, 80, 20).build());
    }

    private void update() {
        out = RotorCipher.run(in == null ? "" : in.getValue(), key);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new Batch2Payloads.Cipher(hand == InteractionHand.OFF_HAND, key));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x0 = (width - W) / 2, y = height / 2 - 60;
        g.drawCenteredString(font, title, width / 2, y - 24, 0xFFFFFF);
        g.drawString(font, Component.translatable("pgtelegraph.cipher.key"), x0, y - 11, 0xA0A0A0);
        g.drawString(font, Component.translatable("pgtelegraph.cipher.in"), x0, y + 29, 0xA0A0A0);
        g.drawString(font, Component.translatable("pgtelegraph.cipher.out"), x0, y + 66, 0xA0A0A0);
        g.fill(x0, y + 77, x0 + W, y + 103, 0xFF101010);
        int i = 0;
        for (var line : font.split(Component.literal(out), W - 4)) {
            if (i >= 2) break;
            g.drawString(font, line, x0 + 2, y + 79 + i++ * 12, 0x80FF80);
        }
    }
}
