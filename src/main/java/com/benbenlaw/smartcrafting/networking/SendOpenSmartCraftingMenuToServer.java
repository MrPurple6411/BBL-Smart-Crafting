package com.benbenlaw.smartcrafting.networking;

import com.benbenlaw.smartcrafting.SmartCrafting;

import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record SendOpenSmartCraftingMenuToServer(BlockPos pos) implements CustomPacketPayload {

    public static final Type<SendOpenSmartCraftingMenuToServer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "sync_purchase_to_server"));

    public static final IPayloadHandler<SendOpenSmartCraftingMenuToServer> HANDLER = (packet, context) -> {

        Player player = context.player();
        ContainerData data = new SimpleContainerData(2);
        player.openMenu(new SimpleMenuProvider(
                (windowId, playerInventory, playerEntity) -> new SmartCraftingMenu(windowId, playerInventory, packet.pos, data),
                Component.translatable("block.smartcrafting.smart_crafting_table")), (buf -> buf.writeBlockPos(packet.pos)));
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SendOpenSmartCraftingMenuToServer> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SendOpenSmartCraftingMenuToServer::pos,
            SendOpenSmartCraftingMenuToServer::new
    );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
