package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.registry.CwcDataComponents;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 装配树——**一次遍历产出全部派生量**。
 * <p>
 * 取代原先四份各自为政的递归：{@code WeaponStats.accumulate}（属性）、{@code CwcWeapon.hasOffhandAttack}
 * （能否副手出刀）、{@code CwcWeapon.findAttackStyle}（普攻方式）、{@code AssembledWeaponRenderer.collectChildren}
 * （渲染项）。它们各自遍历同一棵树，却对"子件顺序"看法不同——前三者迭代 {@code children.entrySet()}
 * （HashMap 序），第四个迭代 {@code type.slots()}（声明序）。目前每种类型最多一个可装零件的槽所以看不出差别，
 * 但**只要出现第二个同类槽位（比如"副刃"），渲染画出来的刃与贡献数值的刃就可能不是同一把**。
 * <p>
 * 这里统一为**槽位声明序**的深度优先前序，两种口径合一。遍历结果 {@link #nodes()} 是确定性的，
 * 渲染、属性、普攻方式、副手标记全部由它派生。
 * <p>
 * 派生量随构造一次算完——树最多几个节点，比"每次查询各走一遍"更省，也避免各处算法漂移。
 */
public final class AssemblyTree {

    /**
     * 一个装配节点。
     *
     * @param partId     零件 id
     * @param def        零件定义
     * @param type       零件类型定义
     * @param parentSlot 它装在哪個槽位上；仅用于取该槽的属性权重
     * @param depth      深度，根的直接子件为 1
     */
    public record Node(String partId, PartDef def, PartTypeDef type,
                       PartTypeDef.SlotDef parentSlot, int depth) {}

    /** 未装配/无效底座的空树 */
    private static final AssemblyTree EMPTY = new AssemblyTree(null, Map.of());

    private final String rootId;
    private final PartDef rootDef;
    private final PartTypeDef rootType;

    /** 全部后代节点，深度优先前序、槽位声明序——确定性的唯一遍历结果 */
    private final List<Node> nodes;
    private final int maxDepth;

    // ──── 派生量 ────

    private final double damage;
    private final double speed;
    private final double durability;
    private final double block;
    private final double reach;
    private final double knockback;

    private AssemblyTree(String rootId, Map<String, PartNode> slots) {
        this.rootId = rootId;
        this.rootDef = rootId == null ? null : PartRegistry.getPartDef(rootId);
        this.rootType = rootDef == null ? null : PartRegistry.getTypeDef(rootDef.type());

        List<Node> collected = new ArrayList<>();
        if (rootType != null && slots != null && !slots.isEmpty()) {
            collect(rootType, slots, null, 1, collected);
        }
        this.nodes = List.copyOf(collected);
        this.maxDepth = nodes.stream().mapToInt(Node::depth).max().orElse(0);

        // ──── 一趟遍历算出全部派生量 ────
        double accDamage = 0.0, accSpeed = 0.0, accDurability = 0.0, accBlock = 0.0;
        double foundReach = 0.0, foundKnockback = 0.0;
        boolean foundAttack = false;

        // 根节点自身也参与判定。根节点没有"所在槽位"，权重按 1.0。
        if (rootType != null) {
            accDamage += attr(rootDef, "damage");
            accSpeed += attr(rootDef, "speed");
            accDurability += attr(rootDef, "durability");
            if (isGuard(rootType)) accBlock += attr(rootDef, "block");
            if (isAttack(rootType)) {
                foundAttack = true;
                foundReach = rootType.combatReach();
                foundKnockback = rootType.combatKnockback();
            }
        }

        for (Node node : nodes) {
            PartTypeDef.SlotDef slot = node.parentSlot();
            accDamage += attr(node.def(), "damage") * weight(slot, "damage");
            accSpeed += attr(node.def(), "speed") * weight(slot, "speed");
            accDurability += attr(node.def(), "durability") * weight(slot, "durability");
            // 格挡减伤不走槽位权重（与原实现一致，裸加）
            if (isGuard(node.type())) accBlock += attr(node.def(), "block");
            // reach/knockback 只取遇到的第一个 attack 型节点，取到后不再覆盖（遍历序 = 声明序，确定性）
            if (!foundAttack && isAttack(node.type())) {
                foundAttack = true;
                foundReach = node.type().combatReach();
                foundKnockback = node.type().combatKnockback();
            }
        }

        this.damage = accDamage;
        this.speed = accSpeed;
        this.durability = accDurability;
        this.block = accBlock;
        this.reach = foundReach;
        this.knockback = foundKnockback;
    }

    // ──── 入口 ────

    /** 从物品栈构建——读 PART_IDENTITY 与 ASSEMBLED_SLOTS */
    public static AssemblyTree of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return EMPTY;
        String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (id == null) return EMPTY;
        Map<String, PartNode> slots = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (slots == null || slots.isEmpty()) {
            // 没装任何零件：仍需读根类型（部件自身可能有槽位/普攻方式声明），故不能直接返回 EMPTY
            return new AssemblyTree(id, Map.of());
        }
        return new AssemblyTree(id, slots);
    }

    // ──── 结构 ────

    public String rootId() { return rootId; }
    public PartDef rootDef() { return rootDef; }
    public PartTypeDef rootType() { return rootType; }

    /** 全部后代节点，DFS 前序、槽位声明序 */
    public List<Node> nodes() { return nodes; }

    /**
     * 是否装了至少一个**子件**（根节点本身不算）。
     * <p>
     * <b>2026-09-27 起它不再是"有没有属性"的判据。</b>属性与耐久本来就把根节点自身算在内
     * （见构造器里那句"根节点自身也参与判定"），而 {@code WeaponStats} 与 {@code CwcWeapon} 里
     * 以本方法为条件的过滤已全部拆掉——"手柄自己带数值"不再是被丢弃的死数。
     * <p>
     * 现在唯一的读者是 {@code CwcWeapon.appendHoverText}：裸手柄不显示"主手右键：格挡"那几行行为读数，
     * 装了零件才显示（那几行是**行为**，不是数值）。
     */
    public boolean hasParts() { return !nodes.isEmpty(); }

    /** 实际用到的最大深度（根的直接子件为 1） */
    public int maxDepth() { return maxDepth; }

    // ──── 派生量 ────

    /** 攻击伤害加成——按各节点所在槽位的权重加权累加 */
    public double damage() { return damage; }
    /** 攻速贡献——**绝对量**（最终攻速 = 玩家基础 4.0 + 它），模组不再叠加固定基准 */
    public double speed() { return speed; }
    /** 耐久上限 */
    public double durability() { return durability; }
    /** 格挡减伤加成（镡提供），已按裸加聚合 */
    public double block() { return block; }
    /** 攻击范围加成——取第一个 attack 型节点的声明值 */
    public double reach() { return reach; }
    /** 击退加成——取第一个 attack 型节点的声明值 */
    public double knockback() { return knockback; }

    // ──── 内部 ────

    /**
     * 深度优先前序收集。**遍历槽位声明序而非 map 序**，这是本类存在的意义之一。
     * <p>
     * 深度超限即停并在更深的分支上截断。当前数据下玩家拼不出这么深（类型约束把深度封在 3 以内），
     * 这里的上限是兜底：只要有人把某个槽位的 constraint 漏写（空约束 = 全收），底座槽又接受任意零件，
     * 就能把武器塞进它自己的槽，进而是无限递归 + 体积指数膨胀。
     */
    private static void collect(PartTypeDef parentType, Map<String, PartNode> children,
                               PartTypeDef.SlotDef parentSlot, int depth, List<Node> out) {
        if (depth > PartNode.MAX_DEPTH) {
            ColdWeaponCraftsmanship.LOGGER.warn(
                    "装配树深度超过上限 {}，更深的分支已被忽略", PartNode.MAX_DEPTH);
            return;
        }
        for (PartTypeDef.SlotDef slot : parentType.slots()) {
            PartNode child = children.get(slot.name());
            if (child == null) continue;
            PartDef def = PartRegistry.getPartDef(child.id());
            if (def == null) {
                // 零件定义查不到（数据包被移除或改了 id）——跳过这一支，不能让整把武器失效
                continue;
            }
            PartTypeDef type = PartRegistry.getTypeDef(def.type());
            if (type == null) continue;
            out.add(new Node(child.id(), def, type, slot, depth));
            collect(type, child.children(), slot, depth + 1, out);
        }
    }

    /** 槽位权重；根节点（slot == null）按 1.0 全量计入 */
    private static double weight(PartTypeDef.SlotDef slot, String attribute) {
        return slot == null ? 1.0 : slot.weight(attribute);
    }

    /**
     * 读取零件 data 的属性值。
     * <p>
     * 两种形状：{@code cwc:default} 放的是标量（{@link Number}），{@code cwc:metal} 放的是
     * {@link PartFormula}——后者**只取 {@code base}**，两个乘数目前不参与计算（属于尚未实现的锻造系统），
     * 详见 {@link PartFormula} 的类注释。改那两个字段不会改变任何数值，很容易误判成 bug。
     */
    private static double attr(PartDef def, String key) {
        Object value = def == null ? null : def.data().get(key);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof PartFormula f) return f.base();
        return 0.0;
    }

    private static boolean isGuard(PartTypeDef type) {
        return type != null && "guard".equals(type.data().get("type"));
    }

    private static boolean isAttack(PartTypeDef type) {
        return type != null && "attack".equals(type.data().get("type"));
    }
}
