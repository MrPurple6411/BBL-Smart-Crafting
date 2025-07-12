package com.benbenlaw.smartcrafting;


import com.benbenlaw.core.network.CoreNetworking;
import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import com.benbenlaw.smartcrafting.block.SmartCraftingBlocks;
import com.benbenlaw.smartcrafting.networking.SmartCraftingMessages;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenus;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(SmartCrafting.MOD_ID)
public class SmartCrafting {
    public static final String MOD_ID = "smartcrafting";
    public static final Logger LOGGER = LogManager.getLogger();

    public SmartCrafting(final IEventBus eventBus, final ModContainer modContainer) {

        SmartCraftingItems.ITEMS.register(eventBus);
        SmartCraftingBlocks.BLOCKS.register(eventBus);
        SmartCraftingMenus.MENUS.register(eventBus);

        eventBus.addListener(this::commonSetup);

    }

    @EventBusSubscriber(modid = SmartCrafting.MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(SmartCraftingMenus.SMART_CRAFTING_MENU.get(), SmartCraftingScreen::new);
        }
    }

    public void commonSetup(RegisterPayloadHandlersEvent event) {
        SmartCraftingMessages.registerNetworking(event);

    }
}
