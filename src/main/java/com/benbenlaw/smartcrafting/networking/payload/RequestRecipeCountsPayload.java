package com.benbenlaw.smartcrafting.networking.payload;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record RequestRecipeCountsPayload(List<ResourceLocation> recipeIds, long clientInventoryVersion) implements CustomPacketPayload {

    public static final Type<RequestRecipeCountsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID, "request_recipe_counts")
    );

    @Override
    public Type<RequestRecipeCountsPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, List<ResourceLocation>> RL_LIST = StreamCodec.of(
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

    public static final StreamCodec<FriendlyByteBuf, RequestRecipeCountsPayload> STREAM_CODEC = StreamCodec.composite(
        RL_LIST, RequestRecipeCountsPayload::recipeIds,
        ByteBufCodecs.VAR_LONG, RequestRecipeCountsPayload::clientInventoryVersion,
        RequestRecipeCountsPayload::new
    );
}
