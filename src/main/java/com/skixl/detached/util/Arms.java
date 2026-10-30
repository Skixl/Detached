package com.skixl.detached.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.NotNull;


// any reference to "post" is just the "up" block state
public final class Arms {
    private Arms() {}

    // ------------------------------------- type of block -------------------------------------
    public static boolean isWall(BlockState blockState) {
        return blockState.hasProperty(BlockStateProperties.NORTH_WALL)
                && blockState.hasProperty(BlockStateProperties.EAST_WALL)
                && blockState.hasProperty(BlockStateProperties.SOUTH_WALL)
                && blockState.hasProperty(BlockStateProperties.WEST_WALL);
    }

    public static boolean isFence(BlockState blockState) {
        return blockState.hasProperty(BlockStateProperties.NORTH)
                && blockState.hasProperty(BlockStateProperties.EAST)
                && blockState.hasProperty(BlockStateProperties.SOUTH)
                && blockState.hasProperty(BlockStateProperties.WEST);
    }

    public static boolean doesNotSupport(BlockState blockState) {
        return !isWall(blockState) && !isFence(blockState);
    }

    /** Half-width of the center post; ignores clicks on the post itself */
    public static double postHalfWidth(BlockState blockState) {
        if (isWall(blockState)) {
            return 0.25; // 8px post
        }

        if (blockState.getBlock() instanceof IronBarsBlock) {
            return 0.0625; // bars and panes: 2px
        }

        return 0.125; // fences: 4px
    }




    // ------------------------------------- arms -------------------------------------
    public static boolean isConnected(BlockState blockState, Direction direction) {
        if (isWall(blockState)) {
            return blockState.getValue(wallProperties(direction)) != WallSide.NONE;
        }

        return blockState.getValue(fenceProperties(direction));
    }

    /** Returns the same instance if nothing changes, so callers can compare by identity. */
    public static BlockState set(BlockState blockState, Direction direction, boolean connected) {
        if (isConnected(blockState, direction) == connected) return blockState;

        if (isWall(blockState)) {
            if (connected) {
                return blockState.setValue(wallProperties(direction), WallSide.LOW);
            } else {
                return blockState.setValue(wallProperties(direction), WallSide.NONE);
            }
        } else {
            return blockState.setValue(fenceProperties(direction), connected);
        }
    }




    // ------------------------------------- wall (post portion) -------------------------------------


    /** Only walls and posts have an `up` property. */
    public static boolean hasUpProperty(BlockState blockState) {
        return isWall(blockState) && blockState.hasProperty(BlockStateProperties.UP);
    }

    /** Post (up blockState) can only be hidden when two opposite arms are connected (like 2 parallel, or 3 and 4 arms) */
    public static boolean cannotHidePost(BlockState blockState) {
        boolean north = isConnected(blockState, Direction.NORTH);
        boolean east = isConnected(blockState, Direction.EAST);
        boolean south = isConnected(blockState, Direction.SOUTH);
        boolean west = isConnected(blockState, Direction.WEST);
        return (!north || !south) && (!east || !west);
    }

    /** Sets the post (up blockState), but should not hide it otherwise player will be able to see through the block */
    public static BlockState setUpProperty(BlockState blockState, boolean up) {
        if (!hasUpProperty(blockState)) return blockState;

        if (!up && cannotHidePost(blockState)) up = true;

        if (blockState.getValue(BlockStateProperties.UP) == up) {
            return blockState;
        }

        return blockState.setValue(BlockStateProperties.UP, up);
    }




    // ------------------------------------- helpers -------------------------------------
    private static EnumProperty<@NotNull WallSide> wallProperties(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH_WALL;
            case EAST -> BlockStateProperties.EAST_WALL;
            case SOUTH -> BlockStateProperties.SOUTH_WALL;
            case WEST -> BlockStateProperties.WEST_WALL;
            default -> throw new IllegalArgumentException("Horizontal only: " + direction);
        };
    }

    private static BooleanProperty fenceProperties(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            default -> throw new IllegalArgumentException("Horizontal only: " + direction);
        };
    }
}