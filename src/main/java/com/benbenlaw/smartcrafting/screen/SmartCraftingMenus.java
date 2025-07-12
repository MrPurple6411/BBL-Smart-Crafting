package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SmartCraftingMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, SmartCrafting.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SmartCraftingMenu>> SMART_CRAFTING_MENU;

    static {
        SMART_CRAFTING_MENU = MENUS.register("smart_crafting_menu", () ->
                IMenuTypeExtension.create(SmartCraftingMenu::new));
    }
}
