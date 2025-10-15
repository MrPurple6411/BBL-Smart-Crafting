package com.benbenlaw.smartcrafting.config;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class SmartCraftingConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> storageRangeCheck;
    public static final ModConfigSpec.ConfigValue<Integer> stonecutterRangeCheck;
    public static final ModConfigSpec.ConfigValue<Boolean> debugMaxCrafts;
    public static final ModConfigSpec.ConfigValue<Boolean> lazyCounts;
    public static final ModConfigSpec.ConfigValue<Boolean> enableAdvancedByDefault;

    static {
        BUILDER.push("Smart Crafting Config");

        storageRangeCheck = BUILDER
                .comment("The range in blocks to check for storage blocks when crafting. Higher valves will take more time, Default is 3.")
                .defineInRange("storageRangeCheck", 3, 1, 64);

        stonecutterRangeCheck = BUILDER
                .comment("The range in blocks to check for stonecutter blocks when crafting. Higher valves will take more time, Default is 3.")
                .defineInRange("stonecutterRangeCheck", 3, 1, 64);

    debugMaxCrafts = BUILDER
        .comment("Enable verbose logging for max crafts calculation (server side). Default false.")
        .define("debugMaxCrafts", false);

    lazyCounts = BUILDER
        .comment("If true, server will not pre-compute max craft counts for every recipe; it sends -1 sentinel values and client locally estimates until on-demand counts are implemented. Greatly reduces menu open/craft lag in large inventories. Default true.")
        .define("lazyCounts", true);

    enableAdvancedByDefault = BUILDER
        .comment("If true, # searches include advanced tooltip lines without requiring #! prefix. Default false.")
        .define("enableAdvancedByDefault", false);

        BUILDER.pop();
        SPEC = BUILDER.build();

    }
}
