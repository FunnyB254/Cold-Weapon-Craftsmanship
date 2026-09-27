package com.funnyb.cwc.combat;

import com.funnyb.cwc.registry.CwcAttachments;
import com.funnyb.cwc.registry.CwcAttachments.MountOwnership;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * "这只坐骑是谁的"——**两端同源**的那一份答案。
 * <p>
 * 起因：横扫的友军过滤（{@link CwcCombat#isSweepFriendly}）要判"这只坐骑是我的 / 我队友的 / 别人的"，
 * 而这一题**客户端答不出来**——{@code AbstractHorse} 的 owner 只写在 NBT 里（见 {@link CwcAttachments}）。
 * 答不出来就会出现"打得到却不亮""亮了却打不到"这类指示器与服务端不一致的老毛病。
 *
 * <h2>做法：服务端对账、客户端读同步副本</h2>
 * 附件 {@code MOUNT_OWNERSHIP} 由服务端每 tick 与马自己的权威字段对账（{@link #onEntityTick}），
 * 值一变就写回并同步给追踪这只马的客户端。查的时候走 {@link #ownerOf} / {@link #ownerTeamMatches}：
 * 服务端现算（权威、且没有一 tick 的同步延迟），客户端读同步过来的那一份。
 * <p>
 * 没有选"两端都读附件"：那样服务端会多出一个"刚驯服、还没对账"的窗口，
 * 而结算恰好是服务端要做的事——判据宁可有延迟的一侧是图标，不要是结算。
 *
 * <h2>为什么用每 tick 对账，而不是盯住驯服那一刻</h2>
 * owner 的写入点不止一个：驯服（{@code AbstractHorse.tameWithName}）、从存档读回
 * （{@code readAdditionalSaveData}）、{@code /summon} 或 {@code /data merge} 带 Owner 标签。
 * 原版的事件也帮不上忙——{@code AnimalTameEvent} 只在 {@code TamableAnimal} 里发，**马不发**。
 * 对账把所有写入点一次覆盖，代价只是每 tick 一次 instanceof + 一次比较。
 */
public final class CwcOwnership {

    /**
     * 把服务端的权威归属对账进附件——不一致才写（写会触发同步包，所以别每 tick 都写）。
     * <p>
     * 只在服务端做：客户端那份是同步来的，反过来写会与服务端打架。
     * <p>
     * 队伍名也在对账范围内，所以**换队/退队/改队名**都会自己同步过去，不需要额外的钩子。
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (horse.level().isClientSide) return;
        MountOwnership actual = snapshot(horse);
        if (!horse.getData(CwcAttachments.MOUNT_OWNERSHIP.get()).equals(actual)) {
            horse.setData(CwcAttachments.MOUNT_OWNERSHIP.get(), actual);
        }
    }

    /**
     * 这只认主实体的主人 UUID；没有主人返回 null。
     * <p>
     * 只有 {@link AbstractHorse} 需要绕道附件——其余 {@code OwnableEntity}（狼/猫/鹦鹉等
     * {@code TamableAnimal}）的 owner 本来就是同步的，直接读原版字段即可。
     */
    public static UUID ownerOf(OwnableEntity ownable) {
        if (ownable instanceof AbstractHorse horse) {
            return ownershipOf(horse).owner().orElse(null);
        }
        return ownable.getOwnerUUID();
    }

    /**
     * 这只坐骑的主人**和我在同一队**吗——两端同源。
     * <p>
     * 比的是**队伍名**而不是"主人的玩家实体"：客户端只有附近那些玩家实体，队友骑马冲远了、
     * 马留在你身边时，UUID → 实体这一步在客户端会失败，于是"队友的马"会被漏判成可以扫
     * （指示器亮、服务端却不结算）。队伍名是同步过来的字符串，永远比得出来。
     * <p>
     * 无主人、主人没队伍、我没有队伍 → 都返回 false（"队友"的判定与
     * {@code player.isAlliedTo} 同口径：同一支计分板队伍）。
     */
    public static boolean ownerTeamMatches(Player player, AbstractHorse horse) {
        String ownerTeam = ownershipOf(horse).team().orElse(null);
        if (ownerTeam == null) return false;
        PlayerTeam myTeam = player.getTeam();
        return myTeam != null && myTeam.getName().equals(ownerTeam);
    }

    // —— 内部 ——

    /** 这只坐骑当前的归属：服务端现算，客户端读同步副本 */
    private static MountOwnership ownershipOf(AbstractHorse horse) {
        if (!horse.level().isClientSide) return snapshot(horse);
        return horse.getData(CwcAttachments.MOUNT_OWNERSHIP.get());
    }

    /** 服务端现算：主人 UUID + 主人此刻所在队伍的名字 */
    private static MountOwnership snapshot(AbstractHorse horse) {
        UUID owner = horse.getOwnerUUID();
        if (owner == null) return MountOwnership.NONE;
        MinecraftServer server = horse.getServer();
        if (server == null) return new MountOwnership(Optional.of(owner), Optional.empty());
        PlayerTeam team = teamOfOwner(server, owner);
        return new MountOwnership(Optional.of(owner),
                Optional.ofNullable(team == null ? null : team.getName()));
    }

    /**
     * 主人所在队伍——**主人离线也要答得出来**。
     * <p>
     * 在线时直接问那个玩家实体；离线时从用户名缓存（{@code GameProfileCache}）取名字，再去计分板查队伍
     * ——队伍成员表是存档里的数据，与人是否在线无关。两次都是内存查表。
     * <p>
     * 名字都查不到（没进过服的 UUID）就返回 null，这只坐骑按"没有队伍"处理——它是别人的马。
     * 不用 {@code Player.getTeam()}（那个要实体在场）就是为了避开"队友下线了、马还在"这个口子。
     */
    private static PlayerTeam teamOfOwner(MinecraftServer server, UUID owner) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        String name = online != null
                ? online.getScoreboardName()
                : server.getProfileCache().get(owner).map(GameProfile::getName).orElse(null);
        return name == null ? null : server.getScoreboard().getPlayersTeam(name);
    }
}
