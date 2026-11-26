package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import com.sparkgap.pgtelegraph.tape.Morse;
import com.sparkgap.pgtelegraph.tape.TapeData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.sparkgap.pgtelegraph.radio.VirtualAir;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic CW station (자동 교신 로봇): a 5 W transceiver with its own decoder and a simple
 * operator. It answers "CQ ... DE call" with a report, answers calls to its own callsign, signs off
 * after "TU" or "73", and calls CQ itself every 5 minutes when the band is quiet. Its callsign comes
 * from a license; its CQ text can be edited like a beacon's (sneak + empty hand).
 */
public class QsoRobotBlockEntity extends com.sparkgap.pgtelegraph.block.PoweredStationBlockEntity implements IHaveGoggleInformation, Tunable,
        BeaconProgram.Holder, RfMeterable {
    public static final double WATTS = 5;
    public static final int IDLE_TICKS = 40, CQ_TICKS = 6000;
    private static final Pattern DE = Pattern.compile("\\bDE ([A-Z0-9/]{3,12})");
    /** a callsign: letters and digits with a digit inside and a letter at the end */
    private static final Pattern CALL = Pattern.compile("\\b([A-Z0-9]{0,3}[0-9][A-Z0-9]{0,3}[A-Z](?:/[A-Z0-9]+)?)(\\?)?");
    private static final Pattern EXCH = Pattern.compile("\\b(5NN|599|5[1-9]9|[1-5][1-9][1-9])\\s+([0-9TNO]{1,4})\\b");
    /** operating modes: ordinary QSOs, contest run, a pileup calling you, a rare DX working split */
    public static final int NORMAL = 0, CONTEST = 1, PILEUP = 2, DX = 3, MODES = 4;
    public static final int SPLIT_HZ = 2000;
    private int opMode;
    private int serial = 1, qsos, lidTimer, lidRun, dxTimer;
    private final java.util.Set<String> worked = new java.util.HashSet<>();
    private String playerCall = "";
    /** pileup / DX callers: call, lat, lon, offset Hz, wpm */
    private final List<Object[]> callers = new ArrayList<>();
    private Object[] current;
    private final List<Integer> lidRuns = new ArrayList<>();
    private boolean lidMark;

    private final BeaconProgram program = new BeaconProgram();
    private final BeaconSequencer tx = new BeaconSequencer();
    private final Reception.TerrainCache terrain = new Reception.TerrainCache();
    private final List<Integer> runs = new ArrayList<>();
    private long freqHz = 7_030_000L;
    private AntennaLink link;
    private int rescan, run, quiet, replyDelay;
    private boolean mark;
    private String pending, partner = "";
    private double fwd, refl;
    // synced
    private String lastHeard = "", lastSent = "";

    public QsoRobotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        program.message = "CQ CQ DE {CALL} {CALL} K";
        program.call = "R0BOT";
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /** mains: 13.8 V, 150 W */
    public static final com.sparkgap.pgtelegraph.block.Spec SPEC = spec(13.8, 150);

    @Override
    public double nominalV() {
        return 13.8;
    }

    @Override
    public double ratedW() {
        return 150;
    }

    @Override
    public com.sparkgap.pgtelegraph.block.Spec spec() {
        return SPEC;
    }

    @Override
    public long freqHz() {
        return freqHz;
    }

    @Override
    public void setFreq(long hz) {
        freqHz = Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, hz));
        setChanged();
        sendData();
    }

    @Override
    public BeaconProgram program() {
        return program;
    }

    @Override
    public void programChanged() {
        setChanged();
        sendData();
    }

    @Override
    public double forwardW() {
        return fwd;
    }

    @Override
    public double reflectedW() {
        return refl;
    }

    public int opMode() {
        return opMode;
    }

    public void setOpMode(int m) {
        opMode = Math.floorMod(m, MODES);
        partner = "";
        callers.clear();
        current = null;
        if (level != null) VirtualAir.cancel(level, owner());
        quiet = 0;
        setChanged();
        sendData();
    }

    private String owner() {
        return "robot" + worldPosition.asLong();
    }

    /** where this robot listens: split in DX mode */
    private long listenHz() {
        return opMode == DX ? freqHz + SPLIT_HZ : freqHz;
    }

    private int cqTicks() {
        return switch (opMode) {
            case CONTEST -> 500;
            case DX -> 700;
            case PILEUP -> Integer.MAX_VALUE;
            default -> CQ_TICKS;
        };
    }

    private String call() {
        return program.call.isEmpty() ? "R0BOT" : program.call;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!powerTick()) {
            RadioWorld.silence(level, worldPosition);
            return;
        }
        if (--rescan <= 0) {
            rescan = 40;
            link = AntennaLink.scan(level, worldPosition);
        }
        if (link == null || link.amplifier() != null) {
            RadioWorld.silence(level, worldPosition);
            return;
        }
        // ---- sending
        if (tx.busy()) {
            if (tx.tick()) {
                refl = RfChain.deliver(level, worldPosition, link, freqHz, WATTS);
                fwd = WATTS;
            } else {
                RadioWorld.silence(level, worldPosition);
                fwd = refl = 0;
            }
            return;
        }
        RadioWorld.silence(level, worldPosition);
        fwd = refl = 0;
        if (pending != null && --replyDelay <= 0) {
            send(pending);
            pending = null;
            return;
        }
        // ---- listening
        var r = Reception.listen(level, link, listenHz(), 0, worldPosition, worldPosition.asLong(), terrain, -137,
                opMode == PILEUP || opMode == DX ? 200 : RadioMath.FILTER_HZ);
        if (opMode == DX) lids();
        boolean m = r.hasSignal() && r.best() - r.noiseDbm() >= 8;
        if (m != mark) {
            if (mark || !runs.isEmpty()) runs.add(Math.max(1, run));
            mark = m;
            run = 0;
        }
        run++;
        if (!mark && !runs.isEmpty() && run >= IDLE_TICKS) decode();
        if (runs.isEmpty() && !mark) {
            if (++quiet >= cqTicks()) {
                quiet = 0;
                partner = "";
                switch (opMode) {
                    case CONTEST -> send("CQ TEST " + call() + " " + call() + " TEST");
                    case DX -> {
                        send("CQ DX DE " + call() + " " + call() + " UP 2");
                        dxCallers();
                    }
                    default -> send(program.message.replace("{CALL}", call()));
                }
            }
            if (opMode == DX && !callers.isEmpty() && ++dxTimer % 160 == 0) dxCallers();
        } else quiet = 0;
    }

    private void send(String text) {
        lastSent = text;
        tx.send(text, 18);
        sendData();
    }

    private void decode() {
        List<String> codes = Morse.decodeCodes(new TapeData(TapeData.INKED, runs, ""), true);
        runs.clear();
        run = 0;
        String heard = Morse.toEnglish(codes).trim().toUpperCase(Locale.ROOT);
        if (heard.isEmpty()) return;
        lastHeard = heard.length() > 60 ? heard.substring(heard.length() - 60) : heard;
        sendData();
        switch (opMode) {
            case CONTEST -> contest(heard);
            case PILEUP -> pileup(heard);
            case DX -> dx(heard);
            default -> normal(heard);
        }
    }

    private void reply(String text) {
        pending = text;
        replyDelay = (opMode == NORMAL ? 30 : 8) + level.random.nextInt(opMode == NORMAL ? 40 : 12);
    }

    private void normal(String heard) {
        String me = call();
        Matcher dm = DE.matcher(heard);
        String other = null;
        while (dm.find()) other = dm.group(1);
        if (other == null || other.equals(me)) return;
        String reply;
        if (heard.contains(me) && (heard.contains("TU") || heard.contains("73"))) {
            reply = "TU 73 " + other + " DE " + me + " SK";
            partner = "";
        } else if (heard.contains(me)) {
            if (level instanceof net.minecraft.server.level.ServerLevel sl) com.sparkgap.pgtelegraph.Awards.near(sl, worldPosition, 64, "robot");
            reply = other + " DE " + me + " GM TNX CALL UR RST 5NN 5NN NAME ROBO QTH MC " + other + " DE " + me + " KN";
            partner = other;
        } else if (heard.contains("CQ") && partner.isEmpty()) {
            reply = other + " DE " + me + " " + me + " K";
        } else return;
        reply(reply);
    }

    /** callsigns in a message (not ours, not the usual words) */
    private List<String> calls(String heard) {
        List<String> out = new ArrayList<>();
        Matcher m = CALL.matcher(heard);
        while (m.find()) {
            String c = m.group(1);
            if (c.equals(call()) || c.equals("5NN") || c.matches("[0-9]+") || c.matches("5[0-9]9")) continue;
            out.add(c + (m.group(2) != null ? "?" : ""));
        }
        return out;
    }

    private static String serialText(int n) {
        return String.format(Locale.ROOT, "%03d", n);
    }

    /** running a contest: callers send their call, we send 5NN and a serial, they send theirs, TU */
    private void contest(String heard) {
        List<String> cs = calls(heard);
        if (!partner.isEmpty()) {
            if (EXCH.matcher(heard).find() || heard.contains("TU")) {
                worked.add(partner);
                serial++;
                qsos++;
                reply("TU " + call() + " TEST");
                partner = "";
                return;
            }
            if (heard.contains("AGN") || heard.contains("NR?") || heard.contains("?") || cs.contains(partner)) {
                reply(partner + " 5NN " + serialText(serial));
                return;
            }
        }
        if (cs.isEmpty()) return;
        String c = cs.get(cs.size() - 1).replace("?", "");
        if (worked.contains(c)) {
            reply(c + " QSO B4 TU " + call() + " TEST");
            return;
        }
        partner = c;
        reply(c + " 5NN " + serialText(serial));
    }

    private BlockPos callerAt(Object[] c) {
        if (freqHz >= RadioMath.LOS_MHZ * 1e6) // VHF/UHF: callers within line of sight
            return VirtualAir.at(level.random.nextDouble() * 360, 5 + level.random.nextDouble() * 40, false);
        return VirtualAir.at((Double) c[1], (Double) c[2]);
    }

    private void sendCaller(Object[] c, String text, long hzBase, int delay) {
        VirtualAir.send(level, callerKey(c), callerAt(c), hzBase + (Integer) c[3], text, (Integer) c[4],
                42 + level.random.nextDouble() * 16, level.random.nextInt(6) == 0, owner());
    }

    private BlockPos callerKey(Object[] c) {
        return new BlockPos(26_000_000 + Math.floorMod(c[0].hashCode(), 1_000_000), -3_000_000, 3);
    }

    private Object[] newCaller(int minOff, int maxOff) {
        Object[] a = VirtualAir.randomCall(level.random);
        int off = minOff + level.random.nextInt(maxOff - minOff + 1);
        if (level.random.nextBoolean()) off = -off;
        return new Object[]{a[0], a[1], a[2], off, 18 + level.random.nextInt(14)};
    }

    /** you run, a pileup calls you: pick one out by ear */
    private void pileup(String heard) {
        Matcher dm = DE.matcher(heard);
        while (dm.find()) playerCall = dm.group(1);
        boolean qrz = heard.contains("CQ") || heard.contains("QRZ");
        if (current != null && (heard.contains("TU") || heard.contains("73"))) { // QSO done: the rest call again
            qsos++;
            worked.add((String) current[0]);
            callers.remove(current);
            current = null;
            callAll(true);
            return;
        }
        if (qrz) {
            current = null;
            callAll(callers.isEmpty());
            return;
        }
        for (String c : calls(heard)) {
            boolean partial = c.endsWith("?");
            String frag = c.replace("?", "");
            Object[] full = null;
            List<Object[]> match = new ArrayList<>();
            for (Object[] k : callers) {
                if (k[0].equals(frag)) full = k;
                else if (((String) k[0]).contains(frag)) match.add(k);
            }
            if (full != null && !partial) {
                current = full;
                VirtualAir.cancel(level, owner());
                sendCaller(full, (playerCall.isEmpty() ? "" : playerCall + " ") + "TU 5NN 5NN", freqHz, 0);
                return;
            }
            if (full != null) match.add(full);
            if (!match.isEmpty()) {
                VirtualAir.cancel(level, owner());
                for (Object[] k : match) sendCaller(k, k[0] + " " + k[0], freqHz, 0);
                for (Object[] k : callers) // a lid or two never listens
                    if (!match.contains(k) && level.random.nextInt(5) == 0) sendCaller(k, (String) k[0], freqHz, 0);
                return;
            }
        }
    }

    private void callAll(boolean fresh) {
        if (fresh || callers.size() < 2) {
            int n = 3 + level.random.nextInt(4);
            for (int i = 0; i < n; i++) callers.add(newCaller(120, 500));
            while (callers.size() > 8) callers.remove(0);
        }
        for (Object[] k : callers)
            sendCaller(k, level.random.nextInt(3) == 0 ? k[0] + " " + k[0] : (String) k[0], freqHz, 0);
    }

    /** the DX's own pileup on the split frequency: QRM for you, and rivals it may pick instead */
    private void dxCallers() {
        if (callers.size() < 3) for (int i = 0; i < 4; i++) callers.add(newCaller(300, 1000));
        while (callers.size() > 6) callers.remove(0);
        for (Object[] k : callers) if (level.random.nextInt(3) != 0) sendCaller(k, (String) k[0], freqHz + SPLIT_HZ, 0);
    }

    /** rare DX working split: listens 2 kHz up, picks a caller, 5NN, TU UP */
    private void dx(String heard) {
        List<String> cs = calls(heard);
        if (!partner.isEmpty()) {
            if (partner.startsWith("*")) return; // working a rival
            if (heard.contains("TU") || heard.contains("5NN") || heard.contains("599") || heard.startsWith("R")) {
                worked.add(partner);
                qsos++;
                reply("TU " + call() + " UP");
                partner = "";
                return;
            }
            if (cs.contains(partner) || heard.contains("?") || heard.contains("AGN")) {
                reply(partner + " 5NN");
                return;
            }
        }
        if (cs.isEmpty()) return;
        String c = cs.get(cs.size() - 1);
        if (c.endsWith("?")) return;
        // a clean, strong call gets through; otherwise a rival from the pile is picked
        if (level.random.nextDouble() < 0.6 || callers.isEmpty()) {
            partner = c;
            reply(c + " 5NN");
        } else {
            Object[] k = callers.get(level.random.nextInt(callers.size()));
            partner = "*" + k[0];
            reply(k[0] + " 5NN");
            sendCaller(k, "TU 5NN", freqHz + SPLIT_HZ, 0);
            callers.remove(k);
            dxRivalDone = level.getGameTime() + 200;
        }
    }

    private long dxRivalDone = -1;

    /** DX mode: someone calling on the DX's own frequency gets "UP UP" */
    private void lids() {
        if (dxRivalDone > 0 && level.getGameTime() >= dxRivalDone && partner.startsWith("*")) {
            dxRivalDone = -1;
            partner = "";
            reply("TU " + call() + " UP");
        }
        if (tx.busy() || pending != null) return;
        var r = Reception.listen(level, link, freqHz, 0, worldPosition, worldPosition.asLong() + 1, terrain, -137, RadioMath.FILTER_HZ);
        boolean m = r.hasSignal() && r.best() - r.noiseDbm() >= 10;
        lidRun = m ? lidRun + 1 : Math.max(0, lidRun - 1);
        if (lidTimer > 0) lidTimer--;
        if (lidRun > 30 && lidTimer == 0) {
            lidRun = 0;
            lidTimer = 400;
            reply("UP UP");
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) RadioWorld.silence(level, worldPosition);
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)).append(" " + call()));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.robot.mode." + opMode)).withStyle(ChatFormatting.GOLD));
        if (opMode == DX) tip.add(Component.literal("     TX " + RadioBlockEntity.formatFreq(freqHz) + "  RX " + RadioBlockEntity.formatFreq(freqHz + SPLIT_HZ))
                .withStyle(ChatFormatting.AQUA));
        if (opMode != NORMAL) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.robot.qsos", qsos,
                opMode == CONTEST ? serialText(serial) : "-")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.freq")).append(": ")
                .append(Component.literal(RadioBlockEntity.formatFreq(freqHz)).withStyle(ChatFormatting.AQUA)));
        if (!lastHeard.isEmpty()) tip.add(Component.literal("     RX: " + lastHeard).withStyle(ChatFormatting.GRAY));
        if (!lastSent.isEmpty()) tip.add(Component.literal("     TX: " + lastSent).withStyle(ChatFormatting.GOLD));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("Freq", freqHz);
        tag.putInt("OpMode", opMode);
        tag.putInt("Serial", serial);
        tag.putInt("Qsos", qsos);
        program.save(tag);
        if (clientPacket) {
            tag.putString("Heard", lastHeard);
            tag.putString("Sent", lastSent);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        opMode = Math.floorMod(tag.getInt("OpMode"), MODES);
        serial = Math.max(1, tag.getInt("Serial"));
        qsos = tag.getInt("Qsos");
        program.load(tag);
        if (clientPacket) {
            lastHeard = tag.getString("Heard");
            lastSent = tag.getString("Sent");
        }
    }
}
