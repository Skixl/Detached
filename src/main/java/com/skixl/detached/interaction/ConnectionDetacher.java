package com.skixl.detached.interaction;

import com.skixl.detached.registry.tag.DetachedTags;
import com.skixl.detached.util.Arms;
import com.skixl.detached.world.saveddata.DetachedConnections;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ConnectionDetacher {

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            BlockPos blockPos = hitResult.getBlockPos();
            BlockState blockState = level.getBlockState(blockPos);

            if (!isDetachable(blockState) || Arms.doesNotSupport(blockState) || !toolMatches(blockState, stack))
                return InteractionResult.PASS;

            // ------------------------------------- post toggle -------------------------------------
            if (Arms.hasUpProperty(blockState) && isPostTop(blockState, blockPos, hitResult)) {
                boolean target = !blockState.getValue(BlockStateProperties.UP);

                // Hiding the post is only allowed with an opposite arm pair
                if (!target && Arms.cannotHidePost(blockState)) return InteractionResult.FAIL;

                if (level instanceof ServerLevel serverLevel) {
                    DetachedConnections data = DetachedConnections.get(serverLevel);

                    data.clearUpOverride(blockPos);
                    BlockState natural = Block.updateFromNeighbourShapes(blockState, level, blockPos);
                    boolean naturalUp = natural.getValue(BlockStateProperties.UP);

                    // Only keep an override if the flipped value differs from the natural one
                    if (naturalUp != target) {
                        data.setUpOverride(blockPos, target);
                    }

                    level.setBlock(blockPos, Arms.setUpProperty(blockState, target), FLAGS);
                    level.playSound(null, blockPos, soundFor(blockState), SoundSource.BLOCKS, 1f, 1f);
                    stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }
                return InteractionResult.SUCCESS;
            }

            // ------------------------------------- arm toggle -------------------------------------
            Direction side = resolveSide(blockState, blockPos, hitResult, player);
            if (side == null) return InteractionResult.PASS;

            if (level instanceof ServerLevel serverLevel) {
                DetachedConnections data = DetachedConnections.get(serverLevel);
                BlockState newState;

                if (Arms.isConnected(blockState, side)) {
                    if (data.isForced(blockPos, side)) {
                        // Arm only exists because it was forced: undo the forcing
                        data.clearArm(blockPos, side);
                        newState = Block.updateFromNeighbourShapes(blockState, level, blockPos);
                    } else {
                        // Naturally connected: detach it
                        data.setDetached(blockPos, side);
                        newState = Arms.set(blockState, side, false);
                    }
                } else {
                    if (data.isDetached(blockPos, side)) {
                        // Restore normal behavior for this side
                        data.clearArm(blockPos, side);
                        newState = Block.updateFromNeighbourShapes(blockState, level, blockPos);
                        if (!Arms.isConnected(newState, side)) {
                            // Nothing to connect to, so force it
                            data.setForced(blockPos, side);
                            newState = Arms.set(newState, side, true);
                        }
                    } else {
                        // Empty side with no neighbor, so force-connect
                        data.setForced(blockPos, side);
                        newState = Arms.set(blockState, side, true);
                    }
                }

                if (Arms.hasUpProperty(newState)) {
                    // A saved "post hidden" override is only valid while the arms can cover the center
                    if (data.getUpOverride(blockPos) < 0 && Arms.cannotHidePost(newState)) {
                        data.clearUpOverride(blockPos);
                    }

                    int up = data.getUpOverride(blockPos);
                    boolean keep;

                    if (up != 0) {
                        keep = up > 0;
                    } else {
                        keep = blockState.getValue(BlockStateProperties.UP);
                    }

                    newState = Arms.setUpProperty(newState, keep);
                }

                level.setBlock(blockPos, newState, FLAGS);
                level.playSound(null, blockPos, soundFor(blockState), SoundSource.BLOCKS, 1f, 1f);
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
            return InteractionResult.SUCCESS;
        });
    }




    // ------------------------------------- helpers -------------------------------------

    public static boolean isDetachable(BlockState blockState) {
        return blockState.is(DetachedTags.Blocks.BY_AXE) || blockState.is(DetachedTags.Blocks.BY_PICKAXE);
    }

    private static boolean toolMatches(BlockState blockState, ItemStack stack) {
        return (blockState.is(DetachedTags.Blocks.BY_AXE) && stack.is(ItemTags.AXES))
                || (blockState.is(DetachedTags.Blocks.BY_PICKAXE) && stack.is(ItemTags.PICKAXES));
    }

    private static SoundEvent soundFor(BlockState state) {
        return state.is(DetachedTags.Blocks.GLASS_PANES) ? SoundEvents.GLASS_BREAK
                : state.is(Blocks.IRON_BARS) ? SoundEvents.IRON_BREAK
                : state.is(DetachedTags.Blocks.COPPER_BARS) ? SoundEvents.COPPER_BREAK
                : state.is(DetachedTags.Blocks.BY_PICKAXE) ? SoundEvents.STONE_BREAK
                : SoundEvents.AXE_STRIP.value();
    }

    private static Direction getClickedArm(BlockState blockState, BlockPos blockPos, Vec3 hitLocation) {
        double relativeX = hitLocation.x - (blockPos.getX() + 0.5);
        double relativeZ = hitLocation.z - (blockPos.getZ() + 0.5);

        // includes a tiny margin to account for floating-point precision
        double post = Arms.postHalfWidth(blockState) + 1e-4;

        if (Math.abs(relativeX) <= post && Math.abs(relativeZ) <= post) return null;

        Direction best = null;
        double bestScore = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!Arms.isConnected(blockState, direction)) continue;
            double score = relativeX * direction.getStepX() + relativeZ * direction.getStepZ();
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return best;
    }

    /* Determines which side of the block the player is interacting with
    * Will first try to get a specific arm, else falls back to the clicked face */
    private static Direction resolveSide(BlockState blockState, BlockPos blockPos, BlockHitResult hitResult, Player player) {
        Direction arm = getClickedArm(blockState, blockPos, hitResult.getLocation());
        if (arm != null) return arm;

        Direction face = hitResult.getDirection();
        if (face.getAxis().isHorizontal()) return face;

        double relativeX = hitResult.getLocation().x - (blockPos.getX() + 0.5);
        double relativeZ = hitResult.getLocation().z - (blockPos.getZ() + 0.5);

        // if the click was almost exactly in the center, use the direction the player is facing
        if (Math.abs(relativeX) < 0.01 && Math.abs(relativeZ) < 0.01) return player.getDirection();

        // otherwise, use whichever axis the click is farthest from the center on
        if (Math.abs(relativeX) > Math.abs(relativeZ)) {
            if (relativeX > 0) {
                return Direction.EAST;
            } else {
                return Direction.WEST;
            }
        } else {
            if (relativeZ > 0) {
                return Direction.SOUTH;
            } else {
                return Direction.NORTH;
            }
        }
    }

    private static boolean isPostTop(BlockState blockState, BlockPos blockPos, BlockHitResult hitResult) {
        if (hitResult.getDirection() != Direction.UP) return false;
        // includes a tiny margin to account for floating-point precision
        double post = Arms.postHalfWidth(blockState) + 1e-4;

        double relativeX = hitResult.getLocation().x - (blockPos.getX() + 0.5);
        double relativeZ = hitResult.getLocation().z - (blockPos.getZ() + 0.5);

        // Both coordinates must fall within the post's width for this to be a post-top click
        return Math.abs(relativeX) <= post && Math.abs(relativeZ) <= post;
    }
}