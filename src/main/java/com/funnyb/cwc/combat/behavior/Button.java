package com.funnyb.cwc.combat.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * 手部的两个键位——{@link #USE} 右键、{@link #ATTACK} 左键普攻。
 * <p>
 * 这是行为声明的分派维度之一（另一个是 {@link net.minecraft.world.InteractionHand}）。做成枚举而不是布尔，
 * 是为了左键普攻也并进同一套时**不用改接口形状**——本批只迁移右键，但接口按"（手 × 键位）"设计。
 * <p>
 * JSON 取值 {@code "use"} / {@code "attack"}：**未知值报错而不是静默归一到某个默认**——这是数据包作者
 * 写错了要立刻看见的地方（同未知 {@code parser} 的处理方式）。
 */
public enum Button {
    USE, ATTACK;

    public static final Codec<Button> CODEC = Codec.STRING.comapFlatMap(
            name -> switch (name) {
                case "use" -> DataResult.success(USE);
                case "attack" -> DataResult.success(ATTACK);
                default -> DataResult.error(() -> "未知的键位 button=\"" + name + "\"（应为 use / attack）");
            },
            button -> button == USE ? "use" : "attack");
}
