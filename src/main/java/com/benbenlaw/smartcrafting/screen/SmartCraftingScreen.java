package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.core.util.MouseUtil;
import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class SmartCraftingScreen extends AbstractContainerScreen<SmartCraftingMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table.png");

    private static final int TOOLTIP_SIZE = 62;

    private static final ResourceLocation CRAFTING_TOOLTIP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table_render.png");

    static final ResourceLocation SCROLL_SPRITE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"textures/gui/scroll.png");

    private List<CraftingRecipe> recipes = Collections.emptyList();

    private List<RecipeHolder<CraftingRecipe>> clientRecipes = Collections.emptyList();

    public void setClientRecipes(List<RecipeHolder<CraftingRecipe>> recipes) {
        this.clientRecipes = recipes;
        updateFilteredRecipes();
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

    private static final int COLUMNS = 8;
    public static int visibleRows = 3;
    private final int itemsPerPage = COLUMNS * visibleRows;

    private EditBox searchBox;
    private String lastSearchText = "";
    private List<RecipeHolder<CraftingRecipe>> filteredRecipes = Collections.emptyList();
    private int dragOffsetY = 0;

    public SmartCraftingScreen(SmartCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - imageWidth) / 2 + 8;
        int y = (height - imageHeight) / 2 - 14; // position above GUI

        searchBox = new EditBox(font, x, y, 141, 15, Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchTextChanged);
        searchBox.setFocused(true);
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
            filteredRecipes = clientRecipes;
        } else {
            filteredRecipes = clientRecipes.stream()
                    .filter(holder -> {
                        ItemStack result = holder.value().getResultItem(Minecraft.getInstance().level.registryAccess());
                        String name = result.getHoverName().getString();
                        return name.toLowerCase(Locale.ROOT).contains(lastSearchText);
                    })
                    .toList();
        }
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

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderRecipeIngredients(guiGraphics, mouseX, mouseY);
        renderRecipeIcons(guiGraphics, mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);

        //guiGraphics.blit(SCROLL_SPRITE, x + SCROLLBAR_X_OFFSET, y + SCROLLBAR_Y_OFFSET, 168, 0, 12, 15, 12, 15);

    }

    private void renderRecipeIcons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<RecipeHolder<CraftingRecipe>> recipes = this.filteredRecipes;

        int xStart = (width - imageWidth) / 2 + 11;
        int yStart = (height - imageHeight) / 2 + 17;

        int maxRows = (int) Math.ceil(recipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(scrollOffset, 0);
        hoveredRecipeIndex = -1;

        for (int i = 0; i < recipes.size(); i++) {
            int row = i / VISIBLE_COLS;
            int col = i % VISIBLE_COLS;

            if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                continue;
            }

            RecipeHolder<CraftingRecipe> recipe = recipes.get(i);
            assert Minecraft.getInstance().level != null;

            ItemStack resultStack = recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess()).copy();

            int iconX = xStart + col * ICON_SPACING;
            int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

            guiGraphics.renderItem(resultStack, iconX, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, resultStack, iconX, iconY);

            // Check if mouse is over this icon
            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                    mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                hoveredRecipeIndex = i; // mark this recipe as hovered

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(resultStack.getHoverName());

                if (Minecraft.getInstance().options.advancedItemTooltips) {
                    tooltip.add(Component.literal("Recipe ID: " + recipe.id()).withStyle(ChatFormatting.DARK_GRAY));
                }

                if (hasShiftDown()) {
                    tooltip.add(Component.literal("SHIFT to craft as many as possible!").withStyle(ChatFormatting.RED));
                }

                guiGraphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }

        // Draw the scroll bar
        int guiX = (width - imageWidth) / 2;
        int guiY = (height - imageHeight) / 2;

        int totalRows = (int) Math.ceil((double) filteredRecipes.size() / VISIBLE_COLS);

        if (maxScroll > 0) {
            float scrollPercent = scrollOffset / (float) maxScroll;

            int knobHeight = 15; // Height of your knob texture
            int trackHeight = SCROLLBAR_HEIGHT - knobHeight;
            int knobY = (int)(scrollPercent * trackHeight);

            guiGraphics.blit(
                    SCROLL_SPRITE,
                    guiX + SCROLLBAR_X_OFFSET,
                    guiY + SCROLLBAR_Y_OFFSET + knobY,
                    0, 0,             // U, V in your texture
                    12, knobHeight,   // width, height of the knob texture
                    12, 15            // texture atlas size (only needed if you're using texture atlas)
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


    private void renderRecipeIngredients(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hoveredRecipeIndex == -1) return;

        if (filteredRecipes.isEmpty() || hoveredRecipeIndex < 0 || hoveredRecipeIndex >= filteredRecipes.size()) {
            return;
        }
        RecipeHolder<CraftingRecipe> recipeHolder = filteredRecipes.get(hoveredRecipeIndex);
        CraftingRecipe recipe = recipeHolder.value();

        List<Ingredient> ingredients = recipe.getIngredients();

        int gridSize = 3;
        int iconSize = 18;  // spacing between icons

        // Fixed tooltip position left of GUI
        int tooltipX = this.leftPos - 58;
        int tooltipY = this.topPos;

        int recipeWidth = 3;
        int recipeHeight = 3;

        // Calculate offset to center recipe in 3x3 grid
        int offsetX = 0;
        int offsetY = 0;

        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            recipeWidth = shaped.getWidth();
            recipeHeight = shaped.getHeight();
            offsetX = (gridSize - recipeWidth) / 2;
            offsetY = (gridSize - recipeHeight) / 2;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 0);

        int texWidth = 64;
        int texHeight = 64;

        RenderSystem.setShaderTexture(0, CRAFTING_TOOLTIP_TEXTURE);
        guiGraphics.blit(
                CRAFTING_TOOLTIP_TEXTURE,
                tooltipX,
                tooltipY,
                0, 0,
                62, 62,
                62,
                62
        );

        // Draw ingredients with proper mapping
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
                                // Try to find a match from player's inventory
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

                                // Fallback to first matching item
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
        if (button == 0) {
            int xStart = (width - imageWidth) / 2 + 11;
            int yStart = (height - imageHeight) / 2 + 17;

            for (int i = 0; i < filteredRecipes.size(); i++) {
                int row = i / VISIBLE_COLS;
                int col = i % VISIBLE_COLS;

                // Only consider visible rows with scrollOffset
                if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                    continue;
                }

                int iconX = xStart + col * ICON_SPACING;
                int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

                if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                        mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {

                    var recipeId = filteredRecipes.get(i).id();
                    assert Minecraft.getInstance().player != null;
                    boolean isShiftClick = hasShiftDown();
                    PacketDistributor.sendToServer(new SmartCraftingRecipeClickPayload(recipeId, isShiftClick));
                    return true; // Click handled
                }
            }
        }

        if (button == 1) { // Right-click
            if (searchBox.isMouseOver(mouseX, mouseY)) {
                searchBox.setValue("");
                onSearchTextChanged(""); // Also trigger filtering
                return true;
            }
        }

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

        // Scrolling up: deltaY > 0 -> decrease scrollOffset (go up one row)
        // Scrolling down: deltaY < 0 -> increase scrollOffset (go down one row)
        if (deltaY < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
        } else if (deltaY > 0 && scrollOffset > 0) {
            scrollOffset--;
        }

        return true;
    }
}
