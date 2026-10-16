package edn.stratodonut.drivebywire;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class WireConfig {

    public static final WireConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    public final ModConfigSpec.BooleanValue shouldConsumeWires;

    public final ModConfigSpec.IntValue maxSourcesPerStructure;
    public final ModConfigSpec.IntValue maxSourcesInWorld;
    public final ModConfigSpec.IntValue maxSinksPerSource;

    private WireConfig(ModConfigSpec.Builder builder) {
        shouldConsumeWires = builder.define("shouldConsumeWires", false);
        maxSourcesPerStructure = builder
            .comment("Maximum wire source blocks per Sable sublevel. 0 disables new sources.")
            .defineInRange("maxSourcesPerStructure", 64, 0, Integer.MAX_VALUE);
        maxSourcesInWorld = builder
            .comment("Maximum wire source blocks outside Sable sublevels, per dimension. 0 disables new world sources.")
            .defineInRange("maxSourcesInWorld", 4096, 0, Integer.MAX_VALUE);
        maxSinksPerSource = builder
            .comment("Maximum sink connections per source channel. 0 disables new connections.")
            .defineInRange("maxSinksPerSource", 64, 0, Integer.MAX_VALUE);
    }

    static {
        Pair<WireConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(WireConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

}
