package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.config.SmartCraftingConfig;
import com.benbenlaw.smartcrafting.networking.packets.SyncFavoriteRecipesClient;
import com.benbenlaw.smartcrafting.networking.packets.SyncSortTypeClient;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.networking.payload.RecipeCountsPayload;
import com.benbenlaw.smartcrafting.util.SmartCraftingTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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
    public int handlers;
    private long inventoryVersion = 1L;
    private long lastHandlerHash = 0L;
    private long totalCountBatches = 0L;
    private long totalCountRecipes = 0L;
    private long totalCountNanos = 0L;
    private long maxBatchNanos = 0L;

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
        if (level.isClientSide) return;

        List<RecipeHolder<?>> recipes = getValidRecipes();

        List<ResourceLocation> recipeIds = recipes.stream()
                .map(RecipeHolder::id)
                .toList();
        sendRecipesToClient(recipeIds);
    }

    private void sendRecipesToClient(List<ResourceLocation> recipeIds) {
        boolean lazy = SmartCraftingConfig.lazyCounts.get();
        updateHandlerHashAndMaybeBumpVersion();
        List<Integer> cappedList = new ArrayList<>(recipeIds.size());
        List<Integer> rawList = new ArrayList<>(recipeIds.size());
        if (lazy) {
            // Send sentinel -1 values; client will estimate locally until on-demand requests implemented
            for (int i = 0; i < recipeIds.size(); i++) {
                cappedList.add(-1);
                rawList.add(-1);
            }
            if (SmartCraftingConfig.debugMaxCrafts.get()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[SmartCrafting][Debug] lazyCounts enabled: skipping bulk max craft computation for " + recipeIds.size() + " recipes."));
            }
        } else {
            Container inv = buildCombinedInventory();
            Map<ResourceLocation, Integer> rawCounts = new HashMap<>();
            Map<ResourceLocation, Integer> cappedCounts = new HashMap<>();
            final int CAP = 4096;
            for (ResourceLocation id : recipeIds) {
                level.getRecipeManager().byKey(id).ifPresent(holder -> {
                    Recipe<?> r = holder.value();
                    int raw = 0;
                    if (r instanceof CraftingRecipe cr) {
                        raw = estimateMaxCraftsCraftingRaw(cr, inv); // returns raw (uncapped)
                    } else if (r instanceof StonecutterRecipe sr) {
                        raw = estimateMaxCraftsStonecutterRaw(sr, inv);
                    }
                    int capped = Math.min(raw, CAP);
                    rawCounts.put(id, raw);
                    cappedCounts.put(id, capped);
                    if (SmartCraftingConfig.debugMaxCrafts.get()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[SmartCrafting][Debug] Recipe " + id + " rawCrafts=" + raw + " capped=" + capped));
                    }
                });
            }
            for (ResourceLocation id : recipeIds) {
                cappedList.add(cappedCounts.getOrDefault(id, 0));
                rawList.add(rawCounts.getOrDefault(id, 0));
            }
        }
        SmartCraftingRecipePayload packet = new SmartCraftingRecipePayload(recipeIds, cappedList, rawList, inventoryVersion);
        PacketDistributor.sendToPlayer((ServerPlayer) player, packet);
    }

    private void updateHandlerHashAndMaybeBumpVersion() {
        long hash = 1469598103934665603L; // FNV offset
        for (IItemHandler handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (s.isEmpty()) continue;
                hash ^= net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(s.getItem());
                hash *= 1099511628211L;
                hash ^= (s.getCount() & 0x3FFF);
                hash *= 1099511628211L;
            }
        }
        if (hash != lastHandlerHash) {
            lastHandlerHash = hash;
            inventoryVersion++;
        }
    }

    public long getInventoryVersion() { return inventoryVersion; }

    // Handle on-demand recipe count request (batch)
    public void handleRecipeCountsRequest(List<ResourceLocation> recipeIds, long clientVersion, IPayloadContext ctx) {
        if (level.isClientSide) return;
    // clientVersion currently not used beyond potential future stale suppression; server always returns authoritative version
        // Build combined inventory once
        Container inv = buildCombinedInventory();
        final int CAP = 4096;
        List<Integer> cappedList = new ArrayList<>(recipeIds.size());
        List<Integer> rawList = new ArrayList<>(recipeIds.size());
        long start = SmartCraftingConfig.debugMaxCrafts.get() ? System.nanoTime() : 0L;
        for (ResourceLocation id : recipeIds) {
            int tmpRaw = 0;
            var opt = level.getRecipeManager().byKey(id);
            if (opt.isPresent()) {
                Recipe<?> r = opt.get().value();
                if (r instanceof CraftingRecipe cr) tmpRaw = estimateMaxCraftsCraftingRaw(cr, inv);
                else if (r instanceof StonecutterRecipe sr) tmpRaw = estimateMaxCraftsStonecutterRaw(sr, inv);
            }
            rawList.add(tmpRaw);
            cappedList.add(Math.min(tmpRaw, CAP));
        }
        if (SmartCraftingConfig.debugMaxCrafts.get()) {
            long dur = System.nanoTime() - start;
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(String.format("[SmartCrafting][Debug] On-demand counts %d recipes in %.3f ms", recipeIds.size(), dur / 1_000_000.0)));
            totalCountBatches++;
            totalCountRecipes += recipeIds.size();
            totalCountNanos += dur;
            if (dur > maxBatchNanos) maxBatchNanos = dur;
            if (totalCountBatches % 10 == 0) {
                double avgMs = (totalCountNanos / (double) totalCountBatches) / 1_000_000.0;
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(String.format("[SmartCrafting][Debug] Batches=%d Avg=%.3f ms Max=%.3f ms RecipesTotal=%d Version=%d", totalCountBatches, avgMs, maxBatchNanos/1_000_000.0, totalCountRecipes, inventoryVersion)));
            }
        }
        long versionToSend = inventoryVersion; // always send current version; client will detect mismatch
        RecipeCountsPayload payload = new RecipeCountsPayload(recipeIds, cappedList, rawList, versionToSend);
        PacketDistributor.sendToPlayer((ServerPlayer) player, payload);
    }

    // Raw estimation helpers (return uncapped crafts)
    private int estimateMaxCraftsCraftingRaw(CraftingRecipe recipe, Container inv) {
        return estimateMaxCraftsCrafting(recipe, inv, false);
    }
    private int estimateMaxCraftsStonecutterRaw(StonecutterRecipe recipe, Container inv) {
        return estimateMaxCraftsStonecutter(recipe, inv, false);
    }

    private int estimateMaxCraftsCrafting(CraftingRecipe recipe, Container inv, boolean applyCap) {
        // Count required ingredient frequencies (treat same logical ingredient choices separately; acceptable approximation)
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return 0;
        // Build multiset of ingredient groups by signature of matching stacks to aggregate duplicates
        Map<String, Integer> required = new HashMap<>();
        Map<String, Ingredient> representative = new HashMap<>();
        for (Ingredient ing : ingredients) {
            if (ing == null || ing.isEmpty()) continue;
            String sig = ingredientSignatureServer(ing);
            required.put(sig, required.getOrDefault(sig, 0) + 1);
            representative.putIfAbsent(sig, ing);
        }
        int craftsPossible = Integer.MAX_VALUE;
        // Pre-fetch handlers once for live large-stack detection
        List<IItemHandler> liveHandlers = findConnectedItemHandlers();
        for (Map.Entry<String,Integer> e : required.entrySet()) {
            Ingredient ing = representative.get(e.getKey());
            int needPerCraft = e.getValue();
            int available = 0;
            boolean catalystLike = false; // set per matching stack; if any non-catalyst exists we count normally
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (ing.test(stack)) {
                    ItemStack rem = stack.getCraftingRemainingItem();
                    boolean isCatalyst = (!rem.isEmpty() && ItemStack.isSameItemSameComponents(rem, stack)) || stack.isDamageableItem();
                    if (isCatalyst) {
                        // treat catalyst as effectively infinite (bounded later)
                        catalystLike = true;
                    } else {
                        available += stack.getCount();
                    }
                }
            }
            // Live re-scan of handlers to detect virtual large counts that may have changed after snapshot build
            if (!catalystLike) {
                long handlerSum = 0;
                for (IItemHandler h : liveHandlers) {
                    for (int slot = 0; slot < h.getSlots(); slot++) {
                        ItemStack hs = h.getStackInSlot(slot);
                        if (!hs.isEmpty() && ing.test(hs)) {
                            handlerSum += hs.getCount();
                            if (handlerSum > Integer.MAX_VALUE) {
                                handlerSum = Integer.MAX_VALUE;
                                break;
                            }
                        }
                    }
                }
                if (handlerSum > available) {
                    if (SmartCraftingConfig.debugMaxCrafts.get()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[SmartCrafting][Debug] Adjusting ingredient " + e.getKey() + " available from snapshot " + available + " to live " + handlerSum));
                    }
                    // Replace available with live sum (will be capped later by craft cap)
                    available = (int)Math.min(handlerSum, Integer.MAX_VALUE);
                }
            }
            if (SmartCraftingConfig.debugMaxCrafts.get()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        String.format("[SmartCrafting][Debug] Ingredient sig=%s needPerCraft=%d available=%d catalystLike=%s", e.getKey(), needPerCraft, available, catalystLike)));
                // Detailed slot breakdown for this ingredient group
                int handlerIndex = 0;
                for (IItemHandler handler : findConnectedItemHandlers()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[SmartCrafting][Debug]  Handler ").append(handlerIndex).append(": ");
                    boolean any = false;
                    for (int h = 0; h < handler.getSlots(); h++) {
                        ItemStack st = handler.getStackInSlot(h);
                        if (!st.isEmpty() && ing.test(st)) {
                            sb.append("[slot ").append(h).append('=').append(st.getCount()).append("] ");
                            any = true;
                        }
                    }
                    if (any) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(sb.toString()));
                    }
                    handlerIndex++;
                }
            }
            if (available == 0 && !catalystLike) return 0; // no materials
            int craftsForThis = catalystLike ? 4096 : (available / needPerCraft);
            craftsPossible = Math.min(craftsPossible, craftsForThis);
            if (craftsPossible <= 0) return 0;
        }
        if (craftsPossible == Integer.MAX_VALUE) return 0;
        return applyCap ? Math.min(craftsPossible, 4096) : craftsPossible;
    }

    private int estimateMaxCraftsStonecutter(StonecutterRecipe recipe, Container inv, boolean applyCap) {
        // Stonecutter uses single ingredient type per craft
        Ingredient ing = recipe.getIngredients().isEmpty() ? null : recipe.getIngredients().getFirst();
        if (ing == null || ing.isEmpty()) return 0;
        int available = 0;
        boolean catalystLike = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (ing.test(stack)) {
                ItemStack rem = stack.getCraftingRemainingItem();
                boolean isCatalyst = (!rem.isEmpty() && ItemStack.isSameItemSameComponents(rem, stack)) || stack.isDamageableItem();
                if (isCatalyst) catalystLike = true; else available += stack.getCount();
            }
        }
        if (available == 0 && !catalystLike) return 0;
        int crafts = catalystLike ? 4096 : available; // need 1 per craft
        return applyCap ? Math.min(crafts, 4096) : crafts;
    }

    private String ingredientSignatureServer(Ingredient ing) {
        ItemStack[] stacks = ing.getItems();
        if (stacks.length == 0) return "empty";
        List<String> parts = new ArrayList<>();
        for (ItemStack s : stacks) if (s != null) {
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
            parts.add(key == null ? "unknown" : key.toString());
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    public List<RecipeHolder<?>> getValidRecipes() {
        if (level.isClientSide) return Collections.emptyList();


        RecipeManager rm = level.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> craftingRecipes = rm.getAllRecipesFor(RecipeType.CRAFTING);
        List<RecipeHolder<StonecutterRecipe>> stonecutterRecipes = rm.getAllRecipesFor(RecipeType.STONECUTTING);

        Container inv = buildCombinedInventory();
        List<RecipeHolder<?>> allRecipes = new ArrayList<>();

        for (RecipeHolder<CraftingRecipe> holder : craftingRecipes) {
            if (recipeHasMatchingIngredients(holder.value(), inv) && canCraftFromInventory(holder.value(), inv)) {
                allRecipes.add(holder);
            }
        }

        if (isStonecutterNearby(player)) {
            for (RecipeHolder<StonecutterRecipe> holder : stonecutterRecipes) {
                if (recipeHasMatchingIngredients(holder.value(), inv) && canCraftStonecutterFromInventory(holder.value(), inv)) {
                    allRecipes.add(holder);
                }
            }
        }

        /* recipe time logging
        long endTime = System.nanoTime(); // End timing
        double durationMs = (endTime - startTime) / 1_000_000.0;

        player.sendSystemMessage(Component.literal("  Crafting Recipes: " + craftingMatchCount + " / " + craftingRecipes.size()));
        player.sendSystemMessage(Component.literal("  Stonecutter Recipes: " + stonecutterMatchCount + " / " + stonecutterRecipes.size()));
        player.sendSystemMessage(Component.literal(String.format("  Recipe filtering took %.3f ms", durationMs)));
         */

        return allRecipes;
    }


    private List<IItemHandler> findConnectedItemHandlers() {
        List<IItemHandler> itemHandlers = new ArrayList<>();
        final int radius = SmartCraftingConfig.storageRangeCheck.get();

        BlockPos.betweenClosedStream(
                blockPos.offset(-radius, -radius / 2, -radius),
                blockPos.offset(radius, radius / 2, radius)
        ).forEach(pos -> {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && level.getBlockState(pos).is(SmartCraftingTags.Blocks.WHITELISTED_STORAGE)) {

                if (be instanceof ChestBlockEntity chest) {

                    ChestType type = chest.getBlockState().getValue(ChestBlock.TYPE);
                    if (type != ChestType.SINGLE) {
                        Direction dir = ChestBlock.getConnectedDirection(chest.getBlockState());
                        if (dir != null) {
                            BlockPos otherPos = pos.relative(dir);
                            if (otherPos.compareTo(pos) < 0) {
                                return;
                            }
                        }
                    }
                }

                IItemHandler handler = Capabilities.ItemHandler.BLOCK
                        .getCapability(level, pos, level.getBlockState(pos), be, null);
                if (handler != null) {
                    itemHandlers.add(handler);
                }
            }
        });

        handlers = itemHandlers.size();

        if (SmartCraftingConfig.debugMaxCrafts.get()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[SmartCrafting][Debug] Found " + handlers + " storage handlers in range."));
        }

        return itemHandlers;
    }


    private Container buildCombinedInventory() {
        List<ItemStack> combinedStacks = new ArrayList<>();

        //Player Inventory
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                combinedStacks.add(stack.copy());
            }
        }

        //IItem Handlers
        for (IItemHandler handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    combinedStacks.add(stack.copy());
                }
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

        // Player Inventory
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

        // IItem Handlers
        for (IItemHandler handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    int toTake = Math.min(stack.getCount(), remaining);
                    ItemStack extracted = handler.extractItem(i, toTake, false);
                    if (!extracted.isEmpty()) {
                        remaining -= extracted.getCount();
                        if (remaining <= 0) return true;
                    }
                }
            }
        }

        return remaining <= 0;
    }

    private boolean isStonecutterNearby(Player player) {
        final int radius = SmartCraftingConfig.stonecutterRangeCheck.get();
        BlockPos playerPos = player.blockPosition();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) { // Now full radius vertically
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

    /**
     * Detailed build returning both the CraftingInput and the exact 3x3 grid snapshot we created.
     * We need the grid later to correlate with remainder (container / catalyst) handling so that
     * we can mutate or return specific stacks instead of bulk-consuming purely by Ingredient counts.
     */
    private record BuiltInput(CraftingInput input, NonNullList<ItemStack> grid) {}

    private BuiltInput buildCraftingInputDetailed(CraftingRecipe recipe, Container inv) {
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

        CraftingInput input = CraftingInput.ofPositioned(3, 3, grid).input();
        return new BuiltInput(input, grid);
    }

    public void craftRecipeById(ResourceLocation recipeId, boolean shiftClick, boolean altClick) {
        if (level.isClientSide) return;

        RecipeManager rm = level.getRecipeManager();
        Optional<RecipeHolder<?>> optionalRecipe = rm.byKey(recipeId);
        if (optionalRecipe.isEmpty()) return;

        Recipe<?> recipeHolder = optionalRecipe.get().value();

        if (recipeHolder instanceof CraftingRecipe craftingRecipe) {
            int maxIterations;
            if (altClick) {
                // Limited craft: produce enough to reach one full stack (64) or fill partial stack in inventory.
                int targetAdditional = computeLimitedTargetAdditional(craftingRecipe);
                maxIterations = targetAdditional; // each iteration crafts once
            } else {
                maxIterations = shiftClick ? 4096 : 1; // hard safety cap for unlimited shift
            }
            for (int i = 0; i < maxIterations; i++) {
                Container combinedInv = buildCombinedInventory();
                BuiltInput built = buildCraftingInputDetailed(craftingRecipe, combinedInv);
                CraftingInput input = built.input();

                if (!craftingRecipe.matches(input, level)) break; // safety re-check

                ItemStack result = craftingRecipe.assemble(input, level.registryAccess());
                if (!player.getInventory().add(result.copy())) player.drop(result.copy(), false);

                // Remainder-aware per-slot handling (catalysts, container items, tools with durability)
                NonNullList<ItemStack> remainders = craftingRecipe.getRemainingItems(input);
                NonNullList<ItemStack> usedGrid = built.grid();

                // Iterate over each used slot (3x3 grid)
                for (int slot = 0; slot < usedGrid.size(); slot++) {
                    ItemStack used = usedGrid.get(slot);
                    if (used.isEmpty()) continue;
                    ItemStack remainder = remainders.size() > slot ? remainders.get(slot) : ItemStack.EMPTY;

                    // Locate and modify / consume one matching stack in player inventory first
                    int remainingToConsume = 1; // only ever 1 per grid cell
                    // Player inventory search
                    for (int invSlot = 0; invSlot < player.getInventory().getContainerSize() && remainingToConsume > 0; invSlot++) {
                        ItemStack realStack = player.getInventory().getItem(invSlot);
                        if (realStack.isEmpty()) continue;
                        if (ItemStack.isSameItemSameComponents(used, realStack)) {
                            if (remainder.isEmpty()) {
                                // Fully consumed
                                realStack.shrink(1);
                                if (realStack.isEmpty()) player.getInventory().setItem(invSlot, ItemStack.EMPTY);
                            } else if (ItemStack.isSameItem(remainder, used)) {
                                // Catalyst-like: same base item remains (e.g. durability tool). Apply new damage value (if any) without consuming.
                                realStack.setDamageValue(remainder.getDamageValue());
                            } else {
                                // Consumed -> give different remainder (e.g. milk bucket -> empty bucket)
                                realStack.shrink(1);
                                if (realStack.isEmpty()) player.getInventory().setItem(invSlot, ItemStack.EMPTY);
                                if (!remainder.isEmpty() && !player.getInventory().add(remainder.copy())) {
                                    player.drop(remainder.copy(), false);
                                }
                            }
                            remainingToConsume = 0; // handled
                        }
                    }

                    // If not found in player inventory, search connected handlers and consume there
                    if (remainingToConsume > 0) {
                        outer:
                        for (IItemHandler handler : findConnectedItemHandlers()) {
                            for (int hSlot = 0; hSlot < handler.getSlots(); hSlot++) {
                                ItemStack stackInHandler = handler.getStackInSlot(hSlot);
                                if (stackInHandler.isEmpty()) continue;
                                if (ItemStack.isSameItemSameComponents(used, stackInHandler)) {
                                    if (remainder.isEmpty()) {
                                        handler.extractItem(hSlot, 1, false);
                                    } else if (ItemStack.isSameItem(remainder, used)) {
                                        // Durability/catalyst: extract and reinsert modified version so count stays stable
                                        ItemStack extracted = handler.extractItem(hSlot, 1, false);
                                        if (!extracted.isEmpty()) {
                                            ItemStack modified = remainder.copy();
                                            // Try to insert back; if cannot, drop to player
                                            ItemStack leftover = insertIntoFirstSlot(handler, modified);
                                            if (!leftover.isEmpty() && !player.getInventory().add(leftover)) {
                                                player.drop(leftover, false);
                                            }
                                        }
                                    } else {
                                        // Consumed with different remainder
                                        ItemStack extracted = handler.extractItem(hSlot, 1, false);
                                        if (!extracted.isEmpty() && !remainder.isEmpty()) {
                                            if (!player.getInventory().add(remainder.copy())) {
                                                player.drop(remainder.copy(), false);
                                            }
                                        }
                                    }
                                    break outer;
                                }
                            }
                        }
                    }
                }

                player.getInventory().setChanged();
            }
            player.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
            return;
        }

        if (recipeHolder instanceof StonecutterRecipe stonecutterRecipe) {
            int maxIterations;
            if (altClick) {
                int targetAdditional = computeLimitedTargetAdditionalStonecutter(stonecutterRecipe);
                maxIterations = targetAdditional;
            } else {
                maxIterations = shiftClick ? 4096 : 1; // safety cap
            }
            for (int i = 0; i < maxIterations; i++) {
                Container combinedInv = buildCombinedInventory();
                if (!canCraftStonecutterFromInventory(stonecutterRecipe, combinedInv)) break;

                ItemStack result = stonecutterRecipe.assemble(null, level.registryAccess());
                if (!player.getInventory().add(result.copy())) {
                    player.drop(result.copy(), false);
                }

                // ✅ Consume from real inventories, not dummy container
                Ingredient ingredient = stonecutterRecipe.getIngredients().getFirst();
                if (!consumeIngredientFromAll(ingredient, 1)) break; // not enough inputs
            }

            player.playNotifySound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
        }
    }

    // Determine how many additional crafts are needed to reach a full stack or fill existing partial stack (crafting table recipe)
    private int computeLimitedTargetAdditional(CraftingRecipe recipe) {
        // Build one instance to get result size
        Container inv = buildCombinedInventory();
        BuiltInput built = buildCraftingInputDetailed(recipe, inv);
        CraftingInput input = built.input();
        if (!recipe.matches(input, level)) return 0;
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) return 0;
    int perCraft = result.getCount();
        int neededToFullStack = Math.max(0, result.getMaxStackSize() - Math.min(result.getMaxStackSize(), findLargestPartialStack(result)));
        if (neededToFullStack == 0) {
            // Provide exactly one full stack if none partial (bounded by material availability implicitly in loop)
            neededToFullStack = result.getMaxStackSize();
        }
        // Convert needed items to craft iterations (ceil division)
        int crafts = (int) Math.ceil(neededToFullStack / (double) perCraft);
        return Math.min(crafts, 64); // soft cap for safety
    }

    private int computeLimitedTargetAdditionalStonecutter(StonecutterRecipe recipe) {
        ItemStack result = recipe.assemble(null, level.registryAccess());
        if (result.isEmpty()) return 0;
        int perCraft = result.getCount();
        int neededToFullStack = Math.max(0, result.getMaxStackSize() - Math.min(result.getMaxStackSize(), findLargestPartialStack(result)));
        if (neededToFullStack == 0) neededToFullStack = result.getMaxStackSize();
        int crafts = (int) Math.ceil(neededToFullStack / (double) perCraft);
        return Math.min(crafts, 64);
    }


    private int findLargestPartialStack(ItemStack prototype) {
        int max = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, prototype)) {
                if (stack.getCount() < stack.getMaxStackSize()) {
                    max = Math.max(max, stack.getCount());
                }
            }
        }
        return max;
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
                if (!found) return false;
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


    // Removed precomputed max craft helpers; dynamic loop handles termination when inputs exhausted.

    // Simple helper to try to insert an ItemStack into the first slot that can accept it; returns remainder if any
    private ItemStack insertIntoFirstSlot(IItemHandler handler, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack toInsert = stack.copy();
        for (int i = 0; i < handler.getSlots(); i++) {
            toInsert = handler.insertItem(i, toInsert, false);
            if (toInsert.isEmpty()) break;
        }
        return toInsert;
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
            for (int i = 0; i < current.size(); i++) {
                lastInventorySnapshot.set(i, current.get(i).copy());
            }
            inventoryVersion++;
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