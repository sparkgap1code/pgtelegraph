package com.sparkgap.pgtelegraph.compat;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.BeaconProgram;
import com.sparkgap.pgtelegraph.block.DecoderBlockEntity;
import com.sparkgap.pgtelegraph.block.ReceiverBlockEntity;
import com.sparkgap.pgtelegraph.block.TeleprinterBlockEntity;
import com.sparkgap.pgtelegraph.block.TransmitterBlockEntity;
import com.sparkgap.pgtelegraph.block.Tunable;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;


/**
 * CC: Tweaked peripherals. Only loaded when computercraft is present.
 * <pre>
 * telegraph_transmitter: send(text[, wpm]) stop() isBusy() isKeyed() getFrequency() setFrequency(hz)
 *                        getForward() getReflected()
 * telegraph_beacon:      + setMessage(text[, wpm[, mode[, gapSec]]]) getMessage()
 * telegraph_receiver:    getFrequency() setFrequency(hz) getSignal() getNoise() isOpen()
 * telegraph_decoder:     read() peek() clear()
 * telegraph_teleprinter: type(text) getPaper() clear()
 * telegraph_dtmf:        read() peek() getOutput() getMode() setMode(mode) getCodes() setCodes(on[, off])
 *                        (mode: "pulse" | "latch" | "digit")
 * telegraph_power:       getInput() getOutput() getCurrent() isLimiting() isOnBattery() getCharge()  (PSU and UPS)
 * telegraph_aprs:        getReports() -> {{name, x, y, z, age, kind, snr}, ...}  getKey()
 * telegraph_selector:    getChannel() setChannel(0-4)  (0 = off)
 * telegraph_tunable:     getFrequency() setFrequency(hz) isPowered()  (QSO robot, broadcast radio, TV, SSTV monitor)
 * </pre>
 */
public final class CcPeripherals {
    private CcPeripherals() {}

    public static void register(RegisterCapabilitiesEvent e) {
        var cap = PeripheralCapability.get();
        e.registerBlockEntity(cap, PgTelegraph.TRANSMITTER_BE.get(), (be, side) -> new Tx(be));
        e.registerBlockEntity(cap, PgTelegraph.SPARK_TRANSMITTER_BE.get(), (be, side) -> new Tx(be));
        e.registerBlockEntity(cap, PgTelegraph.REPEATER_BE.get(), (be, side) -> new Tx(be));
        e.registerBlockEntity(cap, PgTelegraph.BEACON_BE.get(), (be, side) -> new Beacon(be));
        e.registerBlockEntity(cap, PgTelegraph.RECEIVER_BE.get(), (be, side) -> new Rx(be));
        e.registerBlockEntity(cap, PgTelegraph.DECODER_BE.get(), (be, side) -> new Decoder(be));
        e.registerBlockEntity(cap, PgTelegraph.TELEPRINTER_BE.get(), (be, side) -> new Teleprinter(be));
        e.registerBlockEntity(cap, PgTelegraph.DTMF_DECODER_BE.get(), (be, side) -> new DtmfP(be));
        e.registerBlockEntity(cap, PgTelegraph.APRS_STATION_BE.get(), (be, side) -> new AprsP(be));
        e.registerBlockEntity(cap, PgTelegraph.SELECTOR_BE.get(), (be, side) -> new SelectorP(be));
        e.registerBlockEntity(cap, PgTelegraph.CREATIVE_TRANSMITTER_BE.get(), (be, side) -> new Tx(be));
        e.registerBlockEntity(cap, PgTelegraph.CREATIVE_RECEIVER_BE.get(), (be, side) -> new Rx(be));
        e.registerBlockEntity(cap, PgTelegraph.POWER_SUPPLY_BE.get(), (be, side) -> new Power(be));
        e.registerBlockEntity(cap, PgTelegraph.UPS_BE.get(), (be, side) -> new Power(be));
        e.registerBlockEntity(cap, PgTelegraph.QSO_ROBOT_BE.get(), (be, side) -> new Tune(be));
        e.registerBlockEntity(cap, PgTelegraph.BROADCAST_RADIO_BE.get(), (be, side) -> new Tune(be));
        e.registerBlockEntity(cap, PgTelegraph.TV_RECEIVER_BE.get(), (be, side) -> new Tune(be));
        e.registerBlockEntity(cap, PgTelegraph.SSTV_MONITOR_BE.get(), (be, side) -> new Tune(be));
    }

