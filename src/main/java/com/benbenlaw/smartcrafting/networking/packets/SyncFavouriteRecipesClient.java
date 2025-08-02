package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.ArrayList;
import java.util.List;

import static com.benbenlaw.smartcrafting.screen.SmartCraftingScreen.FAVORITES_TAG;

public record SyncFavouriteRecipesClient(List<String> favourites) implements CustomPacketPayload {

    public static final Type<SyncFavouriteRecipesClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"favourite_recipes_sync_client"));


    public static final IPayloadHandler<SyncFavouriteRecipesClient> HANDLER = (pkt, ctx) -> {
        Player player = ctx.player();
        ListTag listTag = new ListTag();
        for (String fav : pkt.favourites()) {
            listTag.add(StringTag.valueOf(fav));
        }
        // Save into player persistent data
        player.getPersistentData().put(FAVORITES_TAG, listTag);
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFavouriteRecipesClient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncFavouriteRecipesClient::favourites,
            SyncFavouriteRecipesClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
