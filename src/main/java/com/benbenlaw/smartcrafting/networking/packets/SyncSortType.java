package com.benbenlaw.smartcrafting.networking.packets;

import com.benbenlaw.smartcrafting.SmartCrafting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record SyncSortType(String sortType) implements CustomPacketPayload {

    public static final Type<SyncSortType> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SmartCrafting.MOD_ID,"sync_sort_type"));


    public static final IPayloadHandler<SyncSortType> HANDLER = (pkt, ctx) -> {
        ServerPlayer player = (ServerPlayer) ctx.player();
        player.getPersistentData().putString("smart_crafting_sort_type", pkt.sortType);
        player.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.0F);
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSortType> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncSortType::sortType,
            SyncSortType::new
    );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


}
