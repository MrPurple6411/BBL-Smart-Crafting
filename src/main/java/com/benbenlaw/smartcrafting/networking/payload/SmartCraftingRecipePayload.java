package com.benbenlaw.smartcrafting.networking.payload;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SmartCraftingRecipePayload(List<ResourceLocation> recipeIds, List<Integer> cappedCrafts, List<Integer> rawCrafts, long inventoryVersion) implements CustomPacketPayload {

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
    public static final StreamCodec<FriendlyByteBuf, List<Integer>> INT_LIST_CODEC = StreamCodec.of(
            (buf, list) -> {
                buf.writeVarInt(list.size());
                for (Integer v : list) buf.writeVarInt(v);
            },
            buf -> {
                int size = buf.readVarInt();
                List<Integer> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(buf.readVarInt());
                return list;
            }
    );

    public static final StreamCodec<FriendlyByteBuf, SmartCraftingRecipePayload> STREAM_CODEC = StreamCodec.composite(
        RESOURCE_LOCATION_LIST_CODEC, SmartCraftingRecipePayload::recipeIds,
        INT_LIST_CODEC, SmartCraftingRecipePayload::cappedCrafts,
        INT_LIST_CODEC, SmartCraftingRecipePayload::rawCrafts,
        ByteBufCodecs.VAR_LONG, SmartCraftingRecipePayload::inventoryVersion,
        SmartCraftingRecipePayload::new
    );
}
