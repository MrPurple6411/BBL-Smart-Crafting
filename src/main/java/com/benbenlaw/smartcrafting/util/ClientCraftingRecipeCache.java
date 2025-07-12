package com.benbenlaw.smartcrafting.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClientCraftingRecipeCache {
    public static Map<ResourceLocation, CraftingRecipe> cachedRecipes = new HashMap<>();

    public static void setRecipes(Map<ResourceLocation, CraftingRecipe> recipes) {
        cachedRecipes = recipes;
    }

    public static CraftingRecipe getRecipe(ResourceLocation id) {
        return cachedRecipes.get(id);
    }

    public static SimpleRecipeHolder getRecipeHolder(ResourceLocation id) {
        CraftingRecipe recipe = cachedRecipes.get(id);
        if (recipe == null) return null;
        return new SimpleRecipeHolder(id, recipe);
    }

    public static List<SimpleRecipeHolder> getRecipeHolders() {
        return cachedRecipes.entrySet().stream()
                .map(entry -> new SimpleRecipeHolder(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

}
