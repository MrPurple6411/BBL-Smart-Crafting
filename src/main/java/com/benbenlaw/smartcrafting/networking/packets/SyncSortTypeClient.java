package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record SyncSortTypeClient(String sortType) implements CustomPacketPayload {

    public static final Type<SyncSortTypeClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"sync_sort_type_clieny"));

    public static final IPayloadHandler<SyncSortTypeClient> HANDLER = (pkt, ctx) -> {
        Player player = ctx.player();
        player.getPersistentData().putString("smart_crafting_sort_type", pkt.sortType);
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSortTypeClient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncSortTypeClient::sortType,
            SyncSortTypeClient::new
    );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
