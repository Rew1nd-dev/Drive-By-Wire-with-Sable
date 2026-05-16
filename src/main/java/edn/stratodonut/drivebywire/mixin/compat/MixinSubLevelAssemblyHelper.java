package edn.stratodonut.drivebywire.mixin.compat;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class MixinSubLevelAssemblyHelper {
    @Inject(
        method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V",
        at = @At("RETURN")
    )
    private static void drivebywire$remapWireNetworkAfterSableMoves(
        final ServerLevel originLevel,
        final SubLevelAssemblyHelper.AssemblyTransform transform,
        final Iterable<BlockPos> movedBlocks,
        final CallbackInfo ci
    ) {
        final ServerLevel resultingLevel = transform.getLevel();
        for (final BlockPos oldPos : movedBlocks) {
            WireNetworkManager.handleAssemblyMove(originLevel, resultingLevel, oldPos, transform.apply(oldPos));
        }
    }
}
