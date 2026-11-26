package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.tape.Morse;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Morse decoder: a 100 Ω sensing coil (pulls in at 10 mA) times every mark and space and decodes
 * as it goes with the beam-search decoder of the cwdecoder project (speed and fist learnt from the
 * sender, several readings kept until the words make sense, 50 ms tick resolution modelled).
 * Words appear as they are settled; 2 s of silence settles the rest. English (with the ham
 * lexicon) or Korean (sneak + empty hand switches).
 * The text collects in a buffer (4000 chars); right-click with a book and quill to copy it into
 * the book. Computers can read it too.
 */
public class DecoderBlockEntity extends CoilDeviceBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final float COIL_OHMS = 100f;
    public static final Spec SPEC = new Spec(85f, 2f, 0.5f, 60f, Spec.coil(COIL_OHMS, 0.2f));
    public static final int IDLE_TICKS = 40, MAX_TEXT = 4000;

    private com.sparkgap.pgtelegraph.tape.cw.CwDecoder dec;
    private int run;
    private boolean mark, started;
    /** decoded symbols of the word in progress (Korean jamo are composed a word at a time) */
    private final List<com.sparkgap.pgtelegraph.tape.cw.CwDecoder.Sym> word = new ArrayList<>();
    private int drained;
    private boolean korean;
    private String text = "";
    /** text decoded since a computer last read it */
    private final StringBuilder unread = new StringBuilder();

    public DecoderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        coil = builder.connectSwitch(COIL_OHMS, builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
    }

    @Override
    protected double pickupAmps() {
        return 0.01;
    }

    private com.sparkgap.pgtelegraph.tape.cw.CwDecoder dec() {
        if (dec == null) dec = new com.sparkgap.pgtelegraph.tape.cw.CwDecoder(korean ? com.sparkgap.pgtelegraph.tape.cw.CwDecoder.Lexicon.KOREAN
                : com.sparkgap.pgtelegraph.tape.cw.CwDecoder.Lexicon.ENGLISH, 0.05);
        return dec;
    }

    @Override
    protected void onArmature(boolean energized) {
        if (energized == mark) return;
        double secs = Math.max(1, run) * 0.05;
        if (mark) dec().mark(secs);          // a mark just ended
        else if (started) dec().space(secs); // a gap just ended (the silence before the first mark is not one)
        if (energized) started = true;
        mark = energized;
        run = 0;
        drain();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        run++;
        if (!mark && started) {
            if (run % 2 == 0) {
                dec().offTick(run * 0.05);
                drain();
            }
            if (run >= IDLE_TICKS) {
                dec().finish();
                drain();
                flushWord();
                started = false;
            }
        }
    }

    /** take newly settled symbols; words go to the text as they complete */
    private void drain() {
        var c = dec().committed;
        while (drained < c.size()) {
            var s = c.get(drained++);
            if (s.text().equals(" ")) flushWord();
            else word.add(s);
        }
        if (c.size() > 4096) {
            c.clear();
            drained = 0;
        }
    }

    private void flushWord() {
        if (word.isEmpty()) return;
        String w;
        if (korean) {
            List<String> codes = new ArrayList<>();
            for (var s : word) codes.add(s.code());
            w = Morse.toKorean(codes);
        } else {
            StringBuilder sb = new StringBuilder();
            for (var s : word) sb.append(s.text());
            w = sb.toString();
        }
        word.clear();
        if (w.isBlank()) return;
        String add = (text.isEmpty() ? "" : " ") + w.trim();
        text = text + add;
        if (text.length() > MAX_TEXT) text = text.substring(text.length() - MAX_TEXT);
        unread.append(add);
        if (unread.length() > MAX_TEXT) unread.delete(0, unread.length() - MAX_TEXT);
        setChanged();
        sendData();
    }

    public String text() {
        return text;
    }

    public void clear() {
        text = "";
        unread.setLength(0);
        setChanged();
        sendData();
    }

    /** for computers: what came in since the last call */
    public String takeUnread() {
        String s = unread.toString();
        unread.setLength(0);
        return s;
    }

    public boolean korean() {
        return korean;
    }

    public void toggleLanguage() {
        korean = !korean;
        dec = null; // a different word list
        drained = 0;
        word.clear();
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName())
                .append(Component.literal(korean ? "  [KO]" : "  [EN]").withStyle(ChatFormatting.GRAY)));
        String t = text.length() > 60 ? "..." + text.substring(text.length() - 60) : text;
        tip.add(Component.literal("     " + (t.isEmpty() ? "-" : t)).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Text", text);
        tag.putBoolean("Korean", korean);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        text = tag.getString("Text");
        korean = tag.getBoolean("Korean");
    }
}
