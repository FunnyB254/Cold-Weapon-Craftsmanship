package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
 * <h2>为什么需要它：坐骑的归属只写在服务端</h2>
 * {@code TamableAnimal}（狼/猫/鹦鹉）的 owner 走 {@code DATA_OWNERUUID_ID} **同步**给客户端；
 * 而 {@code AbstractHorse}（马/驴/骡/羊驼/骆驼）的 owner 是一个普通字段（{@code AbstractHorse.java:134}），
 * 只进出存档 NBT。它唯一的同步数据 {@code DATA_ID_FLAGS} 里只有"**被驯服**"这一位，**没有"是谁的"**。
 * <p>
 * 而横扫的友军过滤（{@code CwcCombat.isSweepFriendly}）要判"这只坐骑是我的 / 我队友的 / 别人的"，
 * 这一题**两端都要答**（服务端结算、客户端画指示器）。答不出来就是"指示器与服务端不一致"那类老毛病。
 * 先例上不吃亏：原版本来就把狼/猫的 owner 同步给附近所有客户端。
 *
 * <h2>不序列化</h2>
 * 附件**刻意不写进存档**：服务端每 tick 对账（见 {@code CwcOwnership}），权威值就在马自己身上，
 * 再存一份会多出一份可能漂移的副本。不序列化的代价只是重载后第一 tick 之前为空——没人看得见。
 */
public final class CwcAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ColdWeaponCraftsmanship.MODID);

    /**
     * 坐骑的归属——**服务端写、客户端读**。
     * <p>
     * 服务端那份由 {@code CwcOwnership} 每 tick 现算一遍并与附件对账，所以它永远等于权威值；
     * 客户端拿到的就是同步过来的那一份。
     * <p>
     * 类型写成 {@link Supplier}（而不是 {@code DeferredHolder}）是因为注册表的元素类型本身带通配符
     * （{@code AttachmentType<?>}），写成 holder 要把两个类型参数各写一遍，读起来更绕。
     */
    public static final Supplier<AttachmentType<MountOwnership>> MOUNT_OWNERSHIP = ATTACHMENTS.register(
            "mount_ownership",
            () -> AttachmentType.builder(() -> MountOwnership.NONE)
                    .sync(MountOwnership.STREAM_CODEC)
                    .build());

    /**
     * 一只坐骑的归属，附件 {@link #MOUNT_OWNERSHIP} 的载荷。
     * <p>
     * <b>为什么连队伍名一起同步</b>：判"队友的坐骑"需要知道主人的队伍，而客户端**只有在主人那个玩家实体
     * 就在附近时**才解析得出主人是谁（UUID → 实体这一步在客户端靠本地实体表）。队友骑马冲在前面、马留在
     * 你身边时，客户端就答不出来，于是"队友的马"会被漏判成可以扫（指示器亮、服务端却不结算）。
     * 队伍名是同步过来的**字符串**，永远比得出来，而且服务端读的是同一份——两端结论必然一致。
     *
     * @param owner 主人 UUID
     * @param team  主人所在队伍的名字；无主人 / 主人没队伍 / 查不到主人是谁时为空。
     *              **主人离线不影响**：服务端从用户名缓存取名字再查计分板，见 {@code CwcOwnership}
     */
    public record MountOwnership(Optional<UUID> owner, Optional<String> team) {

        /** 无主（野马、尚未驯服） */
        public static final MountOwnership NONE = new MountOwnership(Optional.empty(), Optional.empty());

        public static final StreamCodec<ByteBuf, MountOwnership> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), MountOwnership::owner,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), MountOwnership::team,
                MountOwnership::new);
    }

    private CwcAttachments() {}

    /** 在模组构造器里调（与 {@code CwcItems.init} 同一批） */
    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
