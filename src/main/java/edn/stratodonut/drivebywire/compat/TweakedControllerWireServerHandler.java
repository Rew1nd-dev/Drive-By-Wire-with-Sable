package edn.stratodonut.drivebywire.compat;

import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class TweakedControllerWireServerHandler {
    public static final String[] BUTTON_TO_CHANNEL = new String[] {
        "buttonA",
        "buttonB",
        "buttonX",
        "buttonY",
        "shoulderLeft",
        "shoulderRight",
        "buttonBack",
        "buttonStart",
        "buttonGuide",
        "leftJoyStickClick",
        "rightJoyStickClick",
        "dPadUp",
        "dPadRight",
        "dPadDown",
        "dPadLeft"
    };
    public static final String[] AXIS_TO_CHANNEL = new String[] {
        "axisLeftX+",
        "axisLeftX-",
        "axisLeftY+",
        "axisLeftY-",
        "axisRightX+",
        "axisRightX-",
        "axisRightY+",
        "axisRightY-",
        "axisTriggerLeft",
        "axisTriggerRight"
    };

    // ==============================================
    // 👇 【关键修改】强绑定：内部名 → 多语言键（一一对应，永不乱序）
    // ==============================================
    public static final Map<String, String> CHANNEL_TO_LANG_KEY = Map.ofEntries(
            Map.entry("world","drivebywire.wire.channel.world"),
            // 按钮映射
            Map.entry("buttonA", "drivebywire.controller.button.a"),
            Map.entry("buttonB", "drivebywire.controller.button.b"),
            Map.entry("buttonX", "drivebywire.controller.button.x"),
            Map.entry("buttonY", "drivebywire.controller.button.y"),
            Map.entry("shoulderLeft", "drivebywire.controller.button.shoulder_left"),
            Map.entry("shoulderRight", "drivebywire.controller.button.shoulder_right"),
            Map.entry("buttonBack", "drivebywire.controller.button.back"),
            Map.entry("buttonStart", "drivebywire.controller.button.start"),
            Map.entry("buttonGuide", "drivebywire.controller.button.guide"),
            Map.entry("leftJoyStickClick", "drivebywire.controller.button.left_joystick_click"),
            Map.entry("rightJoyStickClick", "drivebywire.controller.button.right_joystick_click"),
            Map.entry("dPadUp", "drivebywire.controller.button.dpad_up"),
            Map.entry("dPadRight", "drivebywire.controller.button.dpad_right"),
            Map.entry("dPadDown", "drivebywire.controller.button.dpad_down"),
            Map.entry("dPadLeft", "drivebywire.controller.button.dpad_left"),

            // 轴映射
            Map.entry("axisLeftX+", "drivebywire.controller.axis.left_x_positive"),
            Map.entry("axisLeftX-", "drivebywire.controller.axis.left_x_negative"),
            Map.entry("axisLeftY+", "drivebywire.controller.axis.left_y_positive"),
            Map.entry("axisLeftY-", "drivebywire.controller.axis.left_y_negative"),
            Map.entry("axisRightX+", "drivebywire.controller.axis.right_x_positive"),
            Map.entry("axisRightX-", "drivebywire.controller.axis.right_x_negative"),
            Map.entry("axisRightY+", "drivebywire.controller.axis.right_y_positive"),
            Map.entry("axisRightY-", "drivebywire.controller.axis.right_y_negative"),
            Map.entry("axisTriggerLeft", "drivebywire.controller.axis.left_trigger"),
            Map.entry("axisTriggerRight", "drivebywire.controller.axis.right_trigger"),

            // 键盘按键映射
            Map.entry("keyUp",    "drivebywire.controller.key.up"),
            Map.entry("keyDown",  "drivebywire.controller.key.down"),
            Map.entry("keyLeft",  "drivebywire.controller.key.left"),
            Map.entry("keyRight", "drivebywire.controller.key.right"),
            Map.entry("keyJump",  "drivebywire.controller.key.jump"),
            Map.entry("keyShift", "drivebywire.controller.key.shift")
    );
    private static final int TIMEOUT = 30;
    private static final WorldAttached<Map<Pair<BlockPos, Integer>, Integer>> BUTTON_TIMEOUTS = new WorldAttached<>(level -> new HashMap<>());
    private static final WorldAttached<Map<Pair<BlockPos, Integer>, Integer>> AXIS_TIMEOUTS = new WorldAttached<>(level -> new HashMap<>());

    private TweakedControllerWireServerHandler() {
    }

    public static void tick(final Level level) {
        tickTimeouts(level, BUTTON_TIMEOUTS.get(level), BUTTON_TO_CHANNEL);
        tickTimeouts(level, AXIS_TIMEOUTS.get(level), AXIS_TO_CHANNEL);
    }

    public static void receiveAxis(final Level level, final BlockPos pos, final List<Byte> axisStates) {
        final Map<Pair<BlockPos, Integer>, Integer> timeoutMap = AXIS_TIMEOUTS.get(level);
        for (int index = 0; index < axisStates.size() && index < AXIS_TO_CHANNEL.length; index++) {
            final int value = Byte.toUnsignedInt(axisStates.get(index));
            final Pair<BlockPos, Integer> key = Pair.of(pos.immutable(), index);
            ControllerSignalStore.setSignal(level, pos, AXIS_TO_CHANNEL[index], value);
            if (value > 0) {
                timeoutMap.put(key, TIMEOUT);
            } else {
                timeoutMap.remove(key);
            }
        }
    }

    public static void receiveButton(final Level level, final BlockPos pos, final List<Boolean> buttonStates) {
        final Map<Pair<BlockPos, Integer>, Integer> timeoutMap = BUTTON_TIMEOUTS.get(level);
        for (int index = 0; index < buttonStates.size() && index < BUTTON_TO_CHANNEL.length; index++) {
            final boolean pressed = Boolean.TRUE.equals(buttonStates.get(index));
            final Pair<BlockPos, Integer> key = Pair.of(pos.immutable(), index);
            ControllerSignalStore.setSignal(level, pos, BUTTON_TO_CHANNEL[index], pressed ? 15 : 0);
            if (pressed) {
                timeoutMap.put(key, TIMEOUT);
            } else {
                timeoutMap.remove(key);
            }
        }
    }

    public static void reset(final Level level, final BlockPos pos) {
        final Map<Pair<BlockPos, Integer>, Integer> axisTimeoutMap = AXIS_TIMEOUTS.get(level);
        final Map<Pair<BlockPos, Integer>, Integer> buttonTimeoutMap = BUTTON_TIMEOUTS.get(level);
        for (int index = 0; index < AXIS_TO_CHANNEL.length; index++) {
            ControllerSignalStore.setSignal(level, pos, AXIS_TO_CHANNEL[index], 0);
            axisTimeoutMap.remove(Pair.of(pos, index));
        }
        for (int index = 0; index < BUTTON_TO_CHANNEL.length; index++) {
            ControllerSignalStore.setSignal(level, pos, BUTTON_TO_CHANNEL[index], 0);
            buttonTimeoutMap.remove(Pair.of(pos, index));
        }
    }

    private static void tickTimeouts(
        final Level level,
        final Map<Pair<BlockPos, Integer>, Integer> timeoutMap,
        final String[] channels
    ) {
        final Iterator<Map.Entry<Pair<BlockPos, Integer>, Integer>> iterator = timeoutMap.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Pair<BlockPos, Integer>, Integer> entry = iterator.next();
            final int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                final Pair<BlockPos, Integer> key = entry.getKey();
                ControllerSignalStore.setSignal(level, key.getFirst(), channels[key.getSecond()], 0);
                iterator.remove();
                continue;
            }

            entry.setValue(ttl);
        }
    }
}
