package edn.stratodonut.drivebywire;

import edn.stratodonut.drivebywire.compat.LinkedControllerWireServerHandler;
import edn.stratodonut.drivebywire.compat.TweakedControllerWireServerHandler;
import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class WireCommonEvents {
    private WireCommonEvents() {
    }

    public static void onLevelTick(final LevelTickEvent.Post event) {
        final Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        WireNetworkManager.get(level).flushPendingGraphRebuild(level);
        LinkedControllerWireServerHandler.tick(level);
        TweakedControllerWireServerHandler.tick(level);
    }

    public static void onNeighborNotify(final BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        final WireNetworkManager manager = WireNetworkManager.get(level);
        final BlockPos pos = event.getPos();
        if (manager.hasWorldSource(pos)) {
            final BlockState state = level.getBlockState(pos);
            if (state.isSignalSource()) {
                final int maxSignal = EnumSet.allOf(Direction.class)
                    .stream()
                    .map(direction -> state.getSignal(level, pos, direction))
                    .max(Comparator.naturalOrder())
                    .orElse(0);
                WireNetworkManager.trySetSignalAt(level, pos, WireNetworkManager.WORLD_CHANNEL, maxSignal);
            }
        }

        for (final Direction notifiedSide : event.getNotifiedSides()) {
            final BlockPos neighborPos = pos.relative(notifiedSide);
            if (!manager.hasWorldSource(neighborPos)) {
                continue;
            }
            if (!level.getBlockState(neighborPos).isSignalSource()) {
                WireNetworkManager.trySetSignalAt(
                    level,
                    neighborPos,
                    WireNetworkManager.WORLD_CHANNEL,
                    level.getBestNeighborSignal(neighborPos)
                );
            }
        }
    }
}
