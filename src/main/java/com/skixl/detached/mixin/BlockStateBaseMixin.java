package com.skixl.detached.mixin;

import com.skixl.detached.interaction.ConnectionDetacher;
import com.skixl.detached.util.Arms;
import com.skixl.detached.world.saveddata.DetachedConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnresolvedMixinReference")
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void detached$keepOverrides(LevelReader level, ScheduledTickAccess ticks, BlockPos blockPos, Direction direction, BlockPos neighbourPos,
                                        BlockState neighbourState, RandomSource random, CallbackInfoReturnable<BlockState> cir) {

        BlockState result = cir.getReturnValue();
        if (!ConnectionDetacher.isDetachable(result) || Arms.doesNotSupport(result)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        DetachedConnections data = DetachedConnections.get(serverLevel);
        if (!data.hasOverrides(blockPos)) return;

        // applies detached/forced overrides on top of whatever the block computed is
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            switch (data.getArm(blockPos, dir)) {
                case ON -> result = Arms.set(result, dir, true);
                case OFF -> result = Arms.set(result, dir, false);
                default -> {}
            }
        }

        if (Arms.hasUpProperty(result)) {
            int up = data.getUpOverride(blockPos);

            // a "hidden" override is only valid while the arms can cover the center
            if (up < 0 && Arms.cannotHidePost(result)) {
                data.clearUpOverride(blockPos);
                up = 0;
            }

            if (up != 0) {
                result = Arms.setUpProperty(result, up > 0);
            } else {
                BlockState old = (BlockState) (Object) this;

                boolean keep;
                if (Arms.hasUpProperty(old)) {
                    keep = old.getValue(BlockStateProperties.UP);
                } else {
                    keep = result.getValue(BlockStateProperties.UP);
                }

                result = Arms.setUpProperty(result, keep);
            }
        }

        cir.setReturnValue(result);
    }
}