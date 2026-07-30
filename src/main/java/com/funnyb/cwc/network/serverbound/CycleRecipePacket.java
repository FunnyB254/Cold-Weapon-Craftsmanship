package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：请求切换到下一个配方。
 * 服务端收到后轮询配方列表，更新输入槽幽灵物品并重新检查库存。
 */
public record CycleRecipePacket() implements CustomPacketPayload {

    public static final Type<CycleRecipePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "cycle_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleRecipePacket> STREAM_CODEC =
            StreamCodec.unit(new CycleRecipePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
