package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/** Helpers for geometry authored facing NORTH, in 1/16 block (pixel) units. */
public final class Geometry {
    private Geometry() {}

    private static int steps(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Rotates a NORTH-authored box {x1,y1,z1,x2,y2,z2} clockwise (seen from above) to the facing. */
    public static double[] rotate(double[] b, Direction facing) {
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        for (int i = 0; i < steps(facing); i++) {
            double nx1 = 16 - z1, nz1 = x1, nx2 = 16 - z2, nz2 = x2;
            x1 = nx1; z1 = nz1; x2 = nx2; z2 = nz2;
        }
        return new double[]{Math.min(x1, x2), b[1], Math.min(z1, z2), Math.max(x1, x2), b[4], Math.max(z1, z2)};
    }

    /** Rotates a NORTH-authored point (pixels) and returns it in block units (0..1). */
    public static Vec3 rotatePoint(double x, double y, double z, Direction facing) {
        for (int i = 0; i < steps(facing); i++) {
            double nx = 16 - z, nz = x;
            x = nx; z = nz;
        }
        return new Vec3(x / 16, y / 16, z / 16);
    }

    /** Inverse of {@link #rotatePoint}: a block-local point (pixels) back into the NORTH-authored frame. */
    public static double[] toModel(double x, double z, Direction facing) {
        for (int i = 0; i < (4 - steps(facing)) % 4; i++) {
            double nx = 16 - z, nz = x;
            x = nx; z = nz;
        }
        return new double[]{x, z};
    }

    public static int yRotation(Direction facing) {
        return steps(facing) * 90;
    }

    public static VoxelShape[] shapes(double[][] boxes) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double[] r = rotate(b, dir);
                shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
            }
            out[dir.get2DDataValue()] = shape.optimize();
        }
        return out;
    }

    public static TerminalBoundingBox[] terminals(double[][] boxes, Direction facing) {
        Component[] names = new Component[boxes.length];
        java.util.Arrays.fill(names, IDecoratedTerminal.CONNECTOR);
        return terminals(boxes, names, facing);
    }

    public static TerminalBoundingBox[] terminals(double[][] boxes, Component[] names, Direction facing) {
        TerminalBoundingBox[] out = new TerminalBoundingBox[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            double[] r = rotate(boxes[i], facing);
            out[i] = new TerminalBoundingBox(names[i], r[0], r[1], r[2], r[3], r[4], r[5]);
        }
        return out;
    }
}
