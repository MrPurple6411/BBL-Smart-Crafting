package com.benbenlaw.smartcrafting.networking;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.networking.packets.*;
import com.benbenlaw.smartcrafting.networking.payload.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class SmartCraftingMessages {

    public static void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SmartCrafting.MOD_ID);

        registrar.playToServer(SyncSortType.TYPE, SyncSortType.STREAM_CODEC, SyncSortType.HANDLER);
        registrar.playToServer(SyncFavoriteRecipes.TYPE, SyncFavoriteRecipes.STREAM_CODEC, SyncFavoriteRecipes.HANDLER);
        registrar.playToServer(SmartCraftingRecipeClickPayload.TYPE, SmartCraftingRecipeClickPayload.STREAM_CODEC, SmartCraftingRecipeClickPacket.get()::handle);
        registrar.playToServer(SendOpenSmartCraftingMenuToServer.TYPE, SendOpenSmartCraftingMenuToServer.STREAM_CODEC, SendOpenSmartCraftingMenuToServer.HANDLER);
        registrar.playToServer(RequestRecipeCountsPayload.TYPE, RequestRecipeCountsPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player().containerMenu instanceof com.benbenlaw.smartcrafting.screen.SmartCraftingMenu menu) {
                menu.handleRecipeCountsRequest(payload.recipeIds(), payload.clientInventoryVersion(), ctx);
            }
        });

        registrar.playToClient(SyncSortTypeClient.TYPE, SyncSortTypeClient.STREAM_CODEC, SyncSortTypeClient.HANDLER);
        registrar.playToClient(SyncFavoriteRecipesClient.TYPE, SyncFavoriteRecipesClient.STREAM_CODEC, SyncFavoriteRecipesClient.HANDLER);
        registrar.playToClient(SmartCraftingRecipePayload.TYPE, SmartCraftingRecipePayload.STREAM_CODEC, SmartCraftingRecipePacket.get()::handle);
        registrar.playToClient(RecipeCountsPayload.TYPE, RecipeCountsPayload.STREAM_CODEC, (payload, ctx) -> {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (net.minecraft.client.Minecraft.getInstance().player != null &&
                        net.minecraft.client.Minecraft.getInstance().player.containerMenu instanceof com.benbenlaw.smartcrafting.screen.SmartCraftingMenu &&
                        net.minecraft.client.Minecraft.getInstance().screen instanceof com.benbenlaw.smartcrafting.screen.SmartCraftingScreen screen) {
                    screen.mergeCounts(payload.recipeIds(), payload.cappedCounts(), payload.rawCounts(), payload.inventoryVersion());
                }
            });
        });
    }
}

