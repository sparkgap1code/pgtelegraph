package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.tape.Morse;
import com.sparkgap.pgtelegraph.tape.TapeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Shows a tape as it looks: an ink trace (inked) or punched holes (punched), wrapped into
 * strips, plus the text decoded with both the English and the Korean table. Wheel scrolls.
 */
public class TapeScreen extends Screen {
    private static final int STRIP_H = 12;
    private static final int STRIP_GAP = 4;
    private static final int PAPER = 0xFFF2EBD3;
    private static final int INK = 0xFF1C2440;
    private static final int HOLE = 0xFF2A2A2A;
    private static final int FEED = 0xFF9C9480;

    private final TapeData tape;
    private final String english;
    private final String korean;
    private int scroll;

    public TapeScreen(TapeData tape) {
        super(Component.translatable(tape.kind() == TapeData.PUNCHED
                ? "item.pgtelegraph.paper_tape.punched" : "item.pgtelegraph.paper_tape.inked"));
        this.tape = tape;
        List<String> codes = Morse.decodeCodes(tape);
        this.english = Morse.toEnglish(codes);
        this.korean = Morse.toKorean(codes);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** pixels per run unit: ticks are short, Morse units long */
    private int scale() {
        return tape.kind() == TapeData.PUNCHED ? 4 : 2;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = Math.min(420, width - 30);
        int x0 = (width - w) / 2;
        int y = 12;
        g.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        y += 14;
        if (!tape.label().isEmpty()) {
            g.drawCenteredString(font, "\"" + tape.label() + "\"", width / 2, y, 0xA0A0A0);
            y += 12;
        }
        y = text(g, "pgtelegraph.tape.english", english, x0, y, w);
        y = text(g, "pgtelegraph.tape.korean", korean, x0, y, w);
        y += 4;

        // strips
        int px = scale();
        int perRow = w / px;
        int rowsVisible = Math.max(1, (height - y - 16) / (STRIP_H + STRIP_GAP));
        int totalRows = (tape.total() + perRow - 1) / perRow;
        scroll = Math.min(scroll, Math.max(0, totalRows - rowsVisible));
        int firstUnit = scroll * perRow;
        int lastUnit = firstUnit + rowsVisible * perRow;

        for (int r = 0; r < rowsVisible && scroll + r < totalRows; r++) {
            int ry = y + r * (STRIP_H + STRIP_GAP);
            int len = Math.min(perRow, tape.total() - (scroll + r) * perRow);
            g.fill(x0, ry, x0 + len * px, ry + STRIP_H, PAPER);
            if (tape.kind() == TapeData.PUNCHED) {
                for (int u = 0; u < len; u++) {
                    g.fill(x0 + u * px + 1, ry + 5, x0 + u * px + 3, ry + 7, FEED);
                }
            }
        }

        int pos = 0;
        List<Integer> runs = tape.runs();
        for (int i = 0; i < runs.size(); i++) {
            int r = runs.get(i);
            int start = pos, end = pos + r;
            pos = end;
            if (i % 2 == 1 || end <= firstUnit || start >= lastUnit) continue;
            for (int u = Math.max(start, firstUnit); u < Math.min(end, lastUnit); ) {
                int row = u / perRow;
                int rowEnd = Math.min(end, (row + 1) * perRow);
                int ry = y + (row - scroll) * (STRIP_H + STRIP_GAP);
                int a = x0 + (u - row * perRow) * px;
                int b = x0 + (rowEnd - row * perRow) * px;
                if (tape.kind() == TapeData.PUNCHED) {
                    // dot holes in the upper track, dash holes in the lower one (Wheatstone tape)
                    boolean dash = r >= 2;
                    int hy = dash ? ry + 8 : ry + 1;
                    g.fill(a + 1, hy, b - 1, hy + 3, HOLE);
                } else {
                    g.fill(a, ry + 5, b, ry + 7, INK);
                }
                u = rowEnd;
            }
        }
        if (totalRows > rowsVisible) {
            g.drawCenteredString(font, (scroll + 1) + "-" + Math.min(totalRows, scroll + rowsVisible) + " / " + totalRows,
                    width / 2, height - 12, 0x808080);
        }
    }

    private int text(GuiGraphics g, String key, String value, int x, int y, int w) {
        List<FormattedCharSequence> lines = font.split(
                Component.translatable(key).append(": ").append(Component.literal(value)), w);
        for (int i = 0; i < Math.min(lines.size(), 4); i++) {
            g.drawString(font, lines.get(i), x, y, 0xE0E0E0);
            y += 10;
        }
        return y;
    }
}
