package com.benbenlaw.smartcrafting.config;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class SmartCraftingConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> storageRangeCheck;
    public static final ModConfigSpec.ConfigValue<Integer> stonecutterRangeCheck;

    static {
        BUILDER.push("Smart Crafting Config");

        storageRangeCheck = BUILDER
                .comment("The range in blocks to check for storage blocks when crafting. Higher valves will take more time, Default is 3.")
                .defineInRange("storageRangeCheck", 3, 1, 64);

        stonecutterRangeCheck = BUILDER
                .comment("The range in blocks to check for stonecutter blocks when crafting. Higher valves will take more time, Default is 3.")
                .defineInRange("stonecutterRangeCheck", 3, 1, 64);

        BUILDER.pop();
        SPEC = BUILDER.build();

    }
}
