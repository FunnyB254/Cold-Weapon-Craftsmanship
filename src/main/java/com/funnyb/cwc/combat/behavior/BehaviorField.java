package com.funnyb.cwc.combat.behavior;

import net.minecraft.world.InteractionHand;

/**
 * 行为的三个字段——JSON 里的三个具名键，也是槽位 {@code priority} 表的三个键。
 * <p>
 * 每个字段与"什么时候用它"一一对应（作者 2026-09-25 定）：
 * <ul>
 *   <li>{@link #MAIN_HAND_USE}：该物品**在主手**时右键调用</li>
 *   <li>{@link #OFF_HAND_USE}：该物品**在副手**时右键调用</li>
 *   <li>{@link #ATTACK}：该物品**在哪只手就按那只手**的攻击方式读（今天的短刀在副手，用的就是它）</li>
 * </ul>
 * 做成枚举而不是散落的字符串，是为了让"槽位优先级表的键"与"类型上的字段名"共享同一个来源——
 * 把 {@code mainHandUse} 拼成 {@code mainhandUse} 会被明确拒绝，而不是静默失效（那正是这类
 * "按字段配置"最容易踩的坑）。
 */
public enum BehaviorField {
    MAIN_HAND_USE("mainHandUse"),
    OFF_HAND_USE("offHandUse"),
    ATTACK("attack");

    private final String jsonName;

    BehaviorField(String jsonName) {
        this.jsonName = jsonName;
    }

    /** JSON 里的键名 */
    public String jsonName() {
        return jsonName;
    }

    /** 按 JSON 键名查；未知返回 null（调用方负责报错，消息里应列出全部合法键） */
    public static BehaviorField byJsonName(String name) {
        for (BehaviorField field : values()) {
            if (field.jsonName.equals(name)) return field;
        }
        return null;
    }

    /**
     * 右键路径的字段——**按手分派**（作者定的"只有在主手时右键会调用，其他亦然"）。
     * <p>
     * 攻击字段不在这里：它跟手无关，在哪只手就读同一份（"短刀在副手时用的就是它的 attack"）。
     */
    public static BehaviorField useField(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_HAND_USE : OFF_HAND_USE;
    }

    /** 全部合法键名，用于报错消息 */
    public static String allJsonNames() {
        StringBuilder sb = new StringBuilder();
        for (BehaviorField field : values()) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(field.jsonName);
        }
        return sb.toString();
    }
}
