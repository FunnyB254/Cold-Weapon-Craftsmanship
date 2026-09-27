package com.funnyb.cwc.combat;

import com.funnyb.cwc.registry.CwcAttachments;

import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * "这只坐骑是谁的"——**两端同源**的那一份答案。
 * <p>
 * 起因：横扫的友军过滤（{@link CwcCombat#isSweepFriendly}）要判"是不是我的宠物/坐骑"，而这一题
 * **客户端答不出来**——{@code AbstractHorse} 的 owner 只写在 NBT 里（见 {@link CwcAttachments}）。
 * 客户端答不出来，就会出现"打得到却不亮""亮了却打不到"这类指示器与服务端不一致的老毛病。
 *
 * <h2>做法：服务端对账、客户端读同步副本</h2>
 * 附件 {@code MOUNT_OWNER} 由服务端每 tick 与马自己的权威字段对账（{@link #onEntityTick}），
 * 值一变就写回并同步给追踪这只马的客户端。查的时候走 {@link #ownerOf}：
 * <ul>
 *   <li><b>服务端</b>——读原版字段（权威，且没有一 tick 的同步延迟）；</li>
 *   <li><b>客户端</b>——读同步过来的那一份（原版字段在这侧恒为 null）。</li>
 * </ul>
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
     * 把服务端的权威 owner 对账进附件——不一致才写（写会触发同步包，所以别每 tick 都写）。
     * <p>
     * 只在服务端做：客户端那份是同步来的，反过来写会与服务端打架。
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (horse.level().isClientSide) return;
        Optional<UUID> synced = horse.getData(CwcAttachments.MOUNT_OWNER.get());
        if (!Objects.equals(synced.orElse(null), horse.getOwnerUUID())) {
            horse.setData(CwcAttachments.MOUNT_OWNER.get(), Optional.ofNullable(horse.getOwnerUUID()));
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
            if (!horse.level().isClientSide) return horse.getOwnerUUID();
            return horse.getData(CwcAttachments.MOUNT_OWNER.get()).orElse(null);
        }
        return ownable.getOwnerUUID();
    }
}
