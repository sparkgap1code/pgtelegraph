package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.network.PerforatePayload;
import com.sparkgap.pgtelegraph.tape.Morse;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Type a message; Enter or the button punches it onto the held blank tape. */
public class PerforateScreen extends Screen {
    private final BlockPos pos;
    private EditBox input;

    public PerforateScreen(BlockPos pos) {
        super(Component.translatable("block.pgtelegraph.perforator"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int w = Math.min(300, width - 40);
        int x = (width - w) / 2;
        int y = height / 2 - 40;
        input = new EditBox(font, x, y, w, 20, Component.translatable("pgtelegraph.perforator.input"));
        input.setMaxLength(PerforatePayload.MAX_CHARS);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.perforator.punch"), b -> punch())
                .bounds(x + w - 80, y + 90, 80, 20).build());
    }

    private void punch() {
        String text = input.getValue().strip();
        if (!text.isEmpty() && !Morse.codes(text).isEmpty()) {
            PacketDistributor.sendToServer(new PerforatePayload(pos, text));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            punch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = Math.min(300, width - 40);
        int x = (width - w) / 2;
        int y = height / 2 - 40;
        g.drawCenteredString(font, title, width / 2, y - 24, 0xFFFFFF);
        g.drawString(font, Component.translatable("pgtelegraph.perforator.help"), x, y - 12, 0xA0A0A0);
        String preview = Morse.preview(Morse.codes(input.getValue()));
        List<FormattedCharSequence> lines = font.split(Component.literal(preview), w);
        for (int i = 0; i < Math.min(lines.size(), 6); i++) {
            g.drawString(font, lines.get(i), x, y + 26 + i * 10, 0xE0C060);
        }
    }
}
