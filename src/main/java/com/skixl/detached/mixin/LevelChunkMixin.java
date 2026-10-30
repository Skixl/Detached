package com.skixl.detached.mixin;

import com.skixl.detached.interaction.ConnectionDetacher;
import com.skixl.detached.world.saveddata.DetachedConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnresolvedMixinReference")
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void detached$clearOnReplace(BlockPos blockPos, BlockState newState, int flags,
                                         CallbackInfoReturnable<BlockState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockState old = self.getBlockState(blockPos); // still the old state at HEAD

        // swapping between detachable blocks (stripping, copper oxidizing) keeps the data.
        // replacing with anything else wipes it
        if (ConnectionDetacher.isDetachable(old)
                && !newState.is(old.getBlock())
                && !ConnectionDetacher.isDetachable(newState)) {
            DetachedConnections.get(serverLevel).clear(blockPos);
        }
    }
}