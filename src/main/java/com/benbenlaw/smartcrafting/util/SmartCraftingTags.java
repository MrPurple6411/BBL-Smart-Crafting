package com.benbenlaw.smartcrafting.util;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class SmartCraftingTags {

    public static class Blocks {
        public static final TagKey<Block> BANNED_STORAGE = TagKey.create(
                BuiltInRegistries.BLOCK.key(), ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "banned_storage")
        );
    }
}
