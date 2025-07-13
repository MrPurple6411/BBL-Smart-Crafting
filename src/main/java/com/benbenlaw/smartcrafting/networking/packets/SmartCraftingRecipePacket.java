package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

public record SmartCraftingRecipePacket() {

    public static final SmartCraftingRecipePacket INSTANCE = new SmartCraftingRecipePacket();

    public static SmartCraftingRecipePacket get() {
        return INSTANCE;
    }


    // Resolve off-thread (not GUI)
    public void handle(final SmartCraftingRecipePayload payload, IPayloadContext context) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

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

        // Update the screen on main thread
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null &&
                    Minecraft.getInstance().player.containerMenu instanceof SmartCraftingMenu menu &&
                    Minecraft.getInstance().screen instanceof SmartCraftingScreen screen) {
                screen.setClientRecipes((List<RecipeHolder<?>>) resolvedRecipes);
            }
        });
    }

}
