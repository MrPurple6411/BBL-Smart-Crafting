package com.benbenlaw.smartcrafting.item;

import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.util.KeyBinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.InputEvent;

import java.security.Key;
import java.util.List;

public class PortableSmartCraftingTableItem extends Item {
    public PortableSmartCraftingTableItem(Properties properties) {
        super(properties);
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {

            BlockPos pos = player.blockPosition();

            ContainerData data = new SimpleContainerData(2);

            player.openMenu(new SimpleMenuProvider(
                    (windowId, playerInventory, playerEntity) -> new SmartCraftingMenu(windowId, playerInventory, pos, data),
                    Component.translatable("block.smartcrafting.smart_crafting_table")), (buf -> buf.writeBlockPos(pos)));

        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flag) {
        if (Screen.hasShiftDown()) {
            KeyMapping button = KeyBinds.OPEN_SMART_CRAFTING_MENU_HOTKEY;
            list.add(Component.translatable("tooltip.smartcrafting.button", button.getKey().getDisplayName()).withStyle(ChatFormatting.BLUE));
        } else {
            list.add(Component.translatable("tooltip.smartcrafting.shift").withStyle(ChatFormatting.YELLOW));
        }
    }
}
