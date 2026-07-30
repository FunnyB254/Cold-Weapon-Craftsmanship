package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：请求打开制造界面。
 * 无数据负载，仅作为信号触发服务端打开 CraftingMenu。
 */
public record OpenCraftingPacket() implements CustomPacketPayload {

    /** 网络包类型标识，NeoForge 用此 ID 路由到对应处理器 */
    public static final Type<OpenCraftingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "open_crafting"));

    /** 编解码器：该包无负载，始终序列化为空 */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftingPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenCraftingPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
