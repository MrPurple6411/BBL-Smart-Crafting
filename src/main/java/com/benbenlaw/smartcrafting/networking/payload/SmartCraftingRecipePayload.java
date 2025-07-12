package com.benbenlaw.smartcrafting.networking.payload;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SmartCraftingRecipePayload(List<ResourceLocation> recipeIds) implements CustomPacketPayload {

    public static final Type<SmartCraftingRecipePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "smart_crafting_recipe"));

    @Override
    public Type<SmartCraftingRecipePayload> type() {
        return TYPE;
    }

    // Define your own List<ResourceLocation> StreamCodec here
    public static final StreamCodec<FriendlyByteBuf, List<ResourceLocation>> RESOURCE_LOCATION_LIST_CODEC = StreamCodec.of(
                // Decoder: read size, then read each ResourceLocation
            (buf, list) -> {
                    buf.writeVarInt(list.size());
                    for (ResourceLocation rl : list) {
                        ResourceLocation.STREAM_CODEC.encode(buf, rl);
                    }
                },
                // Encoder: write size, then write each ResourceLocation
            buf -> {
                    int size = buf.readVarInt();
                    List<ResourceLocation> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        list.add(ResourceLocation.STREAM_CODEC.decode(buf));
                    }
                    return list;
                }
        );

    // Use the list codec in your composite codec
    public static final StreamCodec<FriendlyByteBuf, SmartCraftingRecipePayload> STREAM_CODEC = StreamCodec.composite(
            RESOURCE_LOCATION_LIST_CODEC,
            SmartCraftingRecipePayload::recipeIds,
            SmartCraftingRecipePayload::new
    );
}
