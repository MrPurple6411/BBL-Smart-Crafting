package com.benbenlaw.smartcrafting.networking.payload;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SmartCraftingRecipeClickPayload(ResourceLocation recipeID, boolean isShifting) implements CustomPacketPayload {

    public static final Type<SmartCraftingRecipeClickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "smart_crafting_recipe_click")
    );

    @Override
    public Type<SmartCraftingRecipeClickPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<FriendlyByteBuf, SmartCraftingRecipeClickPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            SmartCraftingRecipeClickPayload::recipeID,
            ByteBufCodecs.BOOL,
            SmartCraftingRecipeClickPayload::isShifting,
            SmartCraftingRecipeClickPayload::new
    );
}


