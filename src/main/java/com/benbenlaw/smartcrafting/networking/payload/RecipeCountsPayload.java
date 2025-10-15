package com.benbenlaw.smartcrafting.networking.payload;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record RecipeCountsPayload(List<ResourceLocation> recipeIds, List<Integer> cappedCounts, List<Integer> rawCounts, long inventoryVersion) implements CustomPacketPayload {

    public static final Type<RecipeCountsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "recipe_counts")
    );

    @Override
    public Type<RecipeCountsPayload> type() { return TYPE; }

    private static final StreamCodec<FriendlyByteBuf, List<ResourceLocation>> RL_LIST = StreamCodec.of(
            (buf, list) -> {
                buf.writeVarInt(list.size());
                for (ResourceLocation rl : list) ResourceLocation.STREAM_CODEC.encode(buf, rl);
            },
            buf -> {
                int size = buf.readVarInt();
                List<ResourceLocation> out = new ArrayList<>(size);
                for (int i=0;i<size;i++) out.add(ResourceLocation.STREAM_CODEC.decode(buf));
                return out;
            }
    );

    private static final StreamCodec<FriendlyByteBuf, List<Integer>> INT_LIST = StreamCodec.of(
            (buf, list) -> {
                buf.writeVarInt(list.size());
                for (Integer v : list) buf.writeVarInt(v);
            },
            buf -> {
                int size = buf.readVarInt();
                List<Integer> out = new ArrayList<>(size);
                for (int i=0;i<size;i++) out.add(buf.readVarInt());
                return out;
            }
    );

    public static final StreamCodec<FriendlyByteBuf, RecipeCountsPayload> STREAM_CODEC = StreamCodec.composite(
        RL_LIST, RecipeCountsPayload::recipeIds,
        INT_LIST, RecipeCountsPayload::cappedCounts,
        INT_LIST, RecipeCountsPayload::rawCounts,
        ByteBufCodecs.VAR_LONG, RecipeCountsPayload::inventoryVersion,
        RecipeCountsPayload::new
    );
}
