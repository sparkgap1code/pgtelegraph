package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.IntFunction;

/**
 * Value boxes whose range is too wide for Create's selection board (one column per value: 1000 W
 * or 550 voltage steps run off the screen). The board shows a short list of sensible steps instead
 * - 1, 2, 5, 10, 20, 50 ... - while the stored value is still the real number.
 */
public final class StepScroll {
    private StepScroll() {}

    /** sensible output powers, cut to the panel maximum */
    public static int[] powers(int max) {
        int[] all = {1, 2, 3, 5, 7, 10, 15, 20, 30, 50, 70, 100, 150, 200, 300, 500, 700, 1000, 1500, 2000};
        int[] s = java.util.Arrays.stream(all).filter(v -> v <= max).toArray();
        if (s.length == 0 || s[s.length - 1] != max) { // the panel maximum itself is always a choice
            s = java.util.Arrays.copyOf(s, s.length + 1);
            s[s.length - 1] = max;
        }
        return s;
    }

    static int index(int[] steps, int value) {
        int best = 0;
        for (int i = 1; i < steps.length; i++)
            if (Math.abs(steps[i] - value) < Math.abs(steps[best] - value)) best = i;
        return best;
    }

    static ValueSettingsBoard board(Component label, int[] steps, IntFunction<String> fmt) {
        return new ValueSettingsBoard(label, steps.length - 1, 1, List.of(label),
                new ValueSettingsFormatter(vs -> Component.literal(fmt.apply(steps[Math.max(0, Math.min(steps.length - 1, vs.value()))]))));
    }

    /** a plain scroll value with stepped selection */
    public static class Plain extends ScrollValueBehaviour {
        private final Component label;
        private int[] steps;
        private IntFunction<String> fmt = String::valueOf;

        public Plain(Component label, SmartBlockEntity be, ValueBoxTransform slot, int[] steps) {
            super(label, be, slot);
            this.label = label;
            this.steps = steps;
        }

        public Plain stepFormat(IntFunction<String> f) {
            this.fmt = f;
            return this;
        }

        public void setSteps(int[] steps) {
            this.steps = steps;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
            return board(label, steps, fmt);
        }

        @Override
        public void setValueSettings(Player player, ValueSettings vs, boolean ctrlDown) {
            int i = Math.max(0, Math.min(steps.length - 1, vs.value()));
            if (steps[i] != getValue()) playFeedbackSound(this);
            setValue(steps[i]);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, index(steps, getValue()));
        }
    }

    /** a {@link NamedScroll} (several on one block) with stepped selection */
    public static class Named extends NamedScroll {
        private final Component label;
        private final int[] steps;
        private IntFunction<String> fmt = String::valueOf;

        public Named(BehaviourType<?> type, String key, Component label, SmartBlockEntity be, ValueBoxTransform slot, int[] steps) {
            super(type, key, label, be, slot);
            this.label = label;
            this.steps = steps;
        }

        public Named stepFormat(IntFunction<String> f) {
            this.fmt = f;
            return this;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
            return board(label, steps, fmt);
        }

        @Override
        public void setValueSettings(Player player, ValueSettings vs, boolean ctrlDown) {
            int i = Math.max(0, Math.min(steps.length - 1, vs.value()));
            if (steps[i] != getValue()) playFeedbackSound(this);
            setValue(steps[i]);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, index(steps, getValue()));
        }
    }

    /**
     * A value picked like Create's speed board: columns 1-{@code max}, and rows that multiply them
     * (x1, x10, x100 ...). The stored value is the real number (column x the row's multiplier).
     */
    public static class Rows extends ScrollValueBehaviour {
        private final Component label;
        private final int max;
        private final int[] mults;
        private final String unit;

        public Rows(Component label, SmartBlockEntity be, ValueBoxTransform slot, int max, int[] mults, String unit) {
            super(label, be, slot);
            this.label = label;
            this.max = max;
            this.mults = mults;
            this.unit = unit;
            between(1, max * mults[mults.length - 1]);
            withFormatter(v -> fmt(v, unit));
        }

        public static String fmt(int v, String unit) {
            if (v >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.2f M%s", v / 1e6, unit);
            if (v >= 10_000) return String.format(java.util.Locale.ROOT, "%.1f k%s", v / 1e3, unit);
            return v + " " + unit;
        }

        private int row(int v) {
            for (int r = 0; r < mults.length; r++) if (v % mults[r] == 0 && v / mults[r] <= max) return r;
            return mults.length - 1;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
            List<Component> rows = new java.util.ArrayList<>();
            for (int m : mults) rows.add(Component.literal("x" + m));
            return new ValueSettingsBoard(label, max, Math.max(1, max / 10), rows,
                    new ValueSettingsFormatter(vs -> Component.literal(fmt(Math.max(1, vs.value()) * mults[Math.max(0, Math.min(mults.length - 1, vs.row()))], unit))));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings vs, boolean ctrlDown) {
            int r = Math.max(0, Math.min(mults.length - 1, vs.row()));
            int v = Math.max(1, Math.min(max, vs.value())) * mults[r];
            if (v != getValue()) playFeedbackSound(this);
            setValue(v);
        }

        @Override
        public ValueSettings getValueSettings() {
            int v = getValue(), r = row(v);
            return new ValueSettings(r, Math.max(1, v / mults[r]));
        }
    }
}
