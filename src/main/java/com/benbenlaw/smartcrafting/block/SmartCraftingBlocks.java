package com.benbenlaw.smartcrafting.block;

import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;
import java.util.function.Supplier;

public class SmartCraftingBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SmartCrafting.MOD_ID);
    public static final ResourceKey<Block> SMART_CRAFTING_TABLE_KEY = ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "smart_crafting_table"));

    public static final DeferredBlock<Block> SMART_CRAFTING_TABLE = registerBlock("smart_crafting_table",
            () -> new SmartCraftingTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ACACIA_PLANKS)
                    .sound(SoundType.WOOD)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<BlockBehaviour.Properties, T> function) {
        DeferredBlock<T> toReturn = BLOCKS.registerBlock(name, function);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        SmartCraftingItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }


}