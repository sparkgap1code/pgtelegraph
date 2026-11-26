package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import com.sparkgap.pgtelegraph.network.HandheldSetPayload;
import com.sparkgap.pgtelegraph.radio.HandheldData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Handheld front panel: power switch, dial (2 m / 70 cm), output power, squelch. */
public class HandheldScreen extends Screen {
    private final boolean offHand;
    private long hz;
    private int power, squelch;
    private boolean on, paddle;
    private int wpm;
    private final float batteryPct;
    /** output steps: a manpack has a bigger PA and supply */
    private final float[] powerW;
    private EditBox box;
    private Button onBtn, pwBtn;

    public HandheldScreen(InteractionHand hand, HandheldData d, int batteryPct, boolean manpack) {
        super(Component.translatable(manpack ? "item.pgtelegraph.manpack_radio" : "item.pgtelegraph.handheld_radio"));
        this.powerW = manpack ? com.sparkgap.pgtelegraph.radio.ManpackItem.POWER_W : HandheldData.POWER_W;
        this.offHand = hand == InteractionHand.OFF_HAND;
        this.hz = d.freqHz();
        this.power = d.power();
        this.squelch = d.squelch();
        this.on = d.on();
        this.paddle = d.paddle();
        this.wpm = d.wpm();
        this.batteryPct = batteryPct;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 60;
        onBtn = addRenderableWidget(Button.builder(onLabel(), b -> {
            on = !on;
            b.setMessage(onLabel());
            push();
        }).bounds(cx - 100, y, 60, 20).build());
        box = new EditBox(font, cx - 30, y, 90, 20, Component.empty());
        box.setValue(fmt(hz));
        box.setResponder(s -> {
            try {
                hz = HandheldData.clampFreq(Math.round(Double.parseDouble(s.trim()) * 1e6));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(box);
        long[] steps = {-1_000_000, -25_000, -1000, -100, 100, 1000, 25_000, 1_000_000};
        String[] l = {"-1M", "-25k", "-1k", "-100", "+100", "+1k", "+25k", "+1M"};
        for (int i = 0; i < steps.length; i++) {
            long st = steps[i];
            addRenderableWidget(Button.builder(Component.literal(l[i]), b -> setHz(hz + st))
                    .bounds(cx - 144 + i * 36, y + 26, 34, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("2 m"), b -> setHz(145_000_000L)).bounds(cx - 100, y + 52, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("70 cm"), b -> setHz(435_000_000L)).bounds(cx - 48, y + 52, 48, 20).build());
        pwBtn = addRenderableWidget(Button.builder(pwLabel(), b -> {
            power = (power + 1) % powerW.length;
            b.setMessage(pwLabel());
            push();
        }).bounds(cx + 4, y + 52, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SQL -"), b -> {
            squelch = Math.max(0, squelch - 2);
            push();
        }).bounds(cx - 100, y + 78, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SQL +"), b -> {
            squelch = Math.min(30, squelch + 2);
            push();
        }).bounds(cx - 48, y + 78, 48, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.handheld.parts"), b -> {
            push();
            PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.OpenPartsPayload(offHand));
        }).bounds(cx + 4, y + 78, 46, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 54, y + 78, 46, 20).build());
        // CTCSS tone (sent under the signal; the squelch only opens for it)
        net.minecraft.world.item.ItemStack held = net.minecraft.client.Minecraft.getInstance().player.getItemInHand(
                offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        tone = held.getOrDefault(com.sparkgap.pgtelegraph.PgTelegraph.CTCSS.get(), 0);
        addRenderableWidget(Button.builder(toneLabel(), b -> {
            tone = com.sparkgap.pgtelegraph.radio.Ctcss.clamp(tone + (hasShiftDown() ? -1 : 1));
            b.setMessage(toneLabel());
            PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.HandheldTone(offHand, tone));
        }).bounds(cx + 108, y + 140, 74, 20).build());
        // DTMF keypad: each press keys the radio for a short touch-tone burst
        for (int i = 0; i < com.sparkgap.pgtelegraph.radio.Dtmf.KEYS.length(); i++) {
            char c = com.sparkgap.pgtelegraph.radio.Dtmf.KEYS.charAt(i);
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(c)),
                            b -> PacketDistributor.sendToServer(new com.sparkgap.pgtelegraph.network.Batch2Payloads.DtmfKey(c)))
                    .bounds(cx + 108 + (i % 4) * 19, y + 62 + (i / 4) * 19, 18, 18).build());
        }
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            paddle = !paddle;
            b.setMessage(modeLabel());
            push();
        }).bounds(cx - 100, y + 136, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("WPM -"), b -> {
            wpm = HandheldData.clampWpm(wpm - 1);
            push();
        }).bounds(cx + 4, y + 136, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("WPM +"), b -> {
            wpm = HandheldData.clampWpm(wpm + 1);
            push();
        }).bounds(cx + 52, y + 136, 48, 20).build());
    }

    private int tone;

    private Component toneLabel() {
        return Component.literal("CTCSS " + com.sparkgap.pgtelegraph.radio.Ctcss.label(tone));
    }

    private Component onLabel() {
        return Component.translatable(on ? "pgtelegraph.handheld.on" : "pgtelegraph.handheld.off");
    }

    private Component modeLabel() {
        return Component.translatable(paddle ? "pgtelegraph.handheld.mode.paddle" : "pgtelegraph.handheld.mode.straight");
    }

    private Component pwLabel() {
        return Component.translatable("pgtelegraph.handheld.power", powerW[power]);
    }

    private static String fmt(long hz) {
        return String.format(Locale.ROOT, "%.4f", hz / 1e6);
    }

    private void setHz(long v) {
        hz = HandheldData.clampFreq(v);
        box.setValue(fmt(hz));
        push();
    }

    private void push() {
        PacketDistributor.sendToServer(new HandheldSetPayload(offHand, hz, power, squelch, on, paddle, wpm));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        setHz(hz + (long) Math.signum(sy) * 1000);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            setHz(hz);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        push();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2, y = height / 2 - 60;
        g.drawCenteredString(font, title, cx, y - 26, 0xFFFFFF);
        g.drawString(font, "MHz", cx + 64, y + 6, 0xA0A0A0);
        g.drawString(font, "DTMF", cx + 108, y + 52, 0xA0A0A0);
        g.drawString(font, Component.translatable("pgtelegraph.handheld.squelch", squelch), cx - 100, y + 104, 0xA0A0A0);
        g.drawString(font, batteryPct < 0 ? Component.translatable("pgtelegraph.handheld.no_battery")
                : Component.translatable("pgtelegraph.handheld.battery", Math.round(batteryPct)), cx + 4, y + 104, 0xA0A0A0);
        g.drawCenteredString(font, Component.translatable("pgtelegraph.handheld.help"), cx, y + 120, 0x808080);
        g.drawCenteredString(font, RadioBlockEntity.formatFreq(hz), cx, y - 12, 0x80FFFF);
        g.drawCenteredString(font, Component.translatable(paddle ? "pgtelegraph.handheld.paddle_help" : "pgtelegraph.handheld.straight_help", wpm),
                cx, y + 160, paddle ? 0xFFD060 : 0x808080);
    }
}
