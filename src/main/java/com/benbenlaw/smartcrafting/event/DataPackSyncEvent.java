package com.benbenlaw.smartcrafting.event;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.util.ClientCraftingRecipeCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = SmartCrafting.MOD_ID)
public class DataPackSyncEvent {

    @SubscribeEvent
    public static void onDataPackSync(OnDatapackSyncEvent event) {

        event.sendRecipes(RecipeType.CRAFTING);

    }

    @SubscribeEvent
    public static void onRecipeReceived(RecipesReceivedEvent event) {

        RecipeMap recipeMap = event.getRecipeMap();

        Collection<RecipeHolder<CraftingRecipe>> craftingRecipes = recipeMap.byType(RecipeType.CRAFTING);

        Map<ResourceLocation, CraftingRecipe> craftingRecipeMap = new HashMap<>();

        for (RecipeHolder<CraftingRecipe> recipeHolder : craftingRecipes) {
            // id() already returns ResourceLocation, no .location() needed
            craftingRecipeMap.put(recipeHolder.id().location(), recipeHolder.value());
        }

        ClientCraftingRecipeCache.setRecipes(craftingRecipeMap);
    }
}
