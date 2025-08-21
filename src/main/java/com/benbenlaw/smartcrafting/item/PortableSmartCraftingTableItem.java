package com.benbenlaw.smartcrafting.item;

import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
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
import net.minecraft.world.level.Level;

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

}
