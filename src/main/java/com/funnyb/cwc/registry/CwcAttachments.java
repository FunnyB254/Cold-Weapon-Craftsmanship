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
 * <h2>为什么需要它：认主实体的"归属"只写在服务端</h2>
 * 判"这只宠物/坐骑是不是我的、是不是我队友的"（横扫的友军过滤，{@code CwcCombat.isSweepFriendly}）
 * 需要两样东西，原版**都没有完整同步到客户端**：
 * <ul>
 *   <li><b>主人 UUID</b>：{@code TamableAnimal}（狼/猫/鹦鹉）走 {@code DATA_OWNERUUID_ID} 同步；
 *       但 {@code AbstractHorse}（马/驴/骡/羊驼/骆驼）的 owner 是普通字段（{@code AbstractHorse.java:134}），
 *       只进出存档 NBT——它唯一的同步数据 {@code DATA_ID_FLAGS} 里只有"被驯服"这一位，没有"是谁的"。</li>
 *   <li><b>主人所在队伍</b>：原版从来不把这东西同步给别的客户端；而客户端**只能解析出附近那些玩家实体**，
 *       队友骑马跑远、把马留在你身边时，光有主人的 UUID 也查不到他的队伍。</li>
 * </ul>
 * 这两样缺一个，客户端就答不出"能不能顺手扫它"，于是变成"指示器与服务端不一致"那类老毛病。
 *
 * <h2>不序列化</h2>
 * 附件**刻意不写进存档**：服务端每 tick 对账（见 {@code CwcOwnership}），权威值就在实体自己身上，
 * 再存一份会多出一份可能漂移的副本。不序列化的代价只是重载后第一 tick 之前为空——没人看得见。
 */
public final class CwcAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ColdWeaponCraftsmanship.MODID);

    /**
     * 一只**认主实体**的归属——**服务端写、客户端读**。
     * <p>
     * 服务端那份由 {@code CwcOwnership} 每 tick 现算一遍并与附件对账，所以它永远等于权威值；
     * 客户端拿到的就是同步过来的那一份。
     * <p>
     * 类型写成 {@link Supplier}（而不是 {@code DeferredHolder}）是因为注册表的元素类型本身带通配符
     * （{@code AttachmentType<?>}），写成 holder 要把两个类型参数各写一遍，读起来更绕。
     */
    public static final Supplier<AttachmentType<Ownership>> OWNERSHIP = ATTACHMENTS.register(
            "ownership",
            () -> AttachmentType.builder(() -> Ownership.NONE)
                    .sync(Ownership.STREAM_CODEC)
                    .build());

    /**
     * 一只认主实体的归属，附件 {@link #OWNERSHIP} 的载荷。
     * <p>
     * <b>为什么连队伍名一起同步</b>：判"队友的宠物/坐骑"需要知道主人的队伍，而客户端只有在主人那个
     * 玩家实体**就在附近**时才解析得出主人是谁（UUID → 实体这一步靠本地实体表）。队友冲在前面、宠物留在
     * 你身边时客户端就答不出来，于是它会被漏判成可以扫（指示器亮、服务端却不结算）。队伍名是同步过来的
     * **字符串**，永远比得出来，而且服务端读的是同一份——两端结论必然一致。
     * <p>
     * 狼/猫这类本来就有同步 owner 的实体也走同一条路（它们的 owner 那一项与原版重复，几字节，值不变就不发）：
     * 与其让"哪一族同步什么"散在两处，不如所有认主实体共用一套。
     *
     * @param owner 主人 UUID
     * @param team  主人所在队伍的名字；无主人 / 主人没队伍 / 查不到主人是谁时为空。
     *              **主人离线不影响**：服务端从用户名缓存取名字再查计分板，见 {@code CwcOwnership}
     */
    public record Ownership(Optional<UUID> owner, Optional<String> team) {

        /** 无主（野生、尚未驯服） */
        public static final Ownership NONE = new Ownership(Optional.empty(), Optional.empty());

        public static final StreamCodec<ByteBuf, Ownership> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Ownership::owner,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), Ownership::team,
                Ownership::new);
    }

    private CwcAttachments() {}

    /** 在模组构造器里调（与 {@code CwcItems.init} 同一批） */
    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
