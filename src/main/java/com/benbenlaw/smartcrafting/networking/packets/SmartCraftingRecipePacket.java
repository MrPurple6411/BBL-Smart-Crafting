package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record SmartCraftingRecipePacket() {

    public static final SmartCraftingRecipePacket INSTANCE = new SmartCraftingRecipePacket();

    public static SmartCraftingRecipePacket get() {
        return INSTANCE;
    }


    // Resolve off-thread (not GUI)
    public void handle(final SmartCraftingRecipePayload payload, IPayloadContext context) {
        Level level = context.player().level();

        // Resolve off-thread (not GUI)
        List<? extends RecipeHolder<?>> resolvedRecipes = payload.recipeIds().stream()
                .map(level.getRecipeManager()::byKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(recipe -> {
                    // Keep only crafting or stonecutting recipes
                    return recipe.value() instanceof CraftingRecipe || recipe.value() instanceof StonecutterRecipe|| recipe.value() instanceof SmithingRecipe;
                })
                .toList();

    Map<ResourceLocation, Integer> cappedMap = new HashMap<>();
    Map<ResourceLocation, Integer> rawMap = new HashMap<>();
        for (int i = 0; i < payload.recipeIds().size(); i++) {
            ResourceLocation id = payload.recipeIds().get(i);
            int capped = (i < payload.cappedCrafts().size()) ? payload.cappedCrafts().get(i) : 0;
            int raw = (i < payload.rawCrafts().size()) ? payload.rawCrafts().get(i) : capped;
            cappedMap.put(id, capped);
            rawMap.put(id, raw);
        }

        // Update the screen on main thread
        Minecraft.getInstance().execute(() -> {
        if (Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.containerMenu instanceof SmartCraftingMenu &&
            Minecraft.getInstance().screen instanceof SmartCraftingScreen screen) {
                // Copy into mutable concrete list with erased wildcard
                List<RecipeHolder<?>> concrete = new ArrayList<>(resolvedRecipes.size());
                for (RecipeHolder<?> rh : resolvedRecipes) concrete.add(rh);
                screen.setClientRecipesWithMax(concrete, cappedMap, rawMap, payload.inventoryVersion());
            }
        });
    }

}
