package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Create value-box placements for DeviceBlock-style blocks (geometry authored facing NORTH). */
public final class ValueBoxes {
    private ValueBoxes() {}

    /** Lying flat on a top surface at (x, y, z) pixels of the NORTH-authored model. */
    public static ValueBoxTransform top(double x, double y, double z, float scale) {
        return new ValueBoxTransform.Sided() {
            {
                this.scale = scale;
            }

            @Override
            public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
                return Geometry.rotatePoint(x, y + 0.05, z, state.getValue(DeviceBlock.FACING));
            }

            @Override
            protected Vec3 getSouthLocation() {
                return new Vec3(x / 16, y / 16, z / 16);
            }

            @Override
            protected boolean isSideActive(BlockState state, Direction direction) {
                return direction == Direction.UP;
            }
        };
    }

    /**
     * On whichever horizontal side of a small centred base the player looks at (base spans
     * 16 - faceZ .. faceZ, centre height y in pixels). Create only keeps a value box selected
     * while the crosshair ray hits the block within scale/2 of the box, so on thin blocks
     * (whips) the box has to sit on real geometry, not on the empty block edge.
     */
    public static ValueBoxTransform baseSide(double y, double faceZ, float scale) {
        return new ValueBoxTransform.Sided() {
            {
                this.scale = scale;
            }

            @Override
            protected Vec3 getSouthLocation() {
                return new Vec3(0.5, y / 16, (faceZ + 0.05) / 16);
            }

            @Override
            protected boolean isSideActive(BlockState state, Direction direction) {
                return direction.getAxis().isHorizontal();
            }
        };
    }

    /**
     * On the front face (the side the block faces). frontZ is the front face's z in the
     * NORTH-authored model (e.g. 3 when the body starts at z = 3); x, y are its centre.
     */
    public static ValueBoxTransform front(double x, double y, double frontZ, float scale) {
        return new ValueBoxTransform.Sided() {
            {
                this.scale = scale;
            }

            @Override
            protected Vec3 getSouthLocation() {
                // south frame = model rotated 180°: north face at z becomes south face at 16 - z
                return new Vec3((16 - x) / 16, y / 16, (16 - frontZ + 0.05) / 16);
            }

            @Override
            protected boolean isSideActive(BlockState state, Direction direction) {
                return direction == state.getValue(DeviceBlock.FACING);
            }
        };
    }
}
