package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.networking.packets.SyncFavoriteRecipes;
import com.benbenlaw.smartcrafting.networking.packets.SyncSortType;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import com.benbenlaw.smartcrafting.util.MouseUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.neoforged.fml.ModList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class SmartCraftingScreen extends AbstractContainerScreen<SmartCraftingMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table.png");


    private static final ResourceLocation CRAFTING_TOOLTIP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table_render.png");

    private static final ResourceLocation STONECUTTER_TOOLTIP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table_stonecutter_render.png");

    static final ResourceLocation SCROLL_SPRITE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"textures/gui/scroll.png");

    static final ResourceLocation MODE_BUTTON_SPRITE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"textures/gui/mode_button.png");
    static final ResourceLocation STAR_ICON =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"textures/gui/favorite.png");

    public static final String FAVORITES_TAG = "smart_crafting_favorites";
    private List<RecipeHolder<?>> clientRecipes = Collections.emptyList();
    // Server-provided maximum craft counts keyed by recipe id (single-step availability snapshot)
    private final Map<ResourceLocation, Integer> serverCappedCrafts = new HashMap<>();
    private final Map<ResourceLocation, Integer> serverRawCrafts = new HashMap<>();
    private final Set<ResourceLocation> pendingRequests = new HashSet<>();
    private long lastRequestMillis = 0L;
    private long inventoryVersion = -1L;
    // Track raw (pre-cap) crafts if we compute them (server currently caps); we infer by re-estimating locally if shift held
    private static final int CRAFT_CAP = 4096;
    private static final int LARGE_CRAFT_CONFIRM_THRESHOLD = 1000;
    // Stores a confirmation window: recipe id -> timestamp (ms) of first shift click if requires confirmation
    private ResourceLocation pendingLargeCraft = null;
    private long pendingLargeCraftExpiresAt = 0L;

    private static Set<String> extractRequestedTagNeedles(String raw) {
        Set<String> needles = new HashSet<>();
        if (raw == null || raw.isEmpty()) return needles;
        List<String> tokens = AdvancedSearchQuery.tokenize(raw);
        for (String token : tokens) {
            if (token.equals("|")) continue;
            // Inline OR components (avoid splitting phrases containing spaces)
            if (token.indexOf(' ') >= 0) {
                if (token.startsWith("$")) {
                    String n = token.substring(1).toLowerCase(Locale.ROOT).trim();
                    if (!n.isEmpty()) needles.add(n);
                }
            } else {
                String[] parts = token.split("\\|");
                for (String part : parts) {
                    if (part.startsWith("$")) {
                        String n = part.substring(1).toLowerCase(Locale.ROOT).trim();
                        if (!n.isEmpty()) needles.add(n);
                    }
                }
            }
        }
        return needles;
    }

    public void setClientRecipes(List<RecipeHolder<?>> recipes) {
        this.clientRecipes = new ArrayList<>(recipes);

        // Sort by player-visible mod display name (fallback to namespace) then stable output key
        this.clientRecipes = recipes.stream()
            .filter(r -> !r.value().getResultItem(menu.level.registryAccess()).isEmpty())
            .sorted(Comparator.comparing((RecipeHolder<?> r) -> outputModDisplayNameLower(r))
                .thenComparing(r -> outputSortKey(r)))
            .collect(Collectors.toList());

        updateFilteredRecipes();
        moveSelectedRecipeToFront();

        scrollOffset = 0;
    }

    public void setClientRecipesWithMax(List<RecipeHolder<?>> recipes, Map<ResourceLocation, Integer> capped, Map<ResourceLocation, Integer> raw, long version) {
        this.serverCappedCrafts.clear();
        this.serverRawCrafts.clear();
        if (capped != null) this.serverCappedCrafts.putAll(capped);
        if (raw != null) this.serverRawCrafts.putAll(raw);
        this.inventoryVersion = version;
        setClientRecipes(recipes);
    }

    public void mergeCounts(List<ResourceLocation> ids, List<Integer> capped, List<Integer> raw, long version) {
        if (version != this.inventoryVersion) {
            // Invalidate stale cache and replace version
            this.serverCappedCrafts.clear();
            this.serverRawCrafts.clear();
            this.pendingRequests.clear();
            this.inventoryVersion = version;
        }
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ids.get(i);
            int cap = (i < capped.size()) ? capped.get(i) : -1;
            int r = (i < raw.size()) ? raw.get(i) : cap;
            if (cap >= 0) serverCappedCrafts.put(id, cap);
            if (r >= 0) serverRawCrafts.put(id, r);
            pendingRequests.remove(id);
        }
    }

    public void toggleFavorite(ResourceLocation recipeId) {
        ListTag list = menu.player.getPersistentData().getList(FAVORITES_TAG, Tag.TAG_STRING);
        Set<String> favorites = list.stream()
                .map(t -> t.getAsString())
                .collect(Collectors.toSet());

        if (favorites.contains(recipeId.toString())) {
            favorites.remove(recipeId.toString());
        } else {
            favorites.add(recipeId.toString());
        }

        ListTag newList = new ListTag();
        for (String id : favorites) {
            newList.add(StringTag.valueOf(id));
        }
        menu.player.getPersistentData().put(FAVORITES_TAG, newList);

        List<String> favList = new ArrayList<>(favorites);
        PacketDistributor.sendToServer(new SyncFavoriteRecipes(favList));
    }

    // Slot size and layout
    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 17;
    private static final int VISIBLE_ROWS = 3;
    private static final int VISIBLE_COLS = 8;

    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 54;
    private static final int SCROLLBAR_X_OFFSET = 154;
    private static final int SCROLLBAR_Y_OFFSET = 15;

    private int hoveredRecipeIndex = -1;
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private ResourceLocation selectedRecipeId = null;

    public static int visibleRows = 3;

    private EditBox searchBox;
    private String lastSearchText = "";
    private List<RecipeHolder<?>> filteredRecipes = Collections.emptyList();
    private int dragOffsetY = 0;

    private SortType currentSortType = SortType.NAME;

    public SmartCraftingScreen(SmartCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        if (this.menu.player.getPersistentData().getString("smart_crafting_sort_type").equals("mod")) {
            currentSortType = SortType.MOD;
        } else {
            currentSortType = SortType.NAME;

        }

        int x = (width - imageWidth) / 2 + 8;
        int y = (height - imageHeight) / 2 - 14; // position above GUI

        searchBox = new EditBox(font, x, y, 141, 15, Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchTextChanged);
    // Do not auto-focus (JEI style: require user click unless they start typing and we hook key events later)
    searchBox.setFocused(false);
        searchBox.setBordered(true);
        searchBox.setVisible(true);

        addRenderableWidget(searchBox);

        updateFilteredRecipes();
    }

    private void onSearchTextChanged(String text) {
        lastSearchText = text.toLowerCase();
        updateFilteredRecipes();
    }

    private void updateFilteredRecipes() {
        if (lastSearchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(clientRecipes);
        } else {
            AdvancedSearchQuery query = AdvancedSearchQuery.parse(lastSearchText);
            filteredRecipes = clientRecipes.stream().filter(r -> query.matches(r)).toList();
        }

        // Apply sorting
        if (currentSortType == SortType.MOD) {
            filteredRecipes = filteredRecipes.stream()
                .sorted(Comparator.comparing((RecipeHolder<?> r) -> outputModDisplayNameLower(r))
                    .thenComparing(r -> outputSortKey(r)))
                .toList();

            if (selectedRecipeId != null) {
                // Derive the selected recipe's mod display name (lower) for grouping; fallback to namespace if not present
                String selectedModDisplay = null;
                for (RecipeHolder<?> r : filteredRecipes) {
                    if (r.id().equals(selectedRecipeId)) {
                        selectedModDisplay = outputModDisplayNameLower(r);
                        break;
                    }
                }
                if (selectedModDisplay == null) {
                    // Fallback: best guess from recipe id namespace (rare: if selected recipe filtered out)
                    selectedModDisplay = selectedRecipeId.getNamespace().toLowerCase(Locale.ROOT);
                }

                List<RecipeHolder<?>> selectedModRecipes = new ArrayList<>();
                List<RecipeHolder<?>> otherModRecipes = new ArrayList<>();
                for (RecipeHolder<?> recipe : filteredRecipes) {
                    String recipeModDisplay = outputModDisplayNameLower(recipe);
                    if (recipeModDisplay.equals(selectedModDisplay)) {
                        selectedModRecipes.add(recipe);
                    } else {
                        otherModRecipes.add(recipe);
                    }
                }
                List<RecipeHolder<?>> combined = new ArrayList<>(selectedModRecipes.size() + otherModRecipes.size());
                combined.addAll(selectedModRecipes);
                combined.addAll(otherModRecipes);
                filteredRecipes = combined;
            }
        } else if (currentSortType == SortType.NAME) {
            filteredRecipes = filteredRecipes.stream()
                    .sorted(Comparator.comparing(recipe -> {
                        ItemStack result = recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess());
                        return result.getHoverName().getString().toLowerCase(Locale.ROOT);
                    }))
                    .toList();
        }

        // Global selected/favorites reordering ONLY for NAME sort. For MOD sort we keep contiguous mod groups.
        if (currentSortType != SortType.MOD) {
            Set<String> favoriteIds = menu.player.getPersistentData()
                    .getList("smart_crafting_favorites", net.minecraft.nbt.Tag.TAG_STRING)
                    .stream()
                    .map(tag -> tag.getAsString())
                    .collect(Collectors.toSet());

            RecipeHolder<?> selectedRecipe = null;
            List<RecipeHolder<?>> favoriteRecipes = new ArrayList<>();
            List<RecipeHolder<?>> otherRecipes = new ArrayList<>();

            for (RecipeHolder<?> recipe : filteredRecipes) {
                String idStr = recipe.id().toString();
                if (selectedRecipeId != null && recipe.id().equals(selectedRecipeId)) {
                    selectedRecipe = recipe;
                } else if (favoriteIds.contains(idStr)) {
                    favoriteRecipes.add(recipe);
                } else {
                    otherRecipes.add(recipe);
                }
            }

            // Final assembly: selected -> favorites -> others
            filteredRecipes = new ArrayList<>();
            if (selectedRecipe != null) {
                filteredRecipes.add(selectedRecipe);
            }
            filteredRecipes.addAll(favoriteRecipes);
            filteredRecipes.addAll(otherRecipes);
        }
    }

    private String outputModId(RecipeHolder<?> holder) {
        try {
            if (Minecraft.getInstance().level == null) return holder.id().getNamespace();
            ItemStack stack = holder.value().getResultItem(Minecraft.getInstance().level.registryAccess());
            if (stack.isEmpty()) return holder.id().getNamespace();
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return key == null ? holder.id().getNamespace() : key.getNamespace();
        } catch (Throwable t) {
            return holder.id().getNamespace();
        }
    }

    // Overload for ResourceLocation (selectedRecipeId)
    private String outputModId(ResourceLocation recipeId) {
        // Fallback: we don't have direct access to recipe output here, so recipe namespace is best guess
        // (When the selected recipe is present in filteredRecipes we re-derive correct mod above.)
        return recipeId.getNamespace();
    }

    private String outputSortKey(RecipeHolder<?> holder) {
        try {
            if (Minecraft.getInstance().level == null) return holder.id().toString();
            ItemStack stack = holder.value().getResultItem(Minecraft.getInstance().level.registryAccess());
            if (stack.isEmpty()) return holder.id().getPath();
            // Prefer display name (lower) then item id path for stability
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            String path = key == null ? holder.id().getPath() : key.getPath();
            return name + "|" + path;
        } catch (Throwable t) {
            return holder.id().toString();
        }
    }

    // Cache for mod id -> display name lower (player visible); avoids repeated ModList lookups during sorting
    private static final Map<String, String> MOD_DISPLAY_CACHE = new HashMap<>();

    private static String modDisplayNameLower(String modId) {
        return MOD_DISPLAY_CACHE.computeIfAbsent(modId, id -> {
            String disp = id;
            try {
                var opt = ModList.get().getModContainerById(id);
                if (opt.isPresent()) {
                    disp = opt.get().getModInfo().getDisplayName();
                }
            } catch (Throwable ignored) {}
            return disp.toLowerCase(Locale.ROOT);
        });
    }

    private String outputModDisplayNameLower(RecipeHolder<?> holder) {
        String modId = outputModId(holder).toLowerCase(Locale.ROOT);
        return modDisplayNameLower(modId);
    }



    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return searchBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (searchBox.isFocused()) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers) || searchBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);

    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {

        if (filteredRecipes.isEmpty() && !clientRecipes.isEmpty() && lastSearchText.isEmpty()) {
            filteredRecipes = clientRecipes;
        }

        renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderRecipeIngredients(guiGraphics, mouseX, mouseY);
        renderRecipeIcons(guiGraphics, mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderButtonTooltip(guiGraphics, mouseX, mouseY);
        guiGraphics.blit(MODE_BUTTON_SPRITE, leftPos + 153, topPos - 14, 0, 0, 14, 14, 14, 14);
    }
    private void renderRecipeIcons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<RecipeHolder<?>> recipes = this.filteredRecipes;

        int xStart = (width - imageWidth) / 2 + 11;
        int yStart = (height - imageHeight) / 2 + 17;

        int maxRows = (int) Math.ceil(recipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(scrollOffset, 0);
        hoveredRecipeIndex = -1;

        // Load favorites from player data
        Set<String> favoriteIds = menu.player.getPersistentData()
                .getList("smart_crafting_favorites", Tag.TAG_STRING)
                .stream()
                .map(Tag::getAsString)
                .collect(Collectors.toSet());

        for (int i = 0; i < recipes.size(); i++) {
            int row = i / VISIBLE_COLS;
            int col = i % VISIBLE_COLS;

            if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                continue;
            }

            RecipeHolder<?> recipe = recipes.get(i);
            assert Minecraft.getInstance().level != null;

            ItemStack resultStack = recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess()).copy();

            int iconX = xStart + col * ICON_SPACING;
            int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

            // Highlight selected recipe
            if (recipe.id().equals(selectedRecipeId)) {
                guiGraphics.fill(
                        iconX - 1, iconY - 1,
                        iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1,
                        0xAAFFFF00
                );
            }

            // Render item and decorations
            guiGraphics.renderItem(resultStack, iconX, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, resultStack, iconX, iconY);

            // ✅ Render star on top if favorite
            if (favoriteIds.contains(recipe.id().toString())) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200); // ensure it renders in front
                guiGraphics.blit(
                        STAR_ICON,
                        iconX + ICON_SIZE - 4,
                        iconY,
                        0, 0,
                        4, 4,
                        4, 4
                );
                guiGraphics.pose().popPose();
            }

            // Tooltip on hover
            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                    mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                hoveredRecipeIndex = i;

                List<Component> tooltip = new ArrayList<>();
                // Pull in full vanilla tooltip (name + enchantments, lore, modded additions) respecting advanced flag
                Minecraft mc = Minecraft.getInstance();
                TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
                try {
                    var ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.level);
                    tooltip.addAll(resultStack.getTooltipLines(ctx, mc.player, flag));
                } catch (Throwable t) {
                    tooltip.add(resultStack.getHoverName());
                }

                // Spacer before Smart Crafting specific lines
                tooltip.add(Component.empty());
                // If current search query includes tag filters ($...), surface which tags matched this output for transparency
                if (!lastSearchText.isEmpty() && lastSearchText.contains("$")) {
                    Set<String> requestedTags = extractRequestedTagNeedles(lastSearchText);
                    if (!requestedTags.isEmpty()) {
                        List<String> matched = new ArrayList<>();
                        // Collect item tags (lowercase RL string)
                        resultStack.getTags().forEach(tk -> {
                            String rl = tk.location().toString().toLowerCase(Locale.ROOT);
                            String path = tk.location().getPath().toLowerCase(Locale.ROOT);
                            String[] segments = path.split("/");
                            for (String needle : requestedTags) {
                                if (needle.contains(":")) {
                                    if (rl.contains(needle)) { matched.add(rl); break; }
                                } else {
                                    for (String seg : segments) {
                                        if (AdvancedSearchQuery.matchesTagSegment(seg, needle)) { matched.add(rl); break; }
                                    }
                                    if (matched.size() > 0 && matched.get(matched.size()-1).equals(rl)) break; // already matched this tag
                                }
                            }
                        });
                        if (!matched.isEmpty()) {
                            tooltip.add(Component.literal("Tags:").withStyle(ChatFormatting.GRAY));
                            // Limit to first 5 to avoid huge tooltips; show +n if truncated
                            int limit = 5;
                            int shown = Math.min(limit, matched.size());
                            for (int j = 0; j < shown; j++) {
                                tooltip.add(Component.literal(" • " + matched.get(j)).withStyle(ChatFormatting.DARK_GRAY));
                            }
                            if (matched.size() > limit) {
                                tooltip.add(Component.literal("   +" + (matched.size()-limit) + " more").withStyle(ChatFormatting.DARK_GRAY));
                            }
                        }
                    }
                }
                tooltip.add(Component.literal("Right Click to Favorite").withStyle(ChatFormatting.YELLOW));
                if (Minecraft.getInstance().options.advancedItemTooltips) {
                    tooltip.add(Component.literal("Recipe ID: " + recipe.id()).withStyle(ChatFormatting.DARK_GRAY));
                }
                // Debug: show first matched expression and excerpt of matching line when CTRL held
                if (hasControlDown() && !lastSearchText.isEmpty()) {
                    // Re-run query match context quickly to extract first match line (basic + optionally advanced if config/prefix)
                    Minecraft mc2 = Minecraft.getInstance();
                    MatchContext dbgCtx = new MatchContext(recipe.id(), resultStack, mc2);
                    // Build query again (cheap) to populate firstMatchedExpression side effect
                    AdvancedSearchQuery q = AdvancedSearchQuery.parse(lastSearchText);
                    q.matches(recipe);
                    if (dbgCtx.firstMatchedExpression != null) {
                        tooltip.add(Component.literal("Matched: " + dbgCtx.firstMatchedExpression).withStyle(ChatFormatting.GRAY));
                    }
                }

                // Always show shift crafting info
                {
                    int cappedCrafts = serverCappedCrafts.getOrDefault(recipe.id(), -1);
                    int rawCrafts = serverRawCrafts.getOrDefault(recipe.id(), cappedCrafts);
                    boolean unknown = cappedCrafts < 0; // lazy mode sentinel
                    ShiftEstimate est = unknown ? estimateShiftCraftOutputDetailed(recipe.value()) : estimateFromServer(recipe.value(), cappedCrafts);
                    boolean hasAlt = hasAltDown();
                    boolean hasShift = hasShiftDown() && !hasAlt;

                    ChatFormatting baseColor = hasShift ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY;
                    if (est.totalItems > 0) {
                        boolean isCapped = cappedCrafts >= CRAFT_CAP && rawCrafts > cappedCrafts;
                        int perCraft = Math.max(1, recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess()).getCount());
                        long cappedOutput = (long) cappedCrafts * perCraft;
                        long rawOutput = (long) rawCrafts * perCraft;
                        if (unknown) {
                            tooltip.add(Component.literal("SHIFT: ~" + est.crafts + "x -> ~" + est.totalItems + " items (local est)").withStyle(baseColor));
                        } else if (isCapped) {
                            tooltip.add(Component.literal("SHIFT: " + cappedCrafts + "x (cap) of " + rawCrafts + "x -> " + cappedOutput + "/" + rawOutput + " items").withStyle(baseColor));
                        } else {
                            tooltip.add(Component.literal("SHIFT: " + est.crafts + "x -> " + est.totalItems + " items").withStyle(baseColor));
                        }
                        long outputForThreshold = (long) (unknown ? est.crafts : rawCrafts) * perCraft;
                        if (outputForThreshold >= LARGE_CRAFT_CONFIRM_THRESHOLD && hasShift) {
                            if (pendingLargeCraft != null && recipe.id().equals(pendingLargeCraft) && System.currentTimeMillis() < pendingLargeCraftExpiresAt) {
                                tooltip.add(Component.literal(" Shift-click again to confirm large craft").withStyle(ChatFormatting.DARK_RED));
                            } else {
                                tooltip.add(Component.literal(" Shift-click arms first (output >" + LARGE_CRAFT_CONFIRM_THRESHOLD + ")").withStyle(ChatFormatting.RED));
                            }
                        }
                    } else {
                        tooltip.add(Component.literal(unknown ? "SHIFT: ~0 (local est)" : "SHIFT: 0 (missing ingredients)").withStyle(baseColor));
                    }
                }
                // Always show alt info
                tooltip.add(Component.literal("ALT: fill partial stack / one stack").withStyle(hasAltDown() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));

                guiGraphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }

        // Batch request counts for visible recipes still unknown (-1) with debounce (100 ms)
        long now = System.currentTimeMillis();
        if (now - lastRequestMillis > 100) {
            List<ResourceLocation> need = new ArrayList<>();
            for (int i = 0; i < recipes.size(); i++) {
                int row = i / VISIBLE_COLS;
                if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) continue;
                RecipeHolder<?> rh = recipes.get(i);
                ResourceLocation id = rh.id();
                int cap = serverCappedCrafts.getOrDefault(id, -1);
                if (cap < 0 && !pendingRequests.contains(id)) {
                    need.add(id);
                }
            }
            if (!need.isEmpty()) {
                pendingRequests.addAll(need);
                lastRequestMillis = now;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.benbenlaw.smartcrafting.networking.payload.RequestRecipeCountsPayload(need, inventoryVersion));
            }
        }

        // Draw the scroll bar
        int guiX = (width - imageWidth) / 2;
        int guiY = (height - imageHeight) / 2;


        if (maxScroll > 0) {
            float scrollPercent = scrollOffset / (float) maxScroll;

            int knobHeight = 15;
            int trackHeight = SCROLLBAR_HEIGHT - knobHeight;
            int knobY = (int)(scrollPercent * trackHeight);

            guiGraphics.blit(
                    SCROLL_SPRITE,
                    guiX + SCROLLBAR_X_OFFSET,
                    guiY + SCROLLBAR_Y_OFFSET + knobY,
                    0, 0,
                    12, knobHeight,
                    12, 15
            );
        }
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            int scrollbarY = topPos + SCROLLBAR_Y_OFFSET;
            int handleHeight = 15;

            int totalRows = (int) Math.ceil((double) filteredRecipes.size() / VISIBLE_COLS);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);

            int relativeY = (int) mouseY - scrollbarY - dragOffsetY;
            float percent = Mth.clamp(relativeY / (float)(SCROLLBAR_HEIGHT - handleHeight), 0.0F, 1.0F);

            // scrollOffset is in rows
            scrollOffset = Mth.clamp(Math.round(percent * maxScroll), 0, maxScroll);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void moveSelectedRecipeToFront() {
        if (selectedRecipeId == null) return;

        int index = -1;
        for (int i = 0; i < filteredRecipes.size(); i++) {
            if (filteredRecipes.get(i).id().equals(selectedRecipeId)) {
                index = i;
                break;
            }
        }
        if (index > 0) {
            // Move the selected recipe to the front
            List<RecipeHolder<?>> newList = new ArrayList<>(filteredRecipes);
            RecipeHolder<?> selectedRecipe = newList.remove(index);
            newList.add(0, selectedRecipe);
            filteredRecipes = List.copyOf(newList);
        }
    }

    private void renderRecipeIngredients(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int indexToRender = hoveredRecipeIndex;

        // If no recipe is hovered, try to render the selected recipe
        if (indexToRender == -1 && selectedRecipeId != null) {
            // Find the selected recipe index
            for (int i = 0; i < filteredRecipes.size(); i++) {
                if (filteredRecipes.get(i).id().equals(selectedRecipeId)) {
                    indexToRender = i;
                    break;
                }
            }
        }

        if (indexToRender == -1) return; // Nothing to render

        if (filteredRecipes.isEmpty() || indexToRender < 0 || indexToRender >= filteredRecipes.size()) {
            return;
        }

        RecipeHolder<?> recipeHolder = filteredRecipes.get(indexToRender);
        Recipe<?> recipe = recipeHolder.value();

        List<Ingredient> ingredients = recipe.getIngredients();

        int gridSize = 3;
        int iconSize = 18;  // spacing between icons

        int tooltipX = this.leftPos - 58;
        int tooltipY = this.topPos;

        int recipeWidth = 3;
        int recipeHeight = 3;

        int offsetX = 0;
        int offsetY = 0;

        ResourceLocation tooltipTexture;

        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            recipeWidth = shaped.getWidth();
            recipeHeight = shaped.getHeight();
            offsetX = (gridSize - recipeWidth) / 2;
            offsetY = (gridSize - recipeHeight) / 2;

            tooltipTexture = CRAFTING_TOOLTIP_TEXTURE;
        } else if (recipe instanceof StonecutterRecipe) {

            tooltipTexture = STONECUTTER_TOOLTIP_TEXTURE;

            // Draw stonecutter input and output explicitly
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 0);

            RenderSystem.setShaderTexture(0, tooltipTexture);
            guiGraphics.blit(
                    tooltipTexture,
                    tooltipX,
                    tooltipY,
                    0, 0,
                    62, 62,
                    62,
                    62
            );

            ItemStack inputStack = ItemStack.EMPTY;
            if (!ingredients.isEmpty() && !ingredients.getFirst().isEmpty()) {
                ItemStack[] matchingStacks = ingredients.getFirst().getItems();
                if (matchingStacks.length > 0) {
                    inputStack = matchingStacks[0];  // Just show first matching input for simplicity
                }
            }
            int inputX = tooltipX + 5;
            int inputY = tooltipY + iconSize + 5;
            guiGraphics.renderItem(inputStack, inputX, inputY);
            if (!inputStack.isEmpty()) {
                guiGraphics.renderItemDecorations(minecraft.font, inputStack, inputX, inputY);
            }

            int outputX = tooltipX + 2 * iconSize + 8;
            int outputY = tooltipY + iconSize + 5;
            guiGraphics.renderItem(new ItemStack(Items.STONECUTTER), outputX, outputY);
            guiGraphics.pose().popPose();

            return;
        } else {
            tooltipTexture = CRAFTING_TOOLTIP_TEXTURE;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 0);

        RenderSystem.setShaderTexture(0, tooltipTexture);
        guiGraphics.blit(
                tooltipTexture,
                tooltipX,
                tooltipY,
                0, 0,
                62, 62,
                62,
                62
        );

        // Draw ingredients with proper mapping for non-stonecutter recipes
        for (int slotY = 0; slotY < gridSize; slotY++) {
            for (int slotX = 0; slotX < gridSize; slotX++) {
                int ingredientX = slotX - offsetX;
                int ingredientY = slotY - offsetY;

                ItemStack stack = ItemStack.EMPTY;

                // Only draw if inside recipe bounds
                if (ingredientX >= 0 && ingredientX < recipeWidth && ingredientY >= 0 && ingredientY < recipeHeight) {
                    int ingredientIndex = ingredientY * recipeWidth + ingredientX;
                    if (ingredientIndex < ingredients.size()) {
                        Ingredient ing = ingredients.get(ingredientIndex);
                        if (!ing.isEmpty()) {
                            ItemStack matchedStack = ItemStack.EMPTY;
                            ItemStack[] matchingStacks = ing.getItems();

                            if (matchingStacks.length > 0) {
                                assert Minecraft.getInstance().player != null;
                                for (ItemStack inventoryStack : Minecraft.getInstance().player.getInventory().items) {
                                    if (inventoryStack.isEmpty()) continue;
                                    for (ItemStack candidate : matchingStacks) {
                                        if (ItemStack.isSameItem(inventoryStack, candidate)) {
                                            matchedStack = inventoryStack.copy();
                                            matchedStack.setCount(1); // Display single item
                                            break;
                                        }
                                    }
                                    if (!matchedStack.isEmpty()) break;
                                }

                                if (matchedStack.isEmpty()) {
                                    matchedStack = matchingStacks[0];
                                }

                                stack = matchedStack;
                            }
                        }
                    }
                }

                int x = tooltipX + slotX * iconSize + 5;
                int y = tooltipY + slotY * iconSize + 5;

                guiGraphics.renderItem(stack, x, y);
                if (!stack.isEmpty()) {
                    guiGraphics.renderItemDecorations(minecraft.font, stack, x, y);
                }
            }
        }

        guiGraphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false);
        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean clickedOnSearchBox = searchBox.isMouseOver(mouseX, mouseY);

        //Button Press
        if (MouseUtil.isMouseOver(mouseX, mouseY, leftPos + 153, topPos - 14, 14, 14)) {
            if (currentSortType == SortType.NAME) {
                currentSortType = SortType.MOD;
                PacketDistributor.sendToServer(new SyncSortType("mod"));
                this.menu.player.getPersistentData().putString("smart_crafting_sort_type", "mod");
            } else {
                currentSortType = SortType.NAME;
                PacketDistributor.sendToServer(new SyncSortType("name"));
                this.menu.player.getPersistentData().putString("smart_crafting_sort_type", "name");
            }

            updateFilteredRecipes();
            moveSelectedRecipeToFront();
            scrollOffset = 0;  // Reset scroll to top
            return true;
        }

        if (!clickedOnSearchBox) {
            // Clicked outside the search box, unfocus it
            searchBox.setFocused(false);
        }

        // Existing mouseClicked logic below
        if (button == 0) {
            int xStart = (width - imageWidth) / 2 + 11;
            int yStart = (height - imageHeight) / 2 + 17;

            for (int i = 0; i < filteredRecipes.size(); i++) {
                int row = i / VISIBLE_COLS;
                int col = i % VISIBLE_COLS;

                if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                    continue;
                }

                int iconX = xStart + col * ICON_SPACING;
                int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

                if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                        mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {

                    var recipeId = filteredRecipes.get(i).id();
                    assert Minecraft.getInstance().player != null;
                    boolean altHeld = hasAltDown();
                    // Alt overrides shift semantics: if Alt is held, treat as non-shift for purposes of unlimited crafting & confirmation
                    boolean isShiftClick = hasShiftDown() && !altHeld;
                    boolean willCraft = true; // assume crafting unless we arm confirmation

                    if (isShiftClick) {
                        int cappedCrafts = serverCappedCrafts.getOrDefault(recipeId, -1);
                        int rawCrafts = serverRawCrafts.getOrDefault(recipeId, cappedCrafts);
                        int perCraft = 1;
                        if (Minecraft.getInstance().level != null) {
                            ItemStack res = filteredRecipes.get(i).value().getResultItem(Minecraft.getInstance().level.registryAccess());
                            if (!res.isEmpty()) perCraft = Math.max(1, res.getCount());
                        }
                        long potentialOutput = (long) rawCrafts * perCraft;
                        if (potentialOutput >= LARGE_CRAFT_CONFIRM_THRESHOLD) {
                            long now = System.currentTimeMillis();
                            if (pendingLargeCraft == null || !pendingLargeCraft.equals(recipeId) || now > pendingLargeCraftExpiresAt) {
                                // Arm confirmation instead of sending packet; do NOT reorder yet.
                                pendingLargeCraft = recipeId;
                                pendingLargeCraftExpiresAt = now + 3000; // 3 second window
                                willCraft = false;
                            } else {
                                // Second click within window -> proceed and clear
                                pendingLargeCraft = null;
                                pendingLargeCraftExpiresAt = 0L;
                            }
                        }
                    }

                    if (!willCraft) {
                        // Optionally highlight selection without moving list order: just remember id
                        selectedRecipeId = recipeId; // keep visible highlighting if already in view
                        return true;
                    }

                    // Actual craft: now update selection & ordering
                    selectedRecipeId = recipeId;
                    moveSelectedRecipeToFront();
                    scrollOffset = 0;
                    PacketDistributor.sendToServer(new SmartCraftingRecipeClickPayload(recipeId, isShiftClick, altHeld));
                    return true;
                }
            }
        }

        if (button == 1) {
            if (clickedOnSearchBox) {
                searchBox.setValue("");
                onSearchTextChanged("");
                return true;
            }

            int xStart = (width - imageWidth) / 2 + 11;
            int yStart = (height - imageHeight) / 2 + 17;

            for (int i = 0; i < filteredRecipes.size(); i++) {
                int row = i / VISIBLE_COLS;
                int col = i % VISIBLE_COLS;

                if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) continue;

                int iconX = xStart + col * ICON_SPACING;
                int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

                if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                        mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {

                    toggleFavorite(filteredRecipes.get(i).id());
                    updateFilteredRecipes(); // Re-sort
                    return true;
                }
            }
        }

        // Scrollbar logic
        int scrollbarX = leftPos + SCROLLBAR_X_OFFSET;
        int scrollbarY = topPos + SCROLLBAR_Y_OFFSET;
        int handleHeight = 15;
        int totalRows = (int) Math.ceil((double) filteredRecipes.size() / VISIBLE_COLS);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        if (maxScroll > 0) {
            float scrollPercent = scrollOffset / (float) maxScroll;
            int handleY = scrollbarY + (int) ((SCROLLBAR_HEIGHT - handleHeight) * scrollPercent);

            if (MouseUtil.isMouseOver(mouseX, mouseY, scrollbarX, handleY, SCROLLBAR_WIDTH, handleHeight)) {
                isDraggingScrollbar = true;
                dragOffsetY = (int) mouseY - handleY;
                return true;
            } else {
                isDraggingScrollbar = false;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int maxRows = (int) Math.ceil(filteredRecipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        if (deltaY < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
        } else if (deltaY > 0 && scrollOffset > 0) {
            scrollOffset--;
        }

        return true;
    }

    public void renderButtonTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (MouseUtil.isMouseOver(mouseX, mouseY, leftPos + 153, topPos - 14, 14, 14)) {

            String typeString = this.menu.player.getPersistentData().getString("smart_crafting_sort_type");

            if (typeString.equals("name")) {
                guiGraphics.renderTooltip(font,
                        Component.translatable("block.smartcrafting.smart_crafting_table.sort_by_name")
                                .withStyle(ChatFormatting.WHITE), mouseX, mouseY);

            } else {
                guiGraphics.renderTooltip(font,
                        Component.translatable("block.smartcrafting.smart_crafting_table.sort_by_mod")
                                .withStyle(ChatFormatting.WHITE), mouseX, mouseY);

            }
        }
    }

    private record ShiftEstimate(int crafts, int totalItems) {}

    private ShiftEstimate estimateFromServer(Recipe<?> recipe, int serverMaxCrafts) {
        if (Minecraft.getInstance().level == null) return new ShiftEstimate(0,0);
        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        if (result.isEmpty()) return new ShiftEstimate(0,0);
        int perCraft = Math.max(1, result.getCount());
        long total = (long) perCraft * serverMaxCrafts;
        if (total > Integer.MAX_VALUE) total = Integer.MAX_VALUE;
        return new ShiftEstimate(serverMaxCrafts, (int) total);
    }

    private ShiftEstimate estimateShiftCraftOutputDetailed(Recipe<?> recipe) {
        // Robust estimation: include all menu slots (covers player inventory + any exposed storage) and
        // correctly aggregate duplicate ingredient requirements. Still client-side only (may differ from server aggregation).
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return new ShiftEstimate(0,0);
        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        if (result.isEmpty()) return new ShiftEstimate(0,0);
        List<Ingredient> rawIngredients = recipe.getIngredients();
        if (rawIngredients.isEmpty()) return new ShiftEstimate(0,0);

        int perCraft = Math.max(1, result.getCount());

        // Group identical logical ingredients by a signature of their possible items so we account for multi-count requirements.
        record IngGroup(String signature, List<Ingredient> members, Ingredient representative, int requiredCount) {}
        Map<String, List<Ingredient>> grouped = new HashMap<>();
        for (Ingredient ing : rawIngredients) {
            if (ing == null || ing.isEmpty()) continue;
            String sig = ingredientSignature(ing);
            grouped.computeIfAbsent(sig, k -> new ArrayList<>()).add(ing);
        }
        if (grouped.isEmpty()) return new ShiftEstimate(0,0);

        List<IngGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<Ingredient>> e : grouped.entrySet()) {
            groups.add(new IngGroup(e.getKey(), e.getValue(), e.getValue().getFirst(), e.getValue().size()));
        }

        int craftsPossible = Integer.MAX_VALUE;

        // Build a snapshot of accessible item stacks (avoid double counting by just iterating menu slots once)
        // Note: Player inventory slots are also in menu.slots, so we don't separately iterate player inventory.
        List<ItemStack> accessible = this.menu.slots.stream()
                .filter(slot -> slot.hasItem())
                .map(slot -> slot.getItem())
                .toList();

        for (IngGroup g : groups) {
            int available = 0;
            for (ItemStack stack : accessible) {
                if (stack.isEmpty()) continue;
                if (g.representative().test(stack)) {
                    // Treat likely catalyst/non-consumed items specially: if item has a crafting remaining item equal to itself
                    // or is damageable (tool), count only 1 regardless of its current count, since it isn't fully consumed.
                    boolean catalystLike = false;
                    ItemStack rem = stack.getCraftingRemainingItem();
                    if (!rem.isEmpty() && ItemStack.isSameItemSameComponents(rem, stack)) catalystLike = true;
                    if (stack.isDamageableItem()) catalystLike = true;
                    if (catalystLike) {
                        available += 999999; // effectively infinite for estimation purposes.
                    } else {
                        available += stack.getCount();
                    }
                }
            }
            if (available <= 0) {
                // If any ingredient group reports zero, crafting not possible.
                return new ShiftEstimate(0,0);
            }
            // For catalyst-like groups (treated as large number), division will yield large craftsPossible but we'll clamp later.
            craftsPossible = Math.min(craftsPossible, available / g.requiredCount());
            if (craftsPossible <= 0) return new ShiftEstimate(0,0);
        }

        if (craftsPossible == Integer.MAX_VALUE) return new ShiftEstimate(0,0);
        // Apply a soft cap to avoid absurd numbers if catalyst set inflated availability.
        craftsPossible = Math.min(craftsPossible, 4096); // mirrors server safety cap logic
        long totalItems = (long) craftsPossible * perCraft;
        if (totalItems > Integer.MAX_VALUE) totalItems = Integer.MAX_VALUE;
        return new ShiftEstimate(craftsPossible, (int) totalItems);
    }

    private String ingredientSignature(Ingredient ing) {
        // Deterministic signature of ingredient's possible item resource locations.
        ItemStack[] stacks = ing.getItems();
        if (stacks.length == 0) {
            // Fallback: use identity hash to differentiate empties (not ideal but stable enough client-side).
            return "empty:" + System.identityHashCode(ing);
        }
        return Arrays.stream(stacks)
                .filter(Objects::nonNull)
                .map(s -> {
                    ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
                    return key == null ? "unknown" : key.toString();
                })
                .sorted()
                .collect(Collectors.joining("|"));
    }

    /* ===================== Advanced Search System ===================== */
    private static class AdvancedSearchQuery {
        // Top-level: OR across conjunctions separated by a standalone '|'
        private final List<Conjunction> disjunctions; // if empty => match all
        private AdvancedSearchQuery(List<Conjunction> disjunctions) { this.disjunctions = disjunctions; }

        static AdvancedSearchQuery parse(String raw) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) return new AdvancedSearchQuery(List.of());
            List<String> tokens = tokenize(trimmed);
            List<Conjunction> disj = new ArrayList<>();
            List<List<SearchCondition>> currentGroups = new ArrayList<>();
            for (String rawToken : tokens) {
                String token = rawToken;
                if (token.isEmpty()) continue;
                if (token.equals("|")) { // finalize current conjunction
                    if (!currentGroups.isEmpty()) {
                        disj.add(new Conjunction(currentGroups));
                        currentGroups = new ArrayList<>();
                    }
                    continue;
                }
                List<SearchCondition> orList = new ArrayList<>();
                if (token.indexOf(' ') >= 0) {
                    // Phrase token (may include spaces) - treat as single search part
                    orList.add(parsePart(token));
                } else {
                    for (String part : token.split("\\|")) {
                        if (part.isEmpty()) continue;
                        orList.add(parsePart(part));
                    }
                }
                if (!orList.isEmpty()) currentGroups.add(orList);
            }
            if (!currentGroups.isEmpty()) disj.add(new Conjunction(currentGroups));
            return new AdvancedSearchQuery(disj);
        }

        // Tokenization supporting quoted phrases ("...") that may include spaces and pipes.
        // Quotes are stripped. A standalone | (surrounded by whitespace or as its own token) becomes separate token.
        static List<String> tokenize(String raw) {
            List<String> tokens = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                    continue; // drop quote
                }
                if (!inQuotes) {
                    if (Character.isWhitespace(c)) {
                        if (current.length() > 0) {
                            tokens.add(current.toString());
                            current.setLength(0);
                        }
                        continue;
                    }
                    if (c == '|') {
                        if (current.length() > 0) {
                            tokens.add(current.toString());
                            current.setLength(0);
                        }
                        tokens.add("|");
                        continue;
                    }
                }
                current.append(c);
            }
            if (current.length() > 0) tokens.add(current.toString());
            // Lowercase normalization (except inside quotes already appended)
            for (int i = 0; i < tokens.size(); i++) tokens.set(i, tokens.get(i));
            return tokens;
        }

        private static SearchCondition parsePart(String partRaw) {
            String part = partRaw.toLowerCase(Locale.ROOT);
            boolean negative = false;
            while (part.startsWith("!") || part.startsWith("-")) { // negation support (leading chain)
                negative = true;
                part = part.substring(1);
            }
            // Explicit recipe namespace: @r:modid
            if (part.startsWith("@r:")) {
                String mod = part.substring(4).trim();
                SearchCondition base = ctx -> ctx.recipeId.getNamespace().contains(mod);
                return negative ? ctx -> !base.test(ctx) : base;
            }
            // Default @ = output namespace
            if (part.startsWith("@")) {
                String mod = part.substring(1).trim();
                SearchCondition base = ctx -> ctx.matchesModToken(mod);
                return negative ? ctx -> !base.test(ctx) : base;
            }
            // Tooltip search
            if (part.startsWith("#")) {
                boolean includeAdvanced = false;
                if (part.startsWith("#!")) { // #!keyword => include advanced lines
                    includeAdvanced = true;
                    part = part.substring(2);
                } else {
                    part = part.substring(1);
                }
                String needle = part.trim();
                if (needle.isEmpty()) {
                    SearchCondition base = ctx -> false;
                    return negative ? ctx -> !base.test(ctx) : base;
                }
                String finalNeedle = needle;
                boolean finalIncludeAdvanced = includeAdvanced;
                SearchCondition base = ctx -> {
                    List<String> lines = finalIncludeAdvanced ? ctx.tooltipAdvancedLower() : ctx.tooltipBasicLower();
                    for (String line : lines) if (line.contains(finalNeedle)) return true;
                    return false;
                };
                return negative ? ctx -> !base.test(ctx) : base;
            }
            // Item ID (resource location)
            if (part.startsWith("&")) {
                String id = part.substring(1);
                SearchCondition base = ctx -> ctx.outputId.contains(id);
                return negative ? ctx -> !base.test(ctx) : base;
            }
            // Tag (ore dictionary style) -> search any item tag key path or namespace
            if (part.startsWith("$")) {
                String tagNeedle = part.substring(1).toLowerCase(Locale.ROOT).trim();
                SearchCondition base = ctx -> ctx.tags().stream().anyMatch(tk -> {
                    ResourceLocation loc = tk.location();
                    String ns = loc.getNamespace().toLowerCase(Locale.ROOT);
                    String path = loc.getPath().toLowerCase(Locale.ROOT);
                    if (tagNeedle.isEmpty()) return false;
                    // If user specifies namespace explicitly (contains ':'), do simple substring match
                    if (tagNeedle.contains(":")) {
                        return (ns + ":" + path).contains(tagNeedle);
                    }
                    // Namespace NOT searched unless explicitly typed; focus on path segments
                    String[] segments = path.split("/");
                    for (String seg : segments) {
                        if (matchesTagSegment(seg, tagNeedle)) return true;
                    }
                    return false;
                });
                return negative ? ctx -> !base.test(ctx) : base;
            }
            // Creative tab lookup
            if (part.startsWith("%")) {
                String tab = part.substring(1);
                SearchCondition base = ctx -> ctx.creativeTabs().stream().anyMatch(s -> s.contains(tab));
                return negative ? ctx -> !base.test(ctx) : base;
            }
            // Plain name search on output display name
            String nameNeedle = part;
            SearchCondition base = ctx -> ctx.outputName.contains(nameNeedle);
            return negative ? ctx -> !base.test(ctx) : base;
        }

        private static boolean matchesTagSegment(String segment, String needle) {
            if (segment.equals(needle)) return true; // exact
            // common plural/singular leniency
            if (segment.endsWith("s") && segment.substring(0, segment.length()-1).equals(needle)) return true;
            if (needle.endsWith("s") && needle.substring(0, needle.length()-1).equals(segment)) return true;
            // allow hyphen/underscore unification
            String normSeg = segment.replace('-', '_');
            String normNeedle = needle.replace('-', '_');
            if (normSeg.equals(normNeedle)) return true;
            // partial but only if segment starts with needle (avoid mid-word like 'forge' containing 'ore')
            return normSeg.startsWith(normNeedle);
        }

        boolean matches(RecipeHolder<?> holder) {
            if (disjunctions.isEmpty()) return true; // no constraints
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;
            ItemStack out = holder.value().getResultItem(mc.level.registryAccess());
            if (out.isEmpty()) return false;
            MatchContext ctx = new MatchContext(holder.id(), out, mc);
            // OR across conjunctions
            for (Conjunction conj : disjunctions) {
                if (conj.matches(ctx)) return true;
            }
            return false;
        }
        private record Conjunction(List<List<SearchCondition>> groups) {
            boolean matches(MatchContext ctx) {
                if (groups.isEmpty()) return true; // empty conjunction vacuously true
                for (List<SearchCondition> group : groups) { // AND across groups
                    boolean any = false;
                    for (SearchCondition cond : group) {
                        if (cond.test(ctx)) { if (ctx.firstMatchedExpression == null) ctx.firstMatchedExpression = cond.toString(); any = true; break; }
                    }
                    if (!any) return false; // this AND group failed
                }
                return true;
            }
        }
    }

    @FunctionalInterface
    private interface SearchCondition { boolean test(MatchContext ctx); }

    private static class MatchContext {
        final ResourceLocation recipeId;
        final ItemStack output;
        final String outputId;
        final String outputMod;
    final String outputName;
    final String outputModDisplay; // display name (lowercase) if available
    final String outputModDisplaySanitized; // punctuation stripped
        final Minecraft mc;
        List<Component> tooltipCache;
        List<Component> tooltipBasicCache;
        List<Component> tooltipAdvancedCache;
        List<String> tooltipBasicLowerCache;
        List<String> tooltipAdvancedLowerCache;
        List<String> creativeTabsCache;
        List<TagKey<net.minecraft.world.item.Item>> tagCache;
        String firstMatchedExpression;
        MatchContext(ResourceLocation rid, ItemStack output, Minecraft mc) {
            this.recipeId = rid;
            this.output = output;
            this.mc = mc;
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
            this.outputId = key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
            this.outputMod = key == null ? "" : key.getNamespace().toLowerCase(Locale.ROOT);
            this.outputName = output.getHoverName().getString().toLowerCase(Locale.ROOT);
            String disp = null;
            try {
                var opt = ModList.get().getModContainerById(this.outputMod);
                if (opt.isPresent()) {
                    disp = opt.get().getModInfo().getDisplayName();
                }
            } catch (Throwable ignored) {}
            if (disp == null) disp = this.outputMod; // fallback to mod id
            disp = disp.toLowerCase(Locale.ROOT);
            this.outputModDisplay = disp;
            this.outputModDisplaySanitized = disp.replace("'", "").replace("\"", "");
        }
        boolean matchesModToken(String token) {
            if (token.isEmpty()) return false;
            if (outputMod.contains(token)) return true;
            if (outputModDisplay.contains(token)) return true;
            return outputModDisplaySanitized.contains(token);
        }
        // Legacy combined tooltip (advanced or normal depending on player setting) kept for compatibility
        List<Component> tooltip() {
            if (tooltipCache == null) {
                tooltipCache = new ArrayList<>();
                try {
                    var ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.level);
                    tooltipCache.addAll(output.getTooltipLines(ctx, mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
                } catch (Throwable ignored) { }
            }
            return tooltipCache;
        }
        List<Component> tooltipBasic() {
            if (tooltipBasicCache == null) {
                tooltipBasicCache = new ArrayList<>();
                try {
                    var ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.level);
                    tooltipBasicCache.addAll(output.getTooltipLines(ctx, mc.player, TooltipFlag.Default.NORMAL));
                } catch (Throwable ignored) { }
            }
            return tooltipBasicCache;
        }
        List<Component> tooltipAdvanced() {
            if (tooltipAdvancedCache == null) {
                tooltipAdvancedCache = new ArrayList<>();
                try {
                    var ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.level);
                    tooltipAdvancedCache.addAll(output.getTooltipLines(ctx, mc.player, TooltipFlag.Default.ADVANCED));
                } catch (Throwable ignored) { }
            }
            return tooltipAdvancedCache;
        }
        List<String> tooltipBasicLower() {
            if (tooltipBasicLowerCache == null) {
                tooltipBasicLowerCache = tooltipBasic().stream().map(c -> c.getString().toLowerCase(Locale.ROOT)).toList();
            }
            return tooltipBasicLowerCache;
        }
        List<String> tooltipAdvancedLower() {
            if (tooltipAdvancedLowerCache == null) {
                tooltipAdvancedLowerCache = tooltipAdvanced().stream().map(c -> c.getString().toLowerCase(Locale.ROOT)).toList();
            }
            return tooltipAdvancedLowerCache;
        }
        List<String> creativeTabs() {
            if (creativeTabsCache == null) {
                // Placeholder: creative tab API changed; fallback to empty list until proper integration added.
                creativeTabsCache = List.of();
            }
            return creativeTabsCache;
        }
        List<TagKey<net.minecraft.world.item.Item>> tags() {
            if (tagCache == null) {
                tagCache = output.getTags().toList();
            }
            return tagCache;
        }
    }
}