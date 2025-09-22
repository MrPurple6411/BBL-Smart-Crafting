package com.benbenlaw.smartcrafting.networking;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.networking.packets.*;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class SmartCraftingMessages {

    public static void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SmartCrafting.MOD_ID);

        registrar.playToServer(SyncSortType.TYPE, SyncSortType.STREAM_CODEC, SyncSortType.HANDLER);
        registrar.playToServer(SyncFavoriteRecipes.TYPE, SyncFavoriteRecipes.STREAM_CODEC, SyncFavoriteRecipes.HANDLER);
        registrar.playToServer(SmartCraftingRecipeClickPayload.TYPE, SmartCraftingRecipeClickPayload.STREAM_CODEC, SmartCraftingRecipeClickPacket.get()::handle);
        registrar.playToServer(SendOpenSmartCraftingMenuToServer.TYPE, SendOpenSmartCraftingMenuToServer.STREAM_CODEC, SendOpenSmartCraftingMenuToServer.HANDLER);

        registrar.playToClient(SyncSortTypeClient.TYPE, SyncSortTypeClient.STREAM_CODEC, SyncSortTypeClient.HANDLER);
        registrar.playToClient(SyncFavoriteRecipesClient.TYPE, SyncFavoriteRecipesClient.STREAM_CODEC, SyncFavoriteRecipesClient.HANDLER);
        registrar.playToClient(SmartCraftingRecipePayload.TYPE, SmartCraftingRecipePayload.STREAM_CODEC, SmartCraftingRecipePacket.get()::handle);
    }
}

