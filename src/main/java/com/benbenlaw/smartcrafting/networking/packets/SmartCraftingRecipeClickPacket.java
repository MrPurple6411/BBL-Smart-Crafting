package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import com.benbenlaw.smartcrafting.screen.SmartCraftingMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SmartCraftingRecipeClickPacket() {

    public static final SmartCraftingRecipeClickPacket INSTANCE = new SmartCraftingRecipeClickPacket();

    public static SmartCraftingRecipeClickPacket get() {
        return INSTANCE;
    }

    public void handle(final SmartCraftingRecipeClickPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        System.out.println(payload.isShifting());

        if (player.containerMenu instanceof SmartCraftingMenu menu) {
            menu.craftRecipeById(payload.recipeID(), payload.isShifting());
        }
    }
}

