package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.List;

import static com.benbenlaw.smartcrafting.screen.SmartCraftingScreen.FAVORITES_TAG;

public record SyncFavoriteRecipesClient(List<String> favorites) implements CustomPacketPayload {

    public static final Type<SyncFavoriteRecipesClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"favorite_recipes_sync_client"));


    public static final IPayloadHandler<SyncFavoriteRecipesClient> HANDLER = (pkt, ctx) -> {
        Player player = ctx.player();
        ListTag listTag = new ListTag();
        for (String fav : pkt.favorites()) {
            listTag.add(StringTag.valueOf(fav));
        }
        // Save into player persistent data
        player.getPersistentData().put(FAVORITES_TAG, listTag);
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFavoriteRecipesClient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncFavoriteRecipesClient::favorites,
            SyncFavoriteRecipesClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
