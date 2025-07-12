package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import com.benbenlaw.smartcrafting.util.ClientCraftingRecipeCache;
import com.benbenlaw.smartcrafting.util.SimpleRecipeHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record SmartCraftingRecipePacket() {

    public static final SmartCraftingRecipePacket INSTANCE = new SmartCraftingRecipePacket();

    public static SmartCraftingRecipePacket get() {
        return INSTANCE;
    }

    public void handle(final SmartCraftingRecipePayload payload, IPayloadContext context) {
        Minecraft.getInstance().execute(() -> {
            assert Minecraft.getInstance().player != null;
            if (Minecraft.getInstance().player.containerMenu instanceof SmartCraftingMenu menu &&
                    Minecraft.getInstance().screen instanceof SmartCraftingScreen screen) {

                // Get the cache map
                Map<ResourceLocation, CraftingRecipe> rawCache = ClientCraftingRecipeCache.cachedRecipes;

                // Convert recipe IDs to SimpleRecipeHolders instead of raw CraftingRecipe
                List<SimpleRecipeHolder> resolvedRecipeHolders = payload.recipeIds().stream()
                        .map(id -> {
                            CraftingRecipe recipe = ClientCraftingRecipeCache.getRecipe(id);
                            if (recipe == null) return null;
                            return new SimpleRecipeHolder(id, recipe);
                        })
                        .filter(Objects::nonNull)
                        .toList();

                // Pass the wrapped recipes to the screen
                screen.setClientRecipes(resolvedRecipeHolders);
            }
        });
    }



}
