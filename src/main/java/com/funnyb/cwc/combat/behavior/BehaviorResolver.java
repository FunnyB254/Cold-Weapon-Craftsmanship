package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.AssemblyTree;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 行为选择器——"这只手、这个键位该做什么"的**唯一答案来源**。
 * <p>
 * 输入是一棵装配树，输出是胜出的行为（或 null）。规则：
 * <ol>
 *   <li><b>候选</b>来自根类型、每个节点的类型、以及每个节点所在**槽位**（槽位可以代装进来的零件授予行为）。
 *       遍历顺序是"根优先、然后 DFS 声明序"，但它**不影响胜负**——优先级是唯一的比较键。
 *       顺序只影响诊断消息里列出候选的先后。</li>
 *   <li><b>优先级的三个档</b>：{@code ≥1} 参与竞争（越大越优先）；{@code <0} 是**主动退出**
 *       （不参与，并且取消"它所在槽位代它授予的同一个行为"）；{@code 0} 在解析期就已报错。</li>
 *   <li><b>并列 = 冲突</b>：胜者为 {@code null}，这只手这个键位**什么都不做**。刻意不是"先到先得"——
 *       那会让结果取决于遍历顺序（实现的私事），数据包作者无从预期。冲突由调用方记日志并
 *       在装配界面提示。</li>
 * </ol>
 * <b>不缓存。</b>{@code AssemblyTree.of(stack)} 本来就是随用随建；把结果缓存在物品上正是 ARCH-1
 * 明令禁止的"派生数据物化"。候选规模是"树深 × 每个来源一两条"，重算的代价可以忽略。
 */
public final class BehaviorResolver {

    private BehaviorResolver() {}

    /** 完整解析（含候选与冲突信息）——装配界面的冲突提示也用它 */
    public static Resolution resolve(AssemblyTree tree, InteractionHand hand, Button button) {
        List<Candidate> candidates = collect(tree, hand, button);

        int best = Integer.MIN_VALUE;
        for (Candidate c : candidates) {
            best = Math.max(best, c.decl().priority());
        }
        if (best < 1) return new Resolution(null, candidates, null);     // 没有任何人参与竞争

        List<Candidate> tied = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.decl().priority() == best) tied.add(c);
        }
        if (tied.size() > 1) return new Resolution(null, candidates, new Conflict(best, tied));

        Candidate winner = tied.get(0);
        WeaponBehavior impl = BehaviorRegistry.get(winner.decl().behavior());
        // 未注册：解析期 codec 已经拦过一道，这里是二道保险（例如行为在运行时被替换掉）
        if (impl == null) return new Resolution(null, candidates, null);
        return new Resolution(new Selection(impl, winner.decl()), candidates, null);
    }

    /** 便捷：只要胜出者。冲突 / 无人竞争 / 实现未注册 → null（= 放行原版或什么都不做） */
    public static Selection select(AssemblyTree tree, InteractionHand hand, Button button) {
        return resolve(tree, hand, button).winner();
    }

    private static List<Candidate> collect(AssemblyTree tree, InteractionHand hand, Button button) {
        List<Candidate> out = new ArrayList<>();
        PartTypeDef root = tree.rootType();
        if (root == null) return out;                                    // 空树 / 非本模组物品
        addDecls(out, root.behaviors(), tree.rootId(), null, hand, button);
        addAliases(out, root, tree.rootId(), null, hand, button, true);

        for (AssemblyTree.Node node : tree.nodes()) {
            PartTypeDef.SlotDef slot = node.parentSlot();
            String slotName = slot == null ? null : slot.name();
            Set<String> optedOut = addDecls(out, node.type().behaviors(), node.partId(), slotName, hand, button);
            addAliases(out, node.type(), node.partId(), slotName, hand, button, false);
            if (slot != null) addSlotGrants(out, slot, optedOut, node.partId(), slotName, hand, button);
        }
        return out;
    }

    /**
     * 把一组声明里匹配本次（手 × 键位）的挑出来，**原样**放进候选（包括 priority &lt; 0 的——
     * 它们留在候选里供诊断，但不参与竞争）。
     *
     * @return 这组声明里"主动退出"的行为 id 集合，供槽位授予用它做取消
     */
    private static Set<String> addDecls(List<Candidate> out, List<BehaviorDecl> decls, String partId,
                                        String slot, InteractionHand hand, Button button) {
        Set<String> optedOut = new HashSet<>();
        for (BehaviorDecl decl : decls) {
            if (decl.hand() != hand || decl.button() != button) continue;
            if (decl.priority() < 0) optedOut.add(decl.behavior());
            out.add(new Candidate(decl, partId, slot, false));
        }
        return optedOut;
    }

    /**
     * 槽位代装进来的零件授予行为。若该零件自己声明了**同一个行为**且优先级为负，这份授予作废
     * ——这就是负优先级存在的意义（槽位说"装了就有格挡"，而某个镡说"我不要"）。
     */
    private static void addSlotGrants(List<Candidate> out, PartTypeDef.SlotDef slot, Set<String> optedOut,
                                      String partId, String slotName, InteractionHand hand, Button button) {
        for (BehaviorDecl decl : slot.behaviors()) {
            if (decl.hand() != hand || decl.button() != button) continue;
            if (optedOut.contains(decl.behavior())) continue;
            out.add(new Candidate(decl, partId, slotName, true));
        }
    }

    /**
     * 旧布尔字段（{@code twoHanded} / {@code offhandAttack}）折算成显式声明——**别名**。
     * <p>
     * 刻意在**这里**折算、不在 codec 里折叠：codec 折叠的话，网络 codec 与 NBT 往返那一趟会把它丢掉
     * （注册表同步走的就是那条路，{@code tmp/codeccheck} 会先报出来）。
     * <p>
     * 别名同样**永不删除**：{@code docs/part-format.md} 是冻结契约，且第三方数据会一直用旧字段。
     */
    private static void addAliases(List<Candidate> out, PartTypeDef type, String partId, String slot,
                                   InteractionHand hand, Button button, boolean isRoot) {
        if (button != Button.USE) return;
        if (hand == InteractionHand.OFF_HAND && type.offhandAttack()) {
            out.add(new Candidate(BehaviorDecl.alias(hand, button, BehaviorRegistry.SWING), partId, slot, false));
        }
        // twoHanded 历来只读**根类型**（旧的 CwcWeapon.isTwoHandedStack 就是 rootType().twoHanded()）
        if (isRoot && hand == InteractionHand.MAIN_HAND && type.twoHanded()) {
            out.add(new Candidate(BehaviorDecl.alias(hand, button, BehaviorRegistry.BLOCK), partId, slot, false));
        }
    }

    /** 一个候选：声明 + 它来自哪个零件 / 槽位（后两个只为诊断） */
    public record Candidate(BehaviorDecl decl, String partId, String slot, boolean fromSlot) {}

    /** 胜出者：实现 + 它的声明（参数从后者取） */
    public record Selection(WeaponBehavior behavior, BehaviorDecl decl) {}

    /**
     * @param winner     胜出者；**冲突、无人竞争、或实现未注册时为 null**（这只手这个键位什么都不做）
     * @param candidates 全部候选（诊断，也用于装配界面的冲突提示）
     * @param conflict   并列冲突（此时 {@code winner} 必为 null）；无冲突为 null
     */
    public record Resolution(Selection winner, List<Candidate> candidates, Conflict conflict) {}

    /** @param priority 并列的那个优先级 @param tied 并列的全部候选（报错消息要指名它们来自哪个零件） */
    public record Conflict(int priority, List<Candidate> tied) {}
}
