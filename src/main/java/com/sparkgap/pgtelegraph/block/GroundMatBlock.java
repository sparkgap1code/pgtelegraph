package com.sparkgap.pgtelegraph.block;

import net.minecraft.world.level.block.Block;

/**
 * Ground mat (접지망): buried copper radial mesh. Every block within 24 blocks of a loading coil
 * lowers the ground loss resistance, which dominates the efficiency of a VLF/LF antenna.
 */
public class GroundMatBlock extends Block {
    public GroundMatBlock(Properties properties) {
        super(properties);
    }
}
