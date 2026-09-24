package com.funnyb.cwc.crafting;

import com.funnyb.cwc.combat.behavior.BehaviorDecl;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 零件类型定义——datapack registry {@code coldweaponcraftsmanship:cwc/part_type} 的条目类型。
 * <p>
 * <b>没有 {@code id} 字段</b>：注册表条目的 id 由注册表键提供（文件名决定），codec 不该也无法自己写它。
 * 需要"id + 定义"两者时用 {@code PartRegistry.entries()}。
 * <p>
 * <b>类型只有一个数据形状</b>，所以这里没有像零件那样的 {@code "parser"} 分派——{@code data} 是自由的
 * 字符串键值对，第三方想加语义键直接加即可，不需要新 codec。（旧 JSON 里的 {@code "parser"}
 * 字段已随之删除。）
 *
 * @param data         键值对。**非空时为 part，空时为 handle_part**（{@link #role()} 的判据）
 * @param slots        槽位列表。空数组表示不能接收其他零件
 * @param position     该类型贴图上的安装点（子件安装点）。null 表示默认 (0,0)
 * @param layer        渲染优先级（整数/实数），越大越靠上；null 默认 0
 * @param combat       攻击特征（刃型声明攻击范围/击退加成/普攻方式）。null 表示默认无加成
 * @param twoHanded    是否双手武器（handle 底座占用主手右键格挡，屏蔽副手交互）。默认 false
 *                     —— **已废弃别名**：等价于一条 {@code {hand:"main", button:"use", behavior:"cwc:block", priority:100}}
 * @param offset       整体贴图偏移（像素，渲染时武器整体平移，改变握持位置）。null 表示无偏移
 * @param offhandAttack 刃型是否可副手右键攻击（放在副手时右键出刀）。默认 false
 *                     —— **已废弃别名**：等价于一条 {@code {hand:"off", button:"use", behavior:"cwc:swing", priority:100}}
 * @param behaviors    行为声明——"装了我就让某只手某个键位做某件事"。见 {@link BehaviorDecl}。
 *                     两个布尔字段与它是**别名关系**，在解析时折算（不在 codec 里折叠，否则 NBT 往返会丢）
 */
public record PartTypeDef(Map<String, String> data, List<SlotDef> slots,
                          Position position, Double layer, CombatStyle combat, boolean twoHanded,
                          Position offset, boolean offhandAttack, List<BehaviorDecl> behaviors) {

    /**
     * 缺省值——**用"非空的默认值"而不是 null**。
     * <p>
     * 这是被 DFU 逼出来的：`Applicative` 组装记录时会对每个组件调 `DataResult.Success.result()`，
     * 而它内部是 `Optional.of(值)`——**组件解出 null 就直接 NPE**。所以可空字段不能靠
     * `optionalFieldOf(name, null)` 表达。
     * <p>
     * 用默认值代替 null **语义完全等价**：原本 null 时各访问器返回的也正是这些值。
     */
    private static final Position NO_POSITION = new Position(0, 0);
    private static final CombatStyle NO_COMBAT = new CombatStyle(0, 0, "normal");

    /**
     * 持久化/网络编解码器。
     * <p>
     * 缺省值与旧的 GSON 解析（{@code DefaultParser}）语义保持一致：{@code data} 与 {@code slots} 缺失为空、
     * {@code position}/{@code offset} 缺失为 (0,0)、{@code layer} 缺失为 0、
     * {@code combat} 缺失为"无加成、普攻方式 normal"、布尔缺失为 false。
     * {@code slots[].constraint} 缺失→空表（等价于"全收"，与旧行为一致）；{@code scale} 缺失→空表。
     */
    public static final Codec<PartTypeDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("data", Map.of())
                    .forGetter(PartTypeDef::data),
            SlotDef.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(PartTypeDef::slots),
            Position.CODEC.optionalFieldOf("position", NO_POSITION).forGetter(PartTypeDef::position),
            Codec.DOUBLE.optionalFieldOf("layer", 0.0).forGetter(PartTypeDef::layer),
            CombatStyle.CODEC.optionalFieldOf("combat", NO_COMBAT).forGetter(PartTypeDef::combat),
            Codec.BOOL.optionalFieldOf("twoHanded", false).forGetter(PartTypeDef::twoHanded),
            Position.CODEC.optionalFieldOf("offset", NO_POSITION).forGetter(PartTypeDef::offset),
            Codec.BOOL.optionalFieldOf("offhandAttack", false).forGetter(PartTypeDef::offhandAttack),
            BehaviorDecl.CODEC.listOf().optionalFieldOf("behaviors", List.of()).forGetter(PartTypeDef::behaviors)
    ).apply(instance, PartTypeDef::new));

    public String role() {
        return data.isEmpty() ? "handle_part" : "part";
    }

    /**
     * 语言文件 key——{@code type.<命名空间>.<路径以 . 连接>}，例 {@code type.coldweaponcraftsmanship.standard_blade}。
     * 带命名空间是为了第三方扩展包不会撞 key（同 {@link PartDef#langKey}）。
     */
    public static String langKey(ResourceLocation id) {
        return "type." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** 类型贴图上的安装点 x，未声明按 0 */
    public int positionX() { return position.x(); }
    /** 类型贴图上的安装点 y，未声明按 0 */
    public int positionY() { return position.y(); }
    /** 渲染优先级，未声明默认 0 */
    public double layerValue() { return layer; }

    /** 攻击范围加成，未声明 combat 按 0 */
    public double combatReach() { return combat.reach(); }
    /** 击退加成，未声明 combat 按 0 */
    public double combatKnockback() { return combat.knockback(); }
    /** 普攻方式（刃型指定），未声明/未知按 NORMAL */
    public AttackStyle attackStyle() { return AttackStyle.from(combat.style()); }

    /** 整体贴图偏移 x（像素，1/16 格），未声明按 0 */
    public int offsetX() { return offset.x(); }
    /** 整体贴图偏移 y（像素，1/16 格），未声明按 0 */
    public int offsetY() { return offset.y(); }

    /**
     * 单个装配槽位。
     *
     * @param name       槽位的语言文件 key
     * @param constraint 匹配约束——键是**零件 data 里的语义键**（如 {@code type}/{@code mount}/{@code weight}），
     *                   值是该键允许的取值集合。空表 = 全收
     * @param scale      属性加权系数
     * @param position   槽位在父件贴图上的安装点。null 表示默认 (0,0)
     * @param behaviors  槽位**代装进来的零件**授予的行为声明（装了才能授予）。空表 = 不授予。
     *                   装在槽里的零件若声明同一行为且优先级为负，这份授予作废——负优先级的意义所在
     */
    public record SlotDef(String name, Map<String, List<String>> constraint,
                          Map<String, Double> scale, Position position, List<BehaviorDecl> behaviors) {

        public static final Codec<SlotDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(SlotDef::name),
                Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                        .optionalFieldOf("constraint", Map.of()).forGetter(SlotDef::constraint),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                        .optionalFieldOf("scale", Map.of()).forGetter(SlotDef::scale),
                // 缺省用非空的 (0,0)，不能传 null——DFU 的 Applicative 会对 null 组件 NPE
                Position.CODEC.optionalFieldOf("position", NO_POSITION).forGetter(SlotDef::position),
                BehaviorDecl.CODEC.listOf().optionalFieldOf("behaviors", List.of()).forGetter(SlotDef::behaviors)
        ).apply(instance, SlotDef::new));

        /**
         * 该槽位是否接受这个类型——**槽位约束匹配的唯一实现**（装配台的放入校验与类型图环检测共用）。
         * <p>
         * 空约束（或约束键的值为 null）= 全收；约束键在候选类型的 {@code data} 里缺失、或取值不在允许集合里
         * 即拒绝。注意匹配的是类型的**语义值**（如 {@code data.type = "attack"}），**不是类型 id**——
         * 所以第三方定义的同语义类型天然可以被现有槽位接受。
         */
        public boolean accepts(PartTypeDef candidate) {
            if (constraint == null || constraint.isEmpty()) return true;
            for (Map.Entry<String, List<String>> entry : constraint.entrySet()) {
                List<String> allowed = entry.getValue();
                if (allowed == null) continue;      // 值为 null 视为该键无约束
                String value = candidate.data().get(entry.getKey());
                if (value == null || !allowed.contains(value)) return false;
            }
            return true;
        }

        /**
         * 某属性的加权系数。JSON 未声明 scale 或未声明该属性时默认 1（全量计入）。
         */
        public double weight(String attribute) {
            return scale == null ? 1.0 : scale.getOrDefault(attribute, 1.0);
        }

        /** 槽位在父件贴图上的安装点 x，未声明按 0 */
        public int positionX() { return position.x(); }
        /** 槽位在父件贴图上的安装点 y，未声明按 0 */
        public int positionY() { return position.y(); }
    }

    /** 组装渲染偏移 / 安装点，零件 data 中的 {@code "position"} 字段 */
    public record Position(int x, int y) {
        public static final Codec<Position> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(Position::x),
                Codec.INT.fieldOf("y").forGetter(Position::y)
        ).apply(instance, Position::new));
    }

    /**
     * 攻击特征——刃型类型声明战斗加成，装配时写入武器属性。
     *
     * @param reach     攻击范围加成（写入 ENTITY_INTERACTION_RANGE，默认 3.0，+0.5 打得更远）
     * @param knockback 击退加成（写入 ATTACK_KNOCKBACK，默认 0）
     * @param style     普攻方式（"normal"/"sweep"/"critical"，由 {@link AttackStyle} 归一化）。null/未知按 normal
     */
    public record CombatStyle(double reach, double knockback, String style) {
        public static final Codec<CombatStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("reach", 0.0).forGetter(CombatStyle::reach),
                Codec.DOUBLE.optionalFieldOf("knockback", 0.0).forGetter(CombatStyle::knockback),
                // 缺省 "normal" 而不是 null——同 NO_POSITION 的理由（DFU 组件不能为 null），
                // 而且语义等价：AttackStyle.from(null) 本来就归一到 NORMAL
                Codec.STRING.optionalFieldOf("style", "normal").forGetter(CombatStyle::style)
        ).apply(instance, CombatStyle::new));
    }

    /**
     * 刃型普攻方式——普攻时按声明执行差异化效果：
     * <ul>
     *   <li>{@link #NORMAL} 普攻：单目标全额，稳定直击，无跳劈暴击</li>
     *   <li>{@link #SWEEP} 横扫：范围内全额伤害，挥出即范围攻击（空挥也算），不依赖主目标命中</li>
     *   <li>{@link #CRITICAL} 暴击：单目标，保留原版跳劈暴击（空中下落 ×1.5 + 红粒子 + 音效）</li>
     * </ul>
     */
    public enum AttackStyle {
        NORMAL, SWEEP, CRITICAL;

        /** 字符串 → 枚举，未知值按 NORMAL（forward-compat，JSON 新增类型不炸） */
        public static AttackStyle from(String style) {
            if (style == null) return NORMAL;
            return switch (style) {
                case "sweep" -> SWEEP;
                case "critical" -> CRITICAL;
                default -> NORMAL;
            };
        }
    }
}
