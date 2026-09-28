package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端在右侧列表选中一个零件**类型**时发给服务端。
 * <p>
 * 载荷是**类型 id**（如 {@code coldweaponcraftsmanship:standard_blade}），不是具体零件 id：
 * 出哪个材质变体由服务端按 3×3 里的材料决定，客户端只负责说"我在做哪一类"。
 */
public record SelectPartPacket(String typeId) implements CustomPacketPayload {

    public static final Type<SelectPartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "select_part"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectPartPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectPartPacket::typeId, SelectPartPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
