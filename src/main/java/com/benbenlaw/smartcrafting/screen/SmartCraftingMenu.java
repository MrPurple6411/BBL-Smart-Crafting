package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.networking.packets.SyncFavoriteRecipesClient;
import com.benbenlaw.smartcrafting.networking.packets.SyncSortTypeClient;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.benbenlaw.smartcrafting.screen.SmartCraftingScreen.FAVORITES_TAG;

public class SmartCraftingMenu extends AbstractContainerMenu {

    protected Level level;
    protected ContainerData data;
    protected Player player;
    protected BlockPos blockPos;
    private final NonNullList<ItemStack> lastInventorySnapshot;
    public String sortType;

    public SmartCraftingMenu(int containerID, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerID, inventory, extraData.readBlockPos(), new SimpleContainerData(2));
    }

    public SmartCraftingMenu(int containerID, Inventory inventory, BlockPos blockPos, ContainerData data) {
        super(SmartCraftingMenus.SMART_CRAFTING_MENU.get(), containerID);
        this.player = inventory.player;
        this.blockPos = blockPos;
        this.level = inventory.player.level();
        this.data = data;
        this.sortType = player.getPersistentData().getString("smart_crafting_sort_type");

        this.lastInventorySnapshot = NonNullList.withSize(player.getInventory().items.size(), ItemStack.EMPTY);
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            this.lastInventorySnapshot.set(i, player.getInventory().items.get(i).copy());
        }

        if (!level.isClientSide) {
            updateValidRecipes();
            PacketDistributor.sendToPlayer((ServerPlayer) inventory.player, new SyncSortTypeClient(player.getPersistentData().getString("smart_crafting_sort_type")));
            ListTag listTag = player.getPersistentData().getList(FAVORITES_TAG, Tag.TAG_STRING);
            List<String> favorites = listTag.stream().map(Tag::getAsString).toList();
            PacketDistributor.sendToPlayer((ServerPlayer) inventory.player, new SyncFavoriteRecipesClient(favorites));
        }

        checkContainerSize(inventory, 2);
        addPlayerInventory(inventory);
        addPlayerHotbar(inventory);
        addDataSlots(data);
    }

    public void updateValidRecipes() {
        if (level.isClientSide) return; // Safety check

        List<RecipeHolder<?>> recipes = getValidRecipes(); // Accept all recipe types

        List<ResourceLocation> recipeIds = recipes.stream()
                .map(RecipeHolder::id)
                .toList();
        sendRecipesToClient(recipeIds);
    }

    private void sendRecipesToClient(List<ResourceLocation> recipeIds) {
        SmartCraftingRecipePayload packet = new SmartCraftingRecipePayload(recipeIds);
        PacketDistributor.sendToPlayer((ServerPlayer) player, packet);
    }

    public List<RecipeHolder<?>> getValidRecipes() {
        if (level.isClientSide) return Collections.emptyList();

        long startTime = System.nanoTime(); // Start timing

        RecipeManager rm = level.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> craftingRecipes = rm.getAllRecipesFor(RecipeType.CRAFTING);
        List<RecipeHolder<StonecutterRecipe>> stonecutterRecipes = rm.getAllRecipesFor(RecipeType.STONECUTTING);

        Container inv = buildCombinedInventory();
        List<RecipeHolder<?>> allRecipes = new ArrayList<>();

        int craftingMatchCount = 0;
        for (RecipeHolder<CraftingRecipe> holder : craftingRecipes) {
            if (recipeHasMatchingIngredients(holder.value(), inv) && canCraftFromInventory(holder.value(), inv)) {
                allRecipes.add(holder);
                craftingMatchCount++;
            }
        }

        int stonecutterMatchCount = 0;
        if (isStonecutterNearby(player)) {
            for (RecipeHolder<StonecutterRecipe> holder : stonecutterRecipes) {
                if (recipeHasMatchingIngredients(holder.value(), inv) && canCraftStonecutterFromInventory(holder.value(), inv)) {
                    allRecipes.add(holder);
                    stonecutterMatchCount++;
                }
            }
        }

        long endTime = System.nanoTime(); // End timing
        double durationMs = (endTime - startTime) / 1_000_000.0;
//
        //player.sendSystemMessage(Component.literal("  Crafting Recipes: " + craftingMatchCount + " / " + craftingRecipes.size()));
        //player.sendSystemMessage(Component.literal("  Stonecutter Recipes: " + stonecutterMatchCount + " / " + stonecutterRecipes.size()));
        //player.sendSystemMessage(Component.literal(String.format("  Recipe filtering took %.3f ms", durationMs)));

        return allRecipes;
    }


    private List<IItemHandler> findConnectedItemHandlers() {
        List<IItemHandler> itemHandlers = new ArrayList<>();
        final int radius = 3;

        BlockPos.betweenClosedStream(
                blockPos.offset(-radius, -2, -radius),
                blockPos.offset(radius, 2, radius)
        ).forEach(pos -> {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                IItemHandler handler = Capabilities.ItemHandler.BLOCK
                        .getCapability(level, pos, level.getBlockState(pos), be, null);
                if (handler != null) {
                    itemHandlers.add(handler);
                }
            }
        });

        return itemHandlers;
    }

    private Container buildCombinedInventory() {
        List<ItemStack> combinedStacks = new ArrayList<>();

        for (IItemHandler handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    combinedStacks.add(stack.copy());
                }
            }
        }

        // Include player inventory
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                combinedStacks.add(stack.copy());
            }
        }

        SimpleContainer combined = new SimpleContainer(combinedStacks.size());
        for (int i = 0; i < combinedStacks.size(); i++) {
            combined.setItem(i, combinedStacks.get(i));
        }

        return combined;
    }


    private boolean consumeIngredientFromAll(Ingredient ingredient, int count) {
        int remaining = count;

        // First, try consuming from nearby item handlers
        for (IItemHandler handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    int toTake = Math.min(stack.getCount(), remaining);

                    // Try simulate then execute
                    ItemStack extracted = handler.extractItem(i, toTake, false);
                    if (!extracted.isEmpty()) {
                        remaining -= extracted.getCount();
                        if (remaining <= 0) return true;
                    }
                }
            }
        }

        // Then try consuming from the player's inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                int toTake = Math.min(stack.getCount(), remaining);
                stack.shrink(toTake);
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                remaining -= toTake;
                if (remaining <= 0) return true;
            }
        }

        return remaining <= 0;
    }


    private boolean isStonecutterNearby(Player player) {
        final int radius = 3;
        BlockPos playerPos = player.blockPosition();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) { // Vertical range - tweak as needed
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    if (level.getBlockState(checkPos).is(Blocks.STONECUTTER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canCraftFromInventory(CraftingRecipe recipe, Container inv) {
        CraftingInput input = buildCraftingInputForRecipe(recipe, inv);
        return recipe.matches(input, level);
    }

    private boolean canCraftStonecutterFromInventory(StonecutterRecipe recipe, Container inv) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            boolean found = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private CraftingInput buildCraftingInputForRecipe(CraftingRecipe recipe, Container inv) {
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

        Recipe<?> recipeHolder = optionalRecipe.get().value();

        if (recipeHolder instanceof CraftingRecipe craftingRecipe) {
            int maxCrafts = shiftClick ? getMaxCraftableAmount(craftingRecipe) : 1;

            for (int i = 0; i < maxCrafts; i++) {
                Container combinedInv = buildCombinedInventory();
                CraftingInput input = buildCraftingInputForRecipe(craftingRecipe, combinedInv);

                if (!craftingRecipe.matches(input, level)) break;

                ItemStack result = craftingRecipe.assemble(input, level.registryAccess());

                if (!player.getInventory().add(result.copy())) {
                    player.drop(result.copy(), false);
                }

                List<Ingredient> ingredients = craftingRecipe.getIngredients();

                Map<Ingredient, Integer> ingredientCounts = new HashMap<>();
                for (Ingredient ingredient : ingredients) {
                    if (!ingredient.isEmpty()) {
                        ingredientCounts.put(ingredient, ingredientCounts.getOrDefault(ingredient, 0) + 1);
                    }
                }

                for (Map.Entry<Ingredient, Integer> entry : ingredientCounts.entrySet()) {
                    Ingredient ingredient = entry.getKey();
                    int needed = entry.getValue();

                    if (!consumeIngredientFromAll(ingredient, needed)) {
                        // Could not consume enough ingredients from all sources, stop crafting
                        return;
                    }
                }

                NonNullList<ItemStack> remainders = craftingRecipe.getRemainingItems(input);
                for (ItemStack remainder : remainders) {
                    if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                        player.drop(remainder, false);
                    }
                }
            }
            player.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
            return;
        }

        // Stonecutter handling unchanged ...
        if (recipeHolder instanceof StonecutterRecipe stonecutterRecipe) {
            int maxCrafts = shiftClick ? getMaxCraftableAmountStonecutter(stonecutterRecipe) : 1;

            for (int i = 0; i < maxCrafts; i++) {
                Container combinedInv = buildCombinedInventory();

                if (!canCraftStonecutterFromInventory(stonecutterRecipe, combinedInv)) break;

                ItemStack result = stonecutterRecipe.assemble(null, level.registryAccess()); // input param can be null

                if (!player.getInventory().add(result.copy())) {
                    player.drop(result.copy(), false);
                }

                if (!consumeIngredientsStonecutter(stonecutterRecipe, combinedInv)) break;
            }

            player.playNotifySound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
        }
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


    private boolean recipeHasMatchingIngredients(Recipe<?> recipe, Container inv) {

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            for (Ingredient ingredient : craftingRecipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                boolean found = false;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false; // Missing ingredient
            }
            return true;
        }

        else if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
            for (Ingredient ingredient : stonecutterRecipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                boolean found = false;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        return false;
    }


    private int getMaxCraftableAmount(CraftingRecipe recipe) {
        Container inv = buildCombinedInventory();
        int max = Integer.MAX_VALUE;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            int count = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (ingredient.test(stack)) {
                    count += stack.getCount();
                }
            }

            max = Math.min(max, count);
        }

        return Math.max(0, Math.min(max, 64));
    }

    private boolean consumeIngredientsStonecutter(StonecutterRecipe recipe, Container container) {
        Ingredient ingredient = recipe.getIngredients().get(0); // Usually one ingredient

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private int getMaxCraftableAmountStonecutter(StonecutterRecipe recipe) {
        Container inv = buildCombinedInventory();
        int max = Integer.MAX_VALUE;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            int count = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
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