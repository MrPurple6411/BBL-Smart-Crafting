package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import com.benbenlaw.smartcrafting.screen.SmartCraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

public record SmartCraftingRecipePacket() {

    public static final SmartCraftingRecipePacket INSTANCE = new SmartCraftingRecipePacket();

    public static SmartCraftingRecipePacket get() {
        return INSTANCE;
    }

    public void handle(final SmartCraftingRecipePayload payload, IPayloadContext context) {
        // Use current recipe manager
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        // Resolve off-thread (not GUI)
        List<RecipeHolder<CraftingRecipe>> resolvedRecipes = payload.recipeIds().stream()
                .map(level.getRecipeManager()::byKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(recipe -> recipe.value() instanceof CraftingRecipe)
                .map(recipe -> (RecipeHolder<CraftingRecipe>) recipe)
                .toList();

        // Then update the screen on main thread
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null &&
                    Minecraft.getInstance().player.containerMenu instanceof SmartCraftingMenu menu &&
                    Minecraft.getInstance().screen instanceof SmartCraftingScreen screen) {
                screen.setClientRecipes(resolvedRecipes);
            }
        });
    }

}
