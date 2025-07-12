package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SmartCraftingMenu extends AbstractContainerMenu {

    protected Level level;
    protected ContainerData data;
    protected Player player;
    protected BlockPos blockPos;
    private final NonNullList<ItemStack> lastInventorySnapshot;

    public SmartCraftingMenu(int containerID, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerID, inventory, extraData.readBlockPos(), new SimpleContainerData(2));

    }

    public SmartCraftingMenu(int containerID, Inventory inventory, BlockPos blockPos, ContainerData data) {
        super(SmartCraftingMenus.SMART_CRAFTING_MENU.get(), containerID);
        this.player = inventory.player;
        this.blockPos = blockPos;
        this.level = inventory.player.level();
        this.data = data;

        this.lastInventorySnapshot = NonNullList.withSize(player.getInventory().items.size(), ItemStack.EMPTY);
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            this.lastInventorySnapshot.set(i, player.getInventory().items.get(i).copy());
        }

        if (!level.isClientSide) {
            updateValidRecipes();
        }

        checkContainerSize(inventory, 2);
        addPlayerInventory(inventory);
        addPlayerHotbar(inventory);

        addDataSlots(data);
    }

    public void updateValidRecipes() {
        if (level.isClientSide) return; // Safety check

        List<RecipeHolder<CraftingRecipe>> recipes = getValidRecipes();

        List<ResourceLocation> recipeIds = recipes.stream()
                .map(RecipeHolder::id)
                .toList();
        sendRecipesToClient(recipeIds);
    }

    private void sendRecipesToClient(List<ResourceLocation> recipeIds) {
        SmartCraftingRecipePayload packet = new SmartCraftingRecipePayload(recipeIds);
        PacketDistributor.sendToPlayer((ServerPlayer) player, packet);
    }

    public List<RecipeHolder<CraftingRecipe>> getValidRecipes() {
        if (level.isClientSide) return Collections.emptyList();

        RecipeManager rm = level.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> allRecipes = rm.getAllRecipesFor(RecipeType.CRAFTING);
        Inventory inv = player.getInventory();

        return allRecipes.stream()
                .filter(holder -> recipeHasMatchingIngredients(holder.value(), inv)) // <-- New pre-filter
                .filter(holder -> canCraftFromInventory(holder.value(), inv))
                .toList();
    }

    private boolean canCraftFromInventory(CraftingRecipe recipe, Inventory inv) {
        CraftingInput input = buildCraftingInputForRecipe(recipe, inv);
        return recipe.matches(input, level);
    }

    private CraftingInput buildCraftingInputForRecipe(CraftingRecipe recipe, Inventory inv) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        List<Ingredient> ingredients = recipe.getIngredients();
        int[] usedSlots = new int[inv.getContainerSize()];

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int recipeIndex = row * width + col;
                    int gridIndex = row * 3 + col;

                    if (recipeIndex >= ingredients.size()) continue;
                    Ingredient ing = ingredients.get(recipeIndex);
                    if (ing.isEmpty()) continue;

                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty() || usedSlots[i] >= stack.getCount()) continue;

                        if (ing.test(stack)) {
                            ItemStack copy = stack.copy();
                            copy.setCount(1);
                            grid.set(gridIndex, copy);
                            usedSlots[i]++;
                            break;
                        }
                    }
                }
            }
        } else {
            // Fallback for shapeless recipes: fill first N slots
            int placed = 0;
            for (Ingredient ing : ingredients) {
                if (ing.isEmpty()) continue;

                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (stack.isEmpty() || usedSlots[i] >= stack.getCount()) continue;

                    if (ing.test(stack)) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        grid.set(placed, copy);
                        usedSlots[i]++;
                        placed++;
                        break;
                    }
                }
            }
        }

        return CraftingInput.ofPositioned(3, 3, grid).input();
    }

    public void craftRecipeById(ResourceLocation recipeId, boolean shiftClick) {
        if (level.isClientSide) return;

        RecipeManager rm = level.getRecipeManager();
        Optional<RecipeHolder<?>> optionalRecipe = rm.byKey(recipeId);
        if (optionalRecipe.isEmpty()) return;

        Recipe<?> recipe = optionalRecipe.get().value();
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) return;

        int maxCrafts = shiftClick ? getMaxCraftableAmount(craftingRecipe) : 1;

        // Build input once
        CraftingInput input = buildCraftingInputForRecipe(craftingRecipe, player.getInventory());

        for (int i = 0; i < maxCrafts; i++) {
            if (!craftingRecipe.matches(input, level)) break;

            NonNullList<ItemStack> remainingItems = craftingRecipe.getRemainingItems(input);

            // Consume ingredients in bulk (improved approach below)
            if (!consumeIngredients(craftingRecipe, player.getInventory())) break;

            for (ItemStack remainder : remainingItems) {
                if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            }

            ItemStack result = craftingRecipe.assemble(input, level.registryAccess());
            player.getInventory().placeItemBackInInventory(result);
        }

        player.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        updateValidRecipes();
    }

    private boolean consumeIngredients(CraftingRecipe recipe, Inventory inventory) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int[] usedSlots = new int[inventory.getContainerSize()];

        // Verify all ingredients can be satisfied
        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack) && usedSlots[i] < stack.getCount()) {
                    found = true;
                    usedSlots[i]++;
                    break;
                }
            }
            if (!found) return false; // Can't satisfy this ingredient
        }

        // Consume items
        usedSlots = new int[inventory.getContainerSize()]; // Reset to do actual consumption
        for (Ingredient ingredient : ingredients) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack) && usedSlots[i] < stack.getCount()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    usedSlots[i]++;
                    break;
                }
            }
        }

        return true;
    }


    private boolean recipeHasMatchingIngredients(CraftingRecipe recipe, Inventory inv) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            boolean found = false;
            for (ItemStack stack : inv.items) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private int getMaxCraftableAmount(CraftingRecipe recipe) {
        Inventory inv = player.getInventory();
        int max = Integer.MAX_VALUE;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            int count = 0;
            for (ItemStack stack : inv.items) {
                if (ingredient.test(stack)) {
                    count += stack.getCount();
                }
            }

            max = Math.min(max, count);
        }

        return Math.max(0, Math.min(max, 64));
    }


    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (level.isClientSide) return;

        boolean changed = false;
        List<ItemStack> current = player.getInventory().items;

        for (int i = 0; i < current.size(); i++) {
            ItemStack oldStack = lastInventorySnapshot.get(i);
            ItemStack newStack = current.get(i);

            if (!ItemStack.matches(oldStack, newStack)) {
                changed = true;
                break;
            }
        }

        if (changed) {
            updateValidRecipes();

            // Update the snapshot
            for (int i = 0; i < current.size(); i++) {
                lastInventorySnapshot.set(i, current.get(i).copy());
            }
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }


    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
}
