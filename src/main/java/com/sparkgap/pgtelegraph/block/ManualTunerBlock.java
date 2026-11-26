package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Manual antenna tuner (수동 안테나 튜너): a roller inductor and a variable capacitor on the lid. It
 * matches the antenna only around the frequency the two knobs resonate at, f = 1/(2π√(LC)) - wider
 * range (64:1) and less loss than an automatic tuner, but you retune it by hand when you change band.
 */
public class ManualTunerBlock extends AntennaTunerBlock implements IBE<ManualTunerBlockEntity> {
    public ManualTunerBlock(Properties properties) {
        super(properties, AntennaLink.Tuner.MANUAL);
    }

    @Override
    public Class<ManualTunerBlockEntity> getBlockEntityClass() {
        return ManualTunerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ManualTunerBlockEntity> getBlockEntityType() {
        return PgTelegraph.MANUAL_TUNER_BE.get();
    }
}
