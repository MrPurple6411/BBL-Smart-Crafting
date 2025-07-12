package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import com.benbenlaw.smartcrafting.util.SimpleRecipeHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartCraftingScreen extends AbstractContainerScreen<SmartCraftingMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table.png");

    private static final int TOOLTIP_SIZE = 62;
    private static final ResourceLocation CRAFTING_TOOLTIP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "textures/gui/smart_crafting_table_render.png");

    private List<CraftingRecipe> recipes = Collections.emptyList();
    private List<SimpleRecipeHolder>  clientRecipes = Collections.emptyList();

    public void setClientRecipes(List<SimpleRecipeHolder> recipes) {
        this.clientRecipes = recipes;
    }

    // Slot size and layout
    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 17;
    private static final int VISIBLE_ROWS = 3;
    private static final int VISIBLE_COLS = 8;

    private int hoveredRecipeIndex = -1;
    private int scrollOffset = 0;

    public SmartCraftingScreen(SmartCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderRecipeIngredients(guiGraphics, mouseX, mouseY);
        renderRecipeIcons(guiGraphics, mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderRecipeIcons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<SimpleRecipeHolder> recipes = this.clientRecipes;

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

            SimpleRecipeHolder recipe = recipes.get(i);
            assert Minecraft.getInstance().level != null;

            ItemStack resultStack = recipe.value().assemble(CraftingInput.EMPTY, Minecraft.getInstance().level.registryAccess());

            int iconX = xStart + col * ICON_SPACING;
            int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

            guiGraphics.renderItem(resultStack, iconX, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, resultStack, iconX, iconY);

            guiGraphics.renderItem(resultStack, iconX, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, resultStack, iconX, iconY);

            // Check if mouse is over this icon
            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                    mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                hoveredRecipeIndex = i; // mark this recipe as hovered

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(resultStack.getHoverName());

                if (hasShiftDown()) {
                    tooltip.add(Component.literal("SHIFT to craft as many as possible!").withStyle(ChatFormatting.RED));
                }

                List<ClientTooltipComponent> clientTooltips = tooltip.stream()
                        .map(component -> ClientTooltipComponent.create(component.getVisualOrderText()))
                        .toList();

                guiGraphics.renderTooltip(
                        font,
                        clientTooltips,
                        mouseX,
                        mouseY,
                        DefaultTooltipPositioner.INSTANCE,
                        null,
                        resultStack
                );
            }
        }
    }


    private void renderRecipeIngredients(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hoveredRecipeIndex == -1) return;

        if (clientRecipes.isEmpty() || hoveredRecipeIndex < 0 || hoveredRecipeIndex >= clientRecipes.size()) {
            return;
        }

        SimpleRecipeHolder recipeHolder = clientRecipes.get(hoveredRecipeIndex);
        CraftingRecipe recipe = recipeHolder.value();

        List<Ingredient> ingredients = recipe.placementInfo().ingredients();

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

        guiGraphics.pose().pushMatrix();

        //guiGraphics.pose().translate(0, 0, 0);

        int texWidth = 64;
        int texHeight = 64;


        guiGraphics.blit(CRAFTING_TOOLTIP_TEXTURE, tooltipX, tooltipY, 0, 0, 62, 62, 62, 62);

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
                            ItemStack[] matchingStacks = ing.items()
                                    .map(holder -> new ItemStack(holder.value()))
                                    .toArray(ItemStack[]::new);

                            if (matchingStacks.length > 0) {
                                // Try to find a match from player's inventory
                                assert Minecraft.getInstance().player != null;
                                for (ItemStack inventoryStack : Minecraft.getInstance().player.getInventory().getNonEquipmentItems()) {
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

        guiGraphics.pose().popMatrix();
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

            for (int i = 0; i < clientRecipes.size(); i++) {
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

                    ResourceLocation recipeId = clientRecipes.get(i).id();
                    assert Minecraft.getInstance().player != null;
                    boolean isShiftClick = hasShiftDown();
                    PacketDistributor.sendToServer(new SmartCraftingRecipeClickPayload(recipeId, isShiftClick));
                    return true; // Click handled
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset -= deltaY;

        int maxRows = (int) Math.ceil(clientRecipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        return true;
    }
}
