package com.benbenlaw.smartcrafting;

import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import com.benbenlaw.smartcrafting.block.SmartCraftingBlocks;
import com.benbenlaw.smartcrafting.networking.SmartCraftingMessages;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenus;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(SmartCrafting.MOD_ID)
public class SmartCrafting {
    public static final String MOD_ID = "smartcrafting";
    public static final Logger LOGGER = LogManager.getLogger();
    private BuildCreativeModeTabContentsEvent event;

    public SmartCrafting(final IEventBus eventBus, final ModContainer modContainer) {

        SmartCraftingItems.ITEMS.register(eventBus);
        SmartCraftingBlocks.BLOCKS.register(eventBus);
        SmartCraftingMenus.MENUS.register(eventBus);

        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::addCreativeTabContents);

    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(SmartCraftingMenus.SMART_CRAFTING_MENU.get(), SmartCraftingScreen::new);
        }


    }

    public void commonSetup(RegisterPayloadHandlersEvent event) {
        SmartCraftingMessages.registerNetworking(event);

    }

    public void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            ItemStack craftingTable = event.getParentEntries().stream()
                    .filter(stack -> stack.getItem() == Items.CRAFTING_TABLE)
                    .findFirst()
                    .orElse(null);

            if (craftingTable != null) {
                ItemStack smartCraftingTable = new ItemStack(SmartCraftingBlocks.SMART_CRAFTING_TABLE.get());
                event.insertAfter(craftingTable, smartCraftingTable, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } else {

                event.accept(new ItemStack(SmartCraftingBlocks.SMART_CRAFTING_TABLE.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
    }
}
