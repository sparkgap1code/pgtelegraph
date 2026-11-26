package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.item.LogbookItem;
import com.sparkgap.pgtelegraph.network.Batch2Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Logbook: newest first; click an entry to select it (delete / make QSL). */
public class LogbookScreen extends Screen {
    private static final int W = 320, ROWS = 12;
    private final InteractionHand hand;
    private final List<String> entries;
    private EditBox call, freq, rst, note;
    private int scroll, selected = -1;

    public LogbookScreen(InteractionHand hand, List<String> entries) {
        super(Component.translatable("item.pgtelegraph.logbook"));
        this.hand = hand;
        this.entries = new ArrayList<>(entries);
    }

    @Override
    protected void init() {
        int x0 = (width - W) / 2, y = height / 2 + 64;
        call = box(x0, y, 70, "pgtelegraph.logbook.call");
        freq = box(x0 + 74, y, 70, "pgtelegraph.logbook.freq");
        rst = box(x0 + 148, y, 34, "pgtelegraph.logbook.rst");
        note = box(x0 + 186, y, 134, "pgtelegraph.logbook.note");
        rst.setValue("599");
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.logbook.add"), b -> add()).bounds(x0, y + 24, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.logbook.delete"), b -> act(LogbookItem.DELETE)).bounds(x0 + 74, y + 24, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pgtelegraph.logbook.qsl"), b -> act(LogbookItem.QSL)).bounds(x0 + 148, y + 24, 90, 20).build());
    }

    private EditBox box(int x, int y, int w, String hint) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.translatable(hint));
        b.setHint(Component.translatable(hint));
        b.setMaxLength(40);
        return addRenderableWidget(b);
    }

    private void add() {
        if (call.getValue().isBlank()) return;
        String e = call.getValue().trim().toUpperCase() + " | " + freq.getValue().trim() + " | RST " + rst.getValue().trim()
                + (note.getValue().isBlank() ? "" : " | " + note.getValue().trim());
        PacketDistributor.sendToServer(new Batch2Payloads.Logbook(hand == InteractionHand.OFF_HAND, LogbookItem.ADD, 0, e));
        entries.add(0, "... | " + e);
        call.setValue("");
        note.setValue("");
        selected = -1;
    }

    private void act(int action) {
        if (selected < 0 || selected >= entries.size()) return;
        PacketDistributor.sendToServer(new Batch2Payloads.Logbook(hand == InteractionHand.OFF_HAND, action, selected, ""));
        if (action == LogbookItem.DELETE) {
            entries.remove(selected);
            selected = -1;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = (width - W) / 2, y0 = height / 2 - 100;
        if (mx >= x0 && mx < x0 + W && my >= y0 && my < y0 + ROWS * 12) {
            int i = scroll + (int) ((my - y0) / 12);
            selected = i < entries.size() ? i : -1;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(Math.max(0, entries.size() - ROWS), scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x0 = (width - W) / 2, y0 = height / 2 - 100;
        g.drawCenteredString(font, title, width / 2, y0 - 14, 0xFFFFFF);
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + ROWS * 12 + 2, 0xFFEDE3C8);
        for (int r = 0; r < ROWS && scroll + r < entries.size(); r++) {
            int i = scroll + r;
            if (i == selected) g.fill(x0, y0 + r * 12, x0 + W, y0 + r * 12 + 12, 0xFFB0C8F0);
            g.drawString(font, font.plainSubstrByWidth(entries.get(i), W - 4), x0 + 2, y0 + r * 12 + 2, 0x302010, false);
        }
        if (entries.isEmpty()) g.drawString(font, Component.translatable("pgtelegraph.logbook.empty"), x0 + 4, y0 + 4, 0x806040, false);
        // contest summary: QSOs (with a full exchange, no dupes), multipliers, dupes, score
        java.util.Set<String> seen = new java.util.HashSet<>(), mults = new java.util.HashSet<>();
        int dupes = 0, bad = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            String[] p = entries.get(i).split("\\|");
            if (p.length < 2) continue;
            String rest = entries.get(i).substring(entries.get(i).indexOf('|') + 1).trim();
            String call = rest.split("[\\s|]+")[0].toUpperCase(java.util.Locale.ROOT);
            if (call.isEmpty()) continue;
            if (!com.sparkgap.pgtelegraph.item.Contest.hasExchange(rest)) bad++;
            else if (!seen.add(call)) dupes++;
            else mults.add(com.sparkgap.pgtelegraph.item.Contest.prefix(call));
        }
        g.drawString(font, Component.translatable("pgtelegraph.logbook.score", seen.size(), mults.size(), dupes, bad,
                seen.size() * Math.max(1, mults.size())), x0, y0 + ROWS * 12 + 6, 0xFFE0C080, false);
    }
}
