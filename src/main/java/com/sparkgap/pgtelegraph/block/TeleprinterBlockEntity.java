package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.sparkgap.pgtelegraph.tape.Ita2;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Teleprinter (텔레타이프): ITA2 5-bit code, start-stop, {@value #BIT_TICKS} ticks per bit (10 baud,
 * about 1.25 characters per second). The line is idle OPEN (space) so it also works through a
 * radio keyed on-off: start bit = mark (closed), 5 data bits (1 = mark), 2 stop bits of space.
 * TX1/TX2: the sending contact (0.5 A). RX1/RX2: the selector magnet, 100 Ω, pulls in at 10 mA.
 */
public class TeleprinterBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final int BIT_TICKS = 2, CHAR_BITS = 8, MAX_PAPER = 3000;
    public static final float MAGNET_OHMS = 100f;
    public static final Spec SPEC = new Spec(85f, 3f, 1f, 130f,
            Spec.contact(0.05f, 0.5f, 1), Spec.coil(MAGNET_OHMS, 0.3f));

    private SwitchedWire contact, magnet;
    private final ArrayDeque<Integer> sendQueue = new ArrayDeque<>();
    private int sendBit = -1, sendCode, sendTick;
    // receiver
    private boolean wasMark;
    private int rxTick = -1, rxCode;
    private final Ita2.Printer printer = new Ita2.Printer();
    private String paper = "";

    public TeleprinterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        contact = builder.connectSwitch(0.05f, builder.terminalNode(0), builder.terminalNode(1), false);
        magnet = builder.connectSwitch(MAGNET_OHMS, builder.terminalNode(2), builder.terminalNode(3), !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (magnet != null) applyPower(magnet);
    }

    @Override
    protected void guards(Guard g) {
        g.check(contact, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
        g.check(magnet, SPEC.maxVolts(), SPEC.part("coil").maxAmps());
    }

    @Override
    protected void openAll() {
        if (contact != null && contact.getState()) contact.setState(false);
        if (magnet != null && magnet.getState()) magnet.setState(false);
    }

    public void type(String text) {
        if (text == null || text.isEmpty()) return;
        List<Integer> codes = Ita2.encode(text.length() > 200 ? text.substring(0, 200) : text);
        if (sendQueue.size() + codes.size() > 1000) return;
        sendQueue.addAll(codes);
    }

    public void clearPaper() {
        paper = "";
        setChanged();
        sendData();
    }

    public String paper() {
        return paper;
    }

    public int queued() {
        return sendQueue.size();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt()) return;
        // ---- sending
        boolean markOut = false;
        if (sendBit < 0 && !sendQueue.isEmpty()) {
            sendCode = sendQueue.poll();
            sendBit = 0;
            sendTick = 0;
        }
        if (sendBit >= 0) {
            if (sendBit == 0) markOut = true;                       // start
            else if (sendBit <= 5) markOut = ((sendCode >> (sendBit - 1)) & 1) != 0;
            if (++sendTick >= BIT_TICKS) {
                sendTick = 0;
                if (++sendBit >= CHAR_BITS) sendBit = -1;
            }
            if (sendBit == 1 && sendTick == 0) level.playSound(null, worldPosition, SoundEvents.WOODEN_BUTTON_CLICK_ON,
                    SoundSource.BLOCKS, 0.25f, 1.7f);
        }
        if (contact != null && contact.getState() != markOut) contact.setState(markOut);

        // ---- receiving: sample in the middle of each bit
        boolean markIn = magnet != null && Math.abs(magnet.current()) >= 0.01;
        if (rxTick < 0) {
            if (markIn && !wasMark) {
                rxTick = 0;
                rxCode = 0;
            }
        } else {
            rxTick++;
            int bit = rxTick / BIT_TICKS;           // 0 start, 1..5 data
            if (rxTick % BIT_TICKS == BIT_TICKS / 2 && bit >= 1 && bit <= 5 && markIn) rxCode |= 1 << (bit - 1);
            if (rxTick >= 6 * BIT_TICKS) {
                rxTick = -1;
                char c = printer.print(rxCode);
                if (c != 0) {
                    if (level instanceof net.minecraft.server.level.ServerLevel sl && Character.isLetterOrDigit(c))
                        com.sparkgap.pgtelegraph.Awards.near(sl, worldPosition, 16, "rtty");
                    paper += c;
                    if (paper.length() > MAX_PAPER) paper = paper.substring(paper.length() - MAX_PAPER);
                    level.playSound(null, worldPosition, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 1.4f);
                    setChanged();
                    sendData();
                }
            }
        }
        wasMark = markIn;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        String t = paper.replace('\n', ' ');
        if (t.length() > 60) t = "..." + t.substring(t.length() - 60);
        tip.add(Component.literal("     " + (t.isEmpty() ? "-" : t)).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Paper", paper);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        paper = tag.getString("Paper");
    }
}
