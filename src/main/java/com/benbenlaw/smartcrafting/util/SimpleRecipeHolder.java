package com.benbenlaw.smartcrafting.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

public class SimpleRecipeHolder {
    private final ResourceLocation id;
    private final CraftingRecipe recipe;

    public SimpleRecipeHolder(ResourceLocation id, CraftingRecipe recipe) {
        this.id = id;
        this.recipe = recipe;
    }

    public CraftingRecipe value() {
        return recipe;
    }

    public ResourceLocation id() {
        return id;
    }

}