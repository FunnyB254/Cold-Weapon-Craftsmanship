package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * CWC 的实体附件——**补原版没同步的东西**。
 *
 * <h2>为什么需要它：坐骑的 owner 只写在 NBT 里</h2>
 * {@code TamableAnimal}（狼/猫/鹦鹉）的 owner 走 {@code DATA_OWNERUUID_ID} **同步**给客户端；
 * 而 {@code AbstractHorse}（马/驴/骡/羊驼/骆驼）的 owner 是一个普通字段（{@code AbstractHorse.java:134}），
 * 只进出存档 NBT。它唯一的同步数据 {@code DATA_ID_FLAGS} 里只有"**被驯服**"这一位，**没有"是谁的"**。
 * <p>
 * 于是"这只坐骑是不是我的"在客户端答不出来，而横扫的友军过滤（{@code CwcCombat.isSweepFriendly}）
 * 两端都要答这一题——答不出来就会出现"指示器不亮/乱亮、与服务端结算不一致"。
 * 这里把 owner 送上客户端，两端就同源了。先例上不吃亏：原版本来就把狼/猫的 owner 同步给附近所有客户端。
 *
 * <h2>不序列化</h2>
 * 附件**刻意不写进存档**：服务端每 tick 对账（见 {@code CwcOwnership}），权威值就在马自己身上，
 * 再存一份会多出一份可能漂移的副本。不序列化的代价只是重载后第一 tick 之前为空——没人看得见。
 */
public final class CwcAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ColdWeaponCraftsmanship.MODID);

    /**
     * 坐骑的 owner UUID（没有主人时为空）——**服务端写、客户端读**。
     * <p>
     * 服务端那份由 {@code CwcOwnership} 每 tick 与 {@code AbstractHorse.getOwnerUUID()} 对账，
     * 所以它永远等于权威值；客户端拿到的就是同步过来的那一份。
     * <p>
     * 类型写成 {@link Supplier}（而不是 {@code DeferredHolder}）是因为注册表的元素类型本身带通配符
     * （{@code AttachmentType<?>}），写成 holder 要把两个类型参数各写一遍，读起来更绕。
     */
    public static final Supplier<AttachmentType<Optional<UUID>>> MOUNT_OWNER = ATTACHMENTS.register(
            "mount_owner",
            () -> AttachmentType.builder(() -> Optional.<UUID>empty())
                    .sync(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC))
                    .build());

    private CwcAttachments() {}

    /** 在模组构造器里调（与 {@code CwcItems.init} 同一批） */
    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