    static long hz(double v) throws LuaException {
        if (!Double.isFinite(v) || v < 10_000 || v > 30e9) throw new LuaException("frequency out of range (10 kHz - 30 GHz)");
        return Math.round(v);
    }

    public static class Tx implements IPeripheral {
        final TransmitterBlockEntity be;

        Tx(TransmitterBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_transmitter";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Tx t && t.be == be;
        }

        @LuaFunction(mainThread = true)
        public final void send(String text, java.util.Optional<Integer> wpm) {
            be.computerText.send(text, wpm.orElse(20));
        }

        @LuaFunction(mainThread = true)
        public final void stop() {
            be.computerText.stop();
        }

        @LuaFunction(mainThread = true)
        public final boolean isBusy() {
            return be.computerText.busy();
        }

        @LuaFunction(mainThread = true)
        public final boolean isKeyed() {
            return be.isKeyed();
        }

        @LuaFunction(mainThread = true)
        public final long getFrequency() {
            return be.freqHz();
        }

        @LuaFunction(mainThread = true)
        public final void setFrequency(double hz) throws LuaException {
            ((Tunable) be).setFreq(hz(hz));
        }

        @LuaFunction(mainThread = true)
        public final double getForward() {
            return be.forwardW();
        }

        @LuaFunction(mainThread = true)
        public final double getReflected() {
            return be.reflectedW();
        }
    }

    public static class Beacon extends Tx {
        Beacon(TransmitterBlockEntity be) {
            super(be);
        }

        @Override
        public String getType() {
            return "telegraph_beacon";
        }

        @LuaFunction(mainThread = true)
        public final void setMessage(String text, java.util.Optional<Integer> wpm, java.util.Optional<Integer> mode,
                                     java.util.Optional<Integer> gap) {
            BeaconProgram.Holder h = (BeaconProgram.Holder) be;
            BeaconProgram p = h.program();
            p.set(text, wpm.orElse(p.wpm), mode.orElse(p.mode), gap.orElse(p.gapSec));
            h.programChanged();
        }

        @LuaFunction(mainThread = true)
        public final String getMessage() {
            return ((BeaconProgram.Holder) be).program().message;
        }
    }

    public static class Rx implements IPeripheral {
        final ReceiverBlockEntity be;

        Rx(ReceiverBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_receiver";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Rx r && r.be == be;
        }

        @LuaFunction(mainThread = true)
        public final long getFrequency() {
            return be.freqHz();
        }

        @LuaFunction(mainThread = true)
        public final void setFrequency(double hz) throws LuaException {
            be.setFreq(hz(hz));
        }

        /** strongest signal in the filter, dBm (nil if none) */
        @LuaFunction(mainThread = true)
        public final Object getSignal() {
            return Double.isInfinite(be.lastSignalDbm) ? null : be.lastSignalDbm;
        }

        @LuaFunction(mainThread = true)
        public final double getNoise() {
            return be.lastNoiseDbm;
        }

        @LuaFunction(mainThread = true)
        public final boolean isOpen() {
            return be.squelchOpen();
        }
    }

    public static class Decoder implements IPeripheral {
        final DecoderBlockEntity be;

        Decoder(DecoderBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_decoder";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Decoder d && d.be == be;
        }

        @LuaFunction(mainThread = true)
        public final String read() {
            return be.takeUnread();
        }

        @LuaFunction(mainThread = true)
        public final String peek() {
            return be.text();
        }

        @LuaFunction(mainThread = true)
        public final void clear() {
            be.clear();
        }
    }

    public static class Teleprinter implements IPeripheral {
        final TeleprinterBlockEntity be;

        Teleprinter(TeleprinterBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_teleprinter";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Teleprinter t && t.be == be;
        }

        @LuaFunction(mainThread = true)
        public final void type(String text) {
            be.type(text);
        }

        @LuaFunction(mainThread = true)
        public final String getPaper() {
            return be.paper();
        }

        @LuaFunction(mainThread = true)
        public final void clear() {
            be.clearPaper();
        }

        @LuaFunction(mainThread = true)
        public final int queued() {
            return be.queued();
        }
    }

    private static final String[] DTMF_MODES = {"pulse", "latch", "digit"};

    public static class DtmfP implements IPeripheral {
        final com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity be;

        DtmfP(com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_dtmf";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof DtmfP d && d.be == be;
        }

