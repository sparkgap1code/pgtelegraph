package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.LoadingCoilBlockEntity;
import com.sparkgap.pgtelegraph.network.CoilPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Variometer knob: type mH or nudge it. Resonance moves with 1/√L, so ±0.2 % of L shifts the
 * antenna by about ±0.1 % - fine enough for a VLF antenna's ~100 Hz bandwidth. Wheel = ±0.2 %.
 */
public class CoilScreen extends Screen {
    private final BlockPos pos;
    private double mH;
    private EditBox box;

    public CoilScreen(BlockPos pos, double mH) {
        super(net.minecraft.client.Minecraft.getInstance().level == null ? Component.translatable("block.pgtelegraph.loading_coil")
                : net.minecraft.client.Minecraft.getInstance().level.getBlockState(pos).getBlock().getName());
        this.pos = pos;
        this.mH = mH;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 40;
        box = new EditBox(font, cx - 50, y, 100, 20, Component.empty());
        box.setValue(fmt(mH));
        box.setResponder(s -> {
            try {
                mH = clamp(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(box);
        double[] f = {0.1, 0.9, 0.98, 0.998, 1.002, 1.02, 1.11, 10};
        String[] l = {"÷10", "-10%", "-2%", "-0.2%", "+0.2%", "+2%", "+10%", "×10"};
        for (int i = 0; i < f.length; i++) {
            double k = f[i];
            addRenderableWidget(Button.builder(Component.literal(l[i]), b -> set(mH * k))
                    .bounds(cx - 160 + i * 40, y + 26, 38, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx - 40, y + 56, 80, 20).build());
    }

    private static double clamp(double v) {
        return Math.max(LoadingCoilBlockEntity.MIN_MH, Math.min(LoadingCoilBlockEntity.MAX_MH, v));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.5g", v);
    }

    private void set(double v) {
        mH = clamp(v);
        box.setValue(fmt(mH));
        PacketDistributor.sendToServer(new CoilPayload(pos, mH));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        set(mH * (sy > 0 ? 1.002 : 1 / 1.002));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            set(mH);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new CoilPayload(pos, mH));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2, y = height / 2 - 40;
        g.drawCenteredString(font, title, cx, y - 26, 0xFFFFFF);
        g.drawString(font, "mH", cx + 56, y + 6, 0xA0A0A0);
        g.drawCenteredString(font, Component.translatable("pgtelegraph.coil.help"), cx, y + 84, 0x909090);
    }
}
