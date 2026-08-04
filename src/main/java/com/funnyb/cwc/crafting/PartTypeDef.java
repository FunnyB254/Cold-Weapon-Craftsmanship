package com.funnyb.cwc.crafting;

import java.util.List;
import java.util.Map;

/**
 * 零件类型定义——由 BaseParser.parseType 解析。
 *
 * @param id       类型标识，如 "cwc.standard_blade"
 * @param data     键值对。非空时为 part，空时为 handle_part
 * @param slots    槽位列表。空数组表示不能接收其他零件
 * @param position 该类型贴图上的安装点（子件安装点）。null 表示默认 (0,0)
 * @param layer     渲染优先级（整数/实数），越大越靠上；null 默认 0
 * @param combat    攻击特征（刃型声明攻击范围/击退加成）。null 表示默认无加成
 * @param twoHanded 是否双手武器（handle 底座占用主手右键格挡，屏蔽副手交互）。默认 false
 */
public record PartTypeDef(String id, Map<String, String> data, List<SlotDef> slots,
                          Position position, Double layer, CombatStyle combat, boolean twoHanded) {

    public String role() {
        return data.isEmpty() ? "handle_part" : "part";
    }

    /** 类型贴图上的安装点 x，未声明按 0 */
    public int positionX() { return position != null ? position.x() : 0; }
    /** 类型贴图上的安装点 y，未声明按 0 */
    public int positionY() { return position != null ? position.y() : 0; }
    /** 渲染优先级，未声明默认 0 */
    public double layerValue() { return layer != null ? layer : 0.0; }

    /** 攻击范围加成，未声明 combat 按 0 */
    public double combatReach() { return combat != null ? combat.reach() : 0.0; }
    /** 击退加成，未声明 combat 按 0 */
    public double combatKnockback() { return combat != null ? combat.knockback() : 0.0; }

    /**
     * 单个装配槽位。
     * @param name       槽位的语言文件 key
     * @param constraint 匹配约束
     * @param scale      属性加权系数
     * @param position   槽位在父件贴图上的安装点。null 表示默认 (0,0)
     */
    public record SlotDef(String name, Map<String, java.util.List<String>> constraint,
                          Map<String, Double> scale, Position position) {

        /**
         * 某属性的加权系数。JSON 未声明 scale 或未声明该属性时默认 1（全量计入）。
         */
        public double weight(String attribute) {
            return scale == null ? 1.0 : scale.getOrDefault(attribute, 1.0);
        }

        /** 槽位在父件贴图上的安装点 x，未声明按 0 */
        public int positionX() { return position != null ? position.x() : 0; }
        /** 槽位在父件贴图上的安装点 y，未声明按 0 */
        public int positionY() { return position != null ? position.y() : 0; }
    }

    /** 组装渲染偏移，零件 data 中的 "position" 字段 */
    public record Position(int x, int y) {}

    /**
     * 攻击特征——刃型类型声明战斗加成，装配时写入武器属性。
     *
     * @param reach     攻击范围加成（写入 ENTITY_INTERACTION_RANGE，默认 3.0，+0.5 打得更远）
     * @param knockback 击退加成（写入 ATTACK_KNOCKBACK，默认 0）
     */
    public record CombatStyle(double reach, double knockback) {}
}