        /** digits heard since the last read() */
        @LuaFunction(mainThread = true)
        public final String read() {
            return be.takeUnread();
        }

        /** the code being entered now (cleared after 5 s without a digit) */
        @LuaFunction(mainThread = true)
        public final String peek() {
            return be.buffer();
        }

        @LuaFunction(mainThread = true)
        public final int getOutput() {
            return be.output();
        }

        @LuaFunction(mainThread = true)
        public final String getMode() {
            return DTMF_MODES[be.mode()];
        }

        @LuaFunction(mainThread = true)
        public final void setMode(String mode) throws LuaException {
            int m = java.util.Arrays.asList(DTMF_MODES).indexOf(mode.toLowerCase(java.util.Locale.ROOT));
            if (m < 0) throw new LuaException("mode must be pulse, latch or digit");
            be.configure(be.onCode(), be.offCode(), m);
        }

        @LuaFunction(mainThread = true)
        public final Object[] getCodes() {
            return new Object[]{be.onCode(), be.offCode()};
        }

        @LuaFunction(mainThread = true)
        public final void setCodes(String on, java.util.Optional<String> off) {
            be.configure(on, off.orElse(""), be.mode());
        }
    }

    public static class Power implements IPeripheral {
        final com.sparkgap.pgtelegraph.block.PowerSupplyBlockEntity be;

        Power(com.sparkgap.pgtelegraph.block.PowerSupplyBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_power";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Power p && p.be == be;
        }

        @LuaFunction(mainThread = true)
        public final double getInput() {
            return be.inputVolts();
        }

        @LuaFunction(mainThread = true)
        public final double getOutput() {
            return be.outputVolts();
        }

        @LuaFunction(mainThread = true)
        public final double getCurrent() {
            return be.outputAmps();
        }

        @LuaFunction(mainThread = true)
        public final boolean isLimiting() {
            return be.isLimiting();
        }

        @LuaFunction(mainThread = true)
        public final boolean isOnBattery() {
            return be.onBattery();
        }

        /** 0-1, or nil for a supply without batteries */
        @LuaFunction(mainThread = true)
        public final Double getCharge() {
            return be.capacityWh() > 0 ? be.chargeFraction() : null;
        }
    }

    public static class Tune implements IPeripheral {
        final net.minecraft.world.level.block.entity.BlockEntity be;

        Tune(net.minecraft.world.level.block.entity.BlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_tunable";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Tune t && t.be == be;
        }

        @LuaFunction(mainThread = true)
        public final double getFrequency() {
            return ((Tunable) be).freqHz();
        }

        @LuaFunction(mainThread = true)
        public final void setFrequency(double v) throws LuaException {
            ((Tunable) be).setFreq(hz(v));
        }

        @LuaFunction(mainThread = true)
        public final boolean isPowered() {
            return be instanceof com.sparkgap.pgtelegraph.block.PoweredStationBlockEntity p && p.isPowered();
        }
    }

    public static class AprsP implements IPeripheral {
        final com.sparkgap.pgtelegraph.block.AprsStationBlockEntity be;

        AprsP(com.sparkgap.pgtelegraph.block.AprsStationBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_aprs";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof AprsP a && a.be == be;
        }

        @LuaFunction(mainThread = true)
        public final java.util.List<java.util.Map<String, Object>> getReports() {
            return be.reports();
        }

        @LuaFunction(mainThread = true)
        public final String getKey() {
            return be.aprsKey();
        }
    }

    public static class SelectorP implements IPeripheral {
        final com.sparkgap.pgtelegraph.block.SelectorBlockEntity be;

        SelectorP(com.sparkgap.pgtelegraph.block.SelectorBlockEntity be) {
            this.be = be;
        }

        @Override
        public String getType() {
            return "telegraph_selector";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof SelectorP p && p.be == be;
        }

        @LuaFunction(mainThread = true)
        public final int getChannel() {
            return be.channel();
        }

        @LuaFunction(mainThread = true)
        public final void setChannel(int ch) throws LuaException {
            if (ch < 0 || ch > com.sparkgap.pgtelegraph.block.SelectorBlockEntity.OUTS) throw new LuaException("channel 0-4");
            var lvl = be.getLevel();
            if (lvl != null) lvl.setBlock(be.getBlockPos(), be.getBlockState().setValue(com.sparkgap.pgtelegraph.block.SelectorBlock.CHANNEL, ch), 3);
        }
    }
}
