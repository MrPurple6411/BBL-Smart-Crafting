package com.benbenlaw.smartcrafting.event;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.item.SmartCraftingItems;
import com.benbenlaw.smartcrafting.networking.SendOpenSmartCraftingMenuToServer;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.util.KeyBinds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

@EventBusSubscriber(modid = SmartCrafting.MOD_ID, value = Dist.CLIENT)
public class OpenCraftingMenuEvent {

    @SubscribeEvent
    public static void onOpenSmartCraftingMenuButtonPressed(InputEvent.Key event) {

        Player player = Minecraft.getInstance().player;

        if (KeyBinds.OPEN_SMART_CRAFTING_MENU_HOTKEY.consumeClick()) {
            assert player != null;
            if (hasPortableSmartCraftingTable(player)) {
                PacketDistributor.sendToServer(new SendOpenSmartCraftingMenuToServer(player.blockPosition()));
            } else {
                player.sendSystemMessage(Component.translatable("message.smartcrafting.no_portable_table"));
            }
        }
    }

    private static boolean hasPortableSmartCraftingTable(Player player) {

        if (player.getInventory().items.stream().anyMatch(itemStack -> itemStack.getItem() == SmartCraftingItems.PORTABLE_SMART_CRAFTING_TABLE.get())) {
            return true;
        }

        if (ModList.get().isLoaded("curios")) {
            var curioApi = CuriosApi.getCuriosInventory(player);
            if (curioApi.isPresent()) {
                return curioApi.get().findFirstCurio(SmartCraftingItems.PORTABLE_SMART_CRAFTING_TABLE.get()).isPresent();
            }
        }

        return false;
    }
}
