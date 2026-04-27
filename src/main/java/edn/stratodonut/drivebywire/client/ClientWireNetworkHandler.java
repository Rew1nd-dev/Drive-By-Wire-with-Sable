package edn.stratodonut.drivebywire.client;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import edn.stratodonut.drivebywire.DriveByWireMod;
import edn.stratodonut.drivebywire.WireItems;
import edn.stratodonut.drivebywire.compat.TweakedControllerWireServerHandler;
import edn.stratodonut.drivebywire.network.WireAddConnectionPacket;
import edn.stratodonut.drivebywire.network.WireNetworkRequestSyncPacket;
import edn.stratodonut.drivebywire.network.WireRemoveConnectionPacket;
import edn.stratodonut.drivebywire.util.BlockFace;
import edn.stratodonut.drivebywire.util.FaceOutlines;
import edn.stratodonut.drivebywire.wire.MultiChannelWireSource;
import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import edn.stratodonut.drivebywire.wire.graph.WireNetworkNode.WireNetworkSink;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = DriveByWireMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWireNetworkHandler {
    private static final AABB UNIT_CUBE = AABB.unitCubeFromLowerCorner(Vec3.ZERO);
    private static final Map<Long, Map<String, Set<WireNetworkSink>>> EMPTY_NETWORK = Map.of();

    private static Map<Long, Map<String, Set<WireNetworkSink>>> currentNetwork = EMPTY_NETWORK;
    private static BlockPos selectedSource;
    private static String currentChannel = WireNetworkManager.WORLD_CHANNEL;
    private static int syncCooldown;
    private static String pendingSchematicSyncReason;

    private ClientWireNetworkHandler() {
    }

    @SubscribeEvent
    public static void onWorldUnload(final LevelEvent.Unload event) {
        clearSource();
    }

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isServer()) {
            return;
        }

        final Player player = event.getEntity();
        if (player == null || player.isSpectator()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        final ItemStack heldItem = event.getItemStack();
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final Direction face = event.getFace() == null ? Direction.UP : event.getFace();

        if (!heldItem.is(WireItems.WIRE.get())) {
            return;
        }

        handleWireUse(player, level, pos, face);
        event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseScrolled(final InputEvent.MouseScrollingEvent event) {
        final Player player = Minecraft.getInstance().player;
        if (player == null || selectedSource == null) {
            return;
        }

        if (!player.getMainHandItem().is(WireItems.WIRE.get())) {
            return;
        }

        final double delta = event.getScrollDeltaY();
        if (delta == 0) {
            return;
        }

        changeChannel(player.level().getBlockState(selectedSource).getBlock(), delta > 0);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        final Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        final Map<Long, Map<String, Set<WireNetworkSink>>> latestNetwork = WireNetworkManager.get(level).getNetwork();
        if (!latestNetwork.equals(currentNetwork)) {
            currentNetwork = latestNetwork;
            if (pendingSchematicSyncReason != null) {
                DriveByWireMod.LOGGER.info(
                    "[schematic-debug] Client wire mirror refreshed after {}: {} sources / {} connections.",
                    pendingSchematicSyncReason,
                    currentNetwork.size(),
                    countConnections(currentNetwork)
                );
                pendingSchematicSyncReason = null;
            }
        }

        final ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(WireItems.WIRE.get()) && !mainHand.is(WireItems.WIRE_CUTTER.get())) {
            clearSource();
            return;
        }

        if (--syncCooldown <= 0) {
            syncManager();
        }

        if (selectedSource != null) {
            drawOutline(level, selectedSource, LineColor.SOURCE.SELECTED.getColor());
        }
        drawOutlines(level, selectedSource, currentNetwork, currentChannel);
    }

    public static void clearSource() {
        currentNetwork = EMPTY_NETWORK;
        selectedSource = null;
        currentChannel = WireNetworkManager.WORLD_CHANNEL;
        syncCooldown = 0;
    }

    public static String getCurrentChannel() {
        return currentChannel;
    }

    public static void requestSchematicSync(final String reason) {
        pendingSchematicSyncReason = reason;
        DriveByWireMod.LOGGER.info(
            "[schematic-debug] Requesting wire mirror sync for {}. Current client mirror: {} sources / {} connections.",
            reason,
            currentNetwork.size(),
            countConnections(currentNetwork)
        );
        syncManager();
    }

    private static void handleWireUse(final Player player, final Level level, final BlockPos pos, final Direction face) {
        if (selectedSource == null) {
            selectedSource = pos.immutable();
            changeChannel(level.getBlockState(selectedSource).getBlock(), true);
            syncManager();
            return;
        }

        if (selectedSource.equals(pos)) {
            clearSource();
            return;
        }

        final Map<String, Set<WireNetworkSink>> currentSelection = currentNetwork.get(selectedSource.asLong());
        final WireNetworkSink sink = WireNetworkSink.of(pos, face);
        if (currentSelection != null && currentSelection.getOrDefault(currentChannel, Set.of()).contains(sink)) {
            PacketDistributor.sendToServer(new WireRemoveConnectionPacket(selectedSource, pos, face, currentChannel));
            return;
        }

        PacketDistributor.sendToServer(new WireAddConnectionPacket(selectedSource, pos, face, currentChannel));
    }

    private static void syncManager() {
        PacketDistributor.sendToServer(WireNetworkRequestSyncPacket.INSTANCE);
        syncCooldown = 20;
    }

    private static void changeChannel(final Block source, final boolean forward) {
        currentChannel = source instanceof MultiChannelWireSource channelSource
            ? channelSource.wire$nextChannel(currentChannel, forward)
            : WireNetworkManager.WORLD_CHANNEL;

        if (currentChannel == null) {
            currentChannel = WireNetworkManager.WORLD_CHANNEL;
        }

        final Player player = Minecraft.getInstance().player;
        if (player != null) {
                // 普通通道：强制从 CHANNEL_TO_LANG_KEY 映射表中查找
                // 找不到则兜底显示通道原始名称，避免客户端报错
                String langKey = TweakedControllerWireServerHandler.CHANNEL_TO_LANG_KEY
                        .getOrDefault(currentChannel,currentChannel);
                Component displayName = Component.translatable(langKey);
                player.displayClientMessage(
                        Component.translatable("drivebywire.wire.channel.selected", displayName),
                        true
                );
        }
    }

    private static void drawOutlines(
        final Level level,
        final BlockPos selectedSource,
        final Map<Long, Map<String, Set<WireNetworkSink>>> network,
        final String activeChannel
    ) {
        for (final Map.Entry<Long, Map<String, Set<WireNetworkSink>>> entry : network.entrySet()) {
            final BlockPos source = BlockPos.of(entry.getKey());
            final Map<String, Set<WireNetworkSink>> perChannel = entry.getValue();

            if (selectedSource != null && source.equals(selectedSource)) {
                for (final Map.Entry<String, Set<WireNetworkSink>> channelEntry : perChannel.entrySet()) {
                    final boolean active = channelEntry.getKey().equals(activeChannel);
                    for (final WireNetworkSink sink : channelEntry.getValue()) {
                        drawConnection(
                            level,
                            source,
                            BlockPos.of(sink.position()),
                            Direction.from3DDataValue(sink.direction()),
                            active ? LineColor.SINK.SELECTED.getColor() : LineColor.SINK.SAME_SOURCE_DIFFERENT_CHANNEL.getColor(),
                            active ? LineColor.WIRE.SELECTED.getColor() : LineColor.WIRE.SAME_SOURCE_DIFFERENT_CHANNEL.getColor()
                        );
                    }
                }
            } else {
                drawOutline(level, source, LineColor.SOURCE.SAME_NETWORK.getColor());
            }
        }
    }

    private static void drawConnection(
        final Level level,
        final BlockPos start,
        final BlockPos end,
        final Direction direction,
        final int faceColor,
        final int wireColor
    ) {
        drawOutlineFace(end, direction, faceColor);
        Outliner.getInstance()
            .showLine(
                net.createmod.catnip.data.Pair.of("wireConnection", net.createmod.catnip.data.Pair.of(end, direction)),
                Vec3.atCenterOf(start),
                Vec3.atCenterOf(end).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5D))
            )
            .colored(wireColor);
    }

    private static void drawOutlineFace(final BlockPos pos, final Direction direction, final int color) {
        Outliner.getInstance()
            .showAABB(net.createmod.catnip.data.Pair.of("wireFace", BlockFace.of(pos, direction)), FaceOutlines.getOutline(direction).move(pos))
            .colored(color)
            .lineWidth(0.0625F);
    }

    private static void drawOutline(final Level level, final BlockPos pos, final int color) {
        final BlockState state = level.getBlockState(pos);
        final AABB box = state.getShape(level, pos).isEmpty() ? UNIT_CUBE : state.getShape(level, pos).bounds();
        Outliner.getInstance()
            .showAABB(net.createmod.catnip.data.Pair.of("wireBlock", pos), box.move(pos))
            .colored(color)
            .lineWidth(0.0625F);
    }

    private static void notifyPlayer(final Player player, final String message) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
    }

    private static int countConnections(final Map<Long, Map<String, Set<WireNetworkSink>>> network) {
        int count = 0;
        for (final Map<String, Set<WireNetworkSink>> perChannel : network.values()) {
            for (final Set<WireNetworkSink> sinks : perChannel.values()) {
                count += sinks.size();
            }
        }
        return count;
    }

    private interface LineColor {
        int getColor();

        enum SINK implements LineColor {
            SELECTED(Mode.DEPOSIT.getColor()),
            SAME_SOURCE_DIFFERENT_CHANNEL(ChatFormatting.DARK_GRAY.getColor());

            private final int color;

            SINK(final int color) {
                this.color = color;
            }

            @Override
            public int getColor() {
                return color;
            }
        }

        enum SOURCE implements LineColor {
            SELECTED(Mode.TAKE.getColor()),
            SAME_NETWORK(0x5773d8);

            private final int color;

            SOURCE(final int color) {
                this.color = color;
            }

            @Override
            public int getColor() {
                return color;
            }
        }

        enum WIRE implements LineColor {
            SELECTED(Color.RED.getRGB()),
            SAME_SOURCE_DIFFERENT_CHANNEL(ChatFormatting.DARK_GRAY.getColor());

            private final int color;

            WIRE(final int color) {
                this.color = color;
            }

            @Override
            public int getColor() {
                return color;
            }
        }
    }
}
