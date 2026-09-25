package com.funnyb.cwc.crafting;

import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.combat.behavior.BehaviorRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * @param twoHanded    是否双手武器。默认 false
 *                     —— **已废弃别名**：等价于 {@code mainHandUse = cwc:block_use} **加上**
 *                     {@code disableOffHand = true}（旧字段同时管"主手右键格挡"与"屏蔽副手"两件事）
 * @param offset       整体贴图偏移（像素，渲染时武器整体平移，改变握持位置）。null 表示无偏移
 * @param offhandAttack 刃型是否可副手右键攻击。默认 false
 *                     —— **已废弃别名**：等价于 {@code offHandUse = cwc:swing_use}
 * @param mainHandUse  该物品**在主手**时右键做什么。缺失 = 原版 use 路径照旧（放方块/交互）
 * @param offHandUse   该物品**在副手**时右键做什么。缺失 = 副手右键交给原版
 * @param attack       该物品的攻击方式——**在哪只手就按那只手读**（短刀在副手时用的就是它）。
 *                     缺失 = 暂走旧的 {@code combat.style} 那套（本批尚未迁移）
 * @param disableOffHand 是否屏蔽另一只手的动作（"双手武器占用右键"那条规则）。默认 false。
 *                     本层按 OR 生效（根 + 直接子件），**子树内部的不外传**
 */
public record PartTypeDef(Map<String, String> data, List<SlotDef> slots,
                          Position position, Double layer, CombatStyle combat, boolean twoHanded,
                          Position offset, boolean offhandAttack,
                          Optional<BehaviorDecl> mainHandUse, Optional<BehaviorDecl> offHandUse,
                          Optional<BehaviorDecl> attack, boolean disableOffHand) {

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
            BehaviorDecl.CODEC.optionalFieldOf("mainHandUse").forGetter(PartTypeDef::mainHandUse),
            BehaviorDecl.CODEC.optionalFieldOf("offHandUse").forGetter(PartTypeDef::offHandUse),
            BehaviorDecl.CODEC.optionalFieldOf("attack").forGetter(PartTypeDef::attack),
            Codec.BOOL.optionalFieldOf("disableOffHand", false).forGetter(PartTypeDef::disableOffHand)
    ).apply(instance, PartTypeDef::new));

    /**
     * 一个字段的行为声明——JSON 里 {@code "mainHandUse": { "behavior": …, "hud": … }} 那个对象。
     * <p>
     * <b>行为与 HUD 是两笔独立的声明</b>（{@code hud} 可省 = 不画）。两者都由**同一套解析机制**选出胜者
     * （见 {@code BehaviorResolver}：同样的层内优先级、同样的槽位 {@code priority} 表），所以两个行为
     * 可以共用一个 HUD、一个行为也可以换不同 HUD，不需要引入任何新机制。
     * <p>
     * <b>为什么 {@code hud} 不做"必填但可写 null"</b>：DFU 的 {@code JsonOps} 在条目层就把 {@code JsonNull}
     * 转成 Java null，标准 codec 区分不出"写了 null"与"没写这个键"；而注册表同步要过 NBT 一趟，
     * NBT 没有 null —— 那样服务端合法的数据到客户端会因为"缺键"解码失败。代价不值得。
     *
     * @param behavior 行为 id，必须在 {@link BehaviorRegistry} 注册过（未知 id 在**加载期**报错）
     * @param hud      HUD id；{@code empty} = 这个行为不画东西。只由客户端解释，所以这里不校验注册
     *                 （写错时客户端加载会 WARN）
     */
    public record BehaviorDecl(String behavior, Optional<String> hud) {
        public static final Codec<BehaviorDecl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("behavior").validate(BehaviorDecl::checkRegistered)
                        .forGetter(BehaviorDecl::behavior),
                Codec.STRING.optionalFieldOf("hud").forGetter(BehaviorDecl::hud)
        ).apply(instance, BehaviorDecl::new));

        /** 未知行为 id → 加载期报错（同未知 {@code parser} 的处理：只让这一条数据失败，不中断整个数据包） */
        private static DataResult<String> checkRegistered(String id) {
            if (BehaviorRegistry.isRegistered(id)) return DataResult.success(id);
            return DataResult.error(() -> "未知的行为 behavior=\"" + id + "\"；已注册的有 "
                    + BehaviorRegistry.registeredIds());
        }
    }

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
     * @param priority   **装在这个槽里的东西**在本层各字段上的话语权（键是 {@link BehaviorField} 的 JSON 名）。
     *                   没写某个字段 = 那个东西在该字段上不参与竞争；写了的必须 ≥1。
     *                   类型自己的声明视作优先级 0（本层默认），所以槽位给的 ≥1 会压过它。
     *                   **优先级和槽位绑定**：子树内部算出什么优先级都不外传，只以这里的数进入父层。
     */
    public record SlotDef(String name, Map<String, List<String>> constraint,
                          Map<String, Double> scale, Position position,
                          Map<String, Integer> priority) {

        public static final Codec<SlotDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(SlotDef::name),
                Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                        .optionalFieldOf("constraint", Map.of()).forGetter(SlotDef::constraint),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                        .optionalFieldOf("scale", Map.of()).forGetter(SlotDef::scale),
                // 缺省用非空的 (0,0)，不能传 null——DFU 的 Applicative 会对 null 组件 NPE。
                // 这里**现造**而不是引用外层的 NO_POSITION：引外层会让"先碰到 SlotDef"的那条路径触发
                // PartTypeDef 的静态初始化，而后者又要 SlotDef.CODEC（此刻还是 null）→ 成环 NPE。
                // 游戏里总是先碰 PartTypeDef.CODEC 所以撞不上，但离线检查器（tmp/codeccheck）会。
                Position.CODEC.optionalFieldOf("position", new Position(0, 0)).forGetter(SlotDef::position),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("priority", Map.of())
                        .validate(SlotDef::checkPriority).forGetter(SlotDef::priority)
        ).apply(instance, SlotDef::new));

        /**
         * 该槽位给"装进来的东西"在某个字段上的优先级。没写返回 0 = **不参与竞争**
         * （类型自己的声明也是 0，那是本层默认；所以 0 这个档刻意不开放给数据写，见 {@link #checkPriority}）。
         */
        public int priorityFor(BehaviorField field) {
            return priority == null ? 0 : priority.getOrDefault(field.jsonName(), 0);
        }

        /**
         * 键必须是三个合法字段名之一（把 {@code mainHandUse} 拼成 {@code mainhandUse} 会静默失效，
         * 所以这里明确报错）；值必须 ≥1——0 会与"本层默认 0"撞成并列，负数与"不写"完全等价、没有存在理由。
         */
        private static DataResult<Map<String, Integer>> checkPriority(Map<String, Integer> map) {
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (BehaviorField.byJsonName(entry.getKey()) == null) {
                    return DataResult.error(() -> "槽位 priority 里有未知字段名 \"" + entry.getKey()
                            + "\"（合法的是 " + BehaviorField.allJsonNames() + "）");
                }
                if (entry.getValue() == null || entry.getValue() < 1) {
                    return DataResult.error(() -> "槽位 priority 里的 \"" + entry.getKey() + "\" 必须 ≥1"
                            + "（不想让这个槽在该字段上说话，就别写这个键）");
                }
            }
            return DataResult.success(map);
        }

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
