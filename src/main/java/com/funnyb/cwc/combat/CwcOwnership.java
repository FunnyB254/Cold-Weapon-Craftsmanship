package com.funnyb.cwc.combat;

import com.funnyb.cwc.registry.CwcAttachments;
import com.funnyb.cwc.registry.CwcAttachments.Ownership;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * "这只宠物/坐骑是谁的"——**两端同源**的那一份答案。
 * <p>
 * 起因：横扫的友军过滤（{@link CwcCombat#isSweepFriendly}）要判"这只认主实体是我的 / 我队友的 / 别人的"，
 * 而这一题**两端都要答**（服务端结算、客户端画指示器），原版给的材料却不够
 * （马的主人不同步、主人的队伍谁也不同步），详见 {@link CwcAttachments}。
 *
 * <h2>做法：服务端对账、客户端读同步副本</h2>
 * 附件 {@code OWNERSHIP} 由服务端每 tick 与实体的权威状态对账（{@link #onEntityTick}），
 * 值一变就写回并同步给追踪这只实体的客户端。查的时候走 {@link #isAllyOwned}：
 * 服务端现算（权威、且没有一 tick 的同步延迟），客户端读同步过来的那一份。
 * <p>
 * 没有选"两端都读附件"：那样服务端会多出一个"刚驯服、还没对账"的窗口，
 * 而结算恰好是服务端要做的事——判据宁可有延迟的一侧是图标，不要是结算。
 *
 * <h2>为什么用每 tick 对账，而不是盯住驯服那一刻</h2>
 * 归属的写入点不止一个：驯服（{@code TamableAnimal.tame} / {@code AbstractHorse.tameWithName}）、
 * 从存档读回、{@code /summon} 或 {@code /data merge} 带 Owner 标签，以及**换队**（队伍名那一项）。
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
        Entity entity = event.getEntity();
        if (!(entity instanceof OwnableEntity ownable)) return;
        if (entity.level().isClientSide) return;
        Ownership actual = snapshot(entity, ownable);
        if (!entity.getData(CwcAttachments.OWNERSHIP.get()).equals(actual)) {
            entity.setData(CwcAttachments.OWNERSHIP.get(), actual);
        }
    }

    /**
     * 这只认主实体**是不是我的、或我队友的**——横扫的友军过滤用，两端同源。
     * <p>
     * "队友"与 {@code player.isAlliedTo} 同口径（同一支计分板队伍）；比的是**队伍名**而不是"主人的玩家
     * 实体"：客户端只有附近那些玩家实体，队友跑远了就解析不出主人是谁，于是"队友的东西"会被漏判成
     * 可以扫（指示器亮、服务端却不结算）。队伍名是同步过来的字符串，永远比得出来。
     * <p>
     * 无主（野生）、我没有队伍、主人没队伍、查不到主人是谁 → 一律 false。
     */
    public static boolean isAllyOwned(Player player, Entity target) {
        if (!(target instanceof OwnableEntity ownable)) return false;
        Ownership ownership = ownershipOf(target, ownable);
        if (ownership.owner().isEmpty()) return false;
        if (ownership.owner().get().equals(player.getUUID())) return true;
        return ownership.team().isPresent() && sameTeam(player, ownership.team().get());
    }

    // —— 内部 ——

    /** 我在这支队伍里吗 */
    private static boolean sameTeam(Player player, String teamName) {
        PlayerTeam myTeam = player.getTeam();
        return myTeam != null && myTeam.getName().equals(teamName);
    }

    /**
     * 这只实体的归属：服务端现算，客户端读同步副本。
     * <p>
     * 两个参数看着冗余，其实是绕开一次强制转换——{@code OwnableEntity} 不 extends {@code Entity}，
     * 而 {@code level()}/{@code getServer()} 要 {@code Entity} 才有。调用方本来就两样都拿着。
     */
    private static Ownership ownershipOf(Entity holder, OwnableEntity ownable) {
        if (!holder.level().isClientSide) return snapshot(holder, ownable);
        return holder.getData(CwcAttachments.OWNERSHIP.get());
    }

    /** 服务端现算：主人 UUID + 主人此刻所在队伍的名字 */
    private static Ownership snapshot(Entity holder, OwnableEntity ownable) {
        UUID owner = ownable.getOwnerUUID();
        if (owner == null) return Ownership.NONE;
        MinecraftServer server = holder.getServer();
        if (server == null) return new Ownership(Optional.of(owner), Optional.empty());
        PlayerTeam team = teamOfOwner(server, owner);
        return new Ownership(Optional.of(owner),
                Optional.ofNullable(team == null ? null : team.getName()));
    }

    /**
     * 主人所在队伍——**主人离线也要答得出来**。
     * <p>
     * 在线时直接问那个玩家实体；离线时从用户名缓存（{@code GameProfileCache}）取名字，再去计分板查队伍
     * ——队伍成员表是存档里的数据，与人是否在线无关。两次都是内存查表。
     * <p>
     * 名字都查不到（没进过服的 UUID）就返回 null，这只实体按"没有队伍"处理——它是别人的。
     * 不用 {@code Player.getTeam()}（那个要实体在场）就是为了避开"队友下线了、宠物还留在原地"这个口子。
     */
    private static PlayerTeam teamOfOwner(MinecraftServer server, UUID owner) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        String name = online != null
                ? online.getScoreboardName()
                : server.getProfileCache().get(owner).map(GameProfile::getName).orElse(null);
        return name == null ? null : server.getScoreboard().getPlayersTeam(name);
    }
}
