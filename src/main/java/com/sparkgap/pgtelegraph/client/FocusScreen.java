package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.TelegraphInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard focus mode for a key or paddle. The world keeps running (no pause, no blur);
 * the keyboard and mouse buttons drive the key until ESC.
 *  Straight key: SPACE or left mouse.
 *  Paddle: dit = Z / LEFT ARROW / left mouse, dah = X / RIGHT ARROW / right mouse.
 */
public class FocusScreen extends Screen {
    private static final int KEEPALIVE_TICKS = 4;
    private static final double MAX_DIST_SQ = 7 * 7;

    private final BlockPos pos;
    private final boolean paddle;
    // held[channel]: STRAIGHT=0, DIT=1, DAH=2
    private final boolean[] held = new boolean[3];
    private int sinceKeepAlive;

    public FocusScreen(BlockPos pos, boolean paddle) {
        super(Component.translatable(paddle ? "pgtelegraph.focus.paddle" : "pgtelegraph.focus.key"));
        this.pos = pos;
        this.paddle = paddle;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int channelForKey(int keyCode) {
        if (!paddle) return keyCode == GLFW.GLFW_KEY_SPACE ? TelegraphInput.STRAIGHT : -1;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_LEFT -> TelegraphInput.DIT;
            case GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_RIGHT -> TelegraphInput.DAH;
            default -> -1;
        };
    }

    private int channelForMouse(int button) {
        if (!paddle) return button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? TelegraphInput.STRAIGHT : -1;
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> TelegraphInput.DIT;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> TelegraphInput.DAH;
            default -> -1;
        };
    }

    private void set(int channel, boolean down) {
        if (channel < 0 || held[channel] == down) return;
        held[channel] = down;
        ClientAccess.send(pos, channel, down);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int ch = channelForKey(keyCode);
        if (ch >= 0) { set(ch, true); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers); // ESC closes
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        int ch = channelForKey(keyCode);
        if (ch >= 0) { set(ch, false); return true; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int ch = channelForMouse(button);
        if (ch >= 0) { set(ch, true); return true; }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        int ch = channelForMouse(button);
        if (ch >= 0) { set(ch, false); return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public void tick() {
        var mc = minecraft;
        if (mc == null || mc.player == null || mc.level == null) { onClose(); return; }
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof TelegraphInput) || mc.player.distanceToSqr(pos.getCenter()) > MAX_DIST_SQ) {
            onClose();
            return;
        }
        if (++sinceKeepAlive >= KEEPALIVE_TICKS) {
            sinceKeepAlive = 0;
            for (int ch = 0; ch < held.length; ch++) {
                if (held[ch]) ClientAccess.send(pos, ch, true);
            }
        }
    }

    @Override
    public void removed() {
        for (int ch = 0; ch < held.length; ch++) set(ch, false);
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // keep the world visible: no dim, no blur
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int boxW = 240, boxH = 46;
        int x = (width - boxW) / 2, y = height - boxH - 40;
        g.fill(x, y, x + boxW, y + boxH, 0xB0101010);
        g.drawCenteredString(font, title, width / 2, y + 5, 0xFFE0C060);

        if (paddle) {
            lamp(g, width / 2 - 60, y + 20, held[TelegraphInput.DIT], Component.translatable("pgtelegraph.focus.dit"));
            lamp(g, width / 2 + 10, y + 20, held[TelegraphInput.DAH], Component.translatable("pgtelegraph.focus.dah"));
        } else {
            lamp(g, width / 2 - 25, y + 20, held[TelegraphInput.STRAIGHT], Component.translatable("pgtelegraph.focus.down"));
        }
        g.drawCenteredString(font, Component.translatable(paddle ? "pgtelegraph.focus.paddle_help" : "pgtelegraph.focus.key_help"),
                width / 2, y + boxH + 4, 0xFFAAAAAA);
    }

    private void lamp(GuiGraphics g, int x, int y, boolean on, Component label) {
        g.fill(x, y, x + 10, y + 10, on ? 0xFFFF4030 : 0xFF402020);
        g.drawString(font, label, x + 14, y + 1, 0xFFFFFFFF);
    }
}
