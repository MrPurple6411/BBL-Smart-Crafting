package com.benbenlaw.smartcrafting.item;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SmartCraftingItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SmartCrafting.MOD_ID);

    public static final DeferredItem<Item> PORTABLE_SMART_CRAFTING_TABLE = ITEMS.register("portable_smart_crafting_table",
            () -> new PortableSmartCraftingTableItem(new Item.Properties()));



}
