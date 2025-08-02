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
        registrar.playToServer(SyncFavouriteRecipes.TYPE, SyncFavouriteRecipes.STREAM_CODEC, SyncFavouriteRecipes.HANDLER);
        registrar.playToServer(SmartCraftingRecipeClickPayload.TYPE, SmartCraftingRecipeClickPayload.STREAM_CODEC, SmartCraftingRecipeClickPacket.get()::handle);

        registrar.playToClient(SyncSortTypeClient.TYPE, SyncSortTypeClient.STREAM_CODEC, SyncSortTypeClient.HANDLER);
        registrar.playToClient(SyncFavouriteRecipesClient.TYPE, SyncFavouriteRecipesClient.STREAM_CODEC, SyncFavouriteRecipesClient.HANDLER);
        registrar.playToClient(SmartCraftingRecipePayload.TYPE, SmartCraftingRecipePayload.STREAM_CODEC, SmartCraftingRecipePacket.get()::handle);
    }
}

