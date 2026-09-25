package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.AssemblyTree;
import com.funnyb.cwc.crafting.PartTypeDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 行为选择器——"这件武器的每个字段最终听谁的"的**唯一答案来源**。
 *
 * <h2>规则（作者 2026-09-25 定）</h2>
 * <ol>
 *   <li><b>一个装配体是一层。</b>层内每个字段各自比优先级，最大者赢。</li>
 *   <li><b>类型自己的声明是本层的默认</b>（视作优先级 0，最低），所以槽位给的 ≥1 会压过它。</li>
 *   <li><b>优先级和槽位绑定</b>：子树内部算出的优先级**不外传**，它进入父层时只以"所在槽位的
 *       {@code priority} 表"给的数参与。→ 嵌套再深也不能越级夺权，父层有最终话语权。</li>
 *   <li>槽位表里**没写的字段 = 那个槽里的东西不参与该字段**。</li>
 *   <li><b>并列 = 冲突</b>：该字段在本层**没有胜者**（结果里干脆不出现这个键），不是先到先得
 *       ——那会让结果取决于遍历顺序，而遍历顺序是实现的私事，数据包作者无从预期。</li>
 * </ol>
 *
 * <h2>行为与 HUD 各自选</h2>
 * 同一个字段下的 {@code behavior} 与 {@code hud} 是**两笔独立的声明**，但走**同一套**上面的规则：
 * 行为取该字段优先级最高者；HUD 取"**带 hud 的**候选里优先级最高者"。于是两个行为可以共用一个 HUD、
 * 一个行为也可以换不同 HUD，而不需要引入任何新机制。（若带 hud 的候选在最高档并列，则 HUD 无胜者、
 * 行为不受影响。）
 *
 * <h2>不缓存</h2>
 * {@code AssemblyTree.of(stack)} 本来就是随用随建；把解析结果物化到物品上正是 ARCH-1 明令禁止的。
 */
public final class BehaviorResolver {

    private BehaviorResolver() {}

    /**
     * 解析整件武器：每个**有胜者**的字段给出一个结果；冲突/无人声明的字段**不在表里**。
     * 键的存在与否就是"这个字段有没有胜者"，调用方不必再判空。
     */
    public static Map<BehaviorField, FieldResult> resolve(AssemblyTree tree) {
        PartTypeDef root = tree.rootType();
        if (root == null) return Map.of();

        List<AssemblyTree.Node> nodes = tree.nodes();
        int count = nodes.size();

        // 层 = 类型 + 它的直接子件。nodes() 只有后代，所以根部合成一层当下标 0；
        // 之后 levels 下标 k 对应 nodes 下标 k-1。
        List<Level> levels = new ArrayList<>(count + 1);
        levels.add(new Level(root, tree.rootId(), new ArrayList<>()));
        for (AssemblyTree.Node node : nodes) {
            levels.add(new Level(node.type(), node.partId(), new ArrayList<>()));
        }

        // 父子关系：DFS 前序里，深度 d 的父就是最近一个深度 d-1 的节点；深度 1 的父是根
        int[] lastAtDepth = new int[Math.max(1, tree.maxDepth() + 1)];
        Arrays.fill(lastAtDepth, -1);
        for (int i = 0; i < count; i++) {
            int depth = nodes.get(i).depth();
            int parentLevel = depth <= 1 ? 0 : lastAtDepth[depth - 1];
            if (parentLevel >= 0) levels.get(parentLevel).children().add(i + 1);
            if (depth < lastAtDepth.length) lastAtDepth[depth] = i + 1;
        }

        // 自底向上：前序里子件排在父件之后，所以倒着遍历时子件的层已经算完了
        List<Map<BehaviorField, FieldResult>> results = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) results.add(null);
        for (int i = count; i >= 0; i--) {
            results.set(i, resolveLevel(levels.get(i), i == 0, results, nodes, tree));
        }
        return results.get(0);
    }

    /** 便捷：某个字段的胜者；没有（未声明或冲突）返回 null */
    public static FieldResult select(AssemblyTree tree, BehaviorField field) {
        return resolve(tree).get(field);
    }

    /**
     * "屏蔽另一只手"——**本层按 OR 生效：根 + 深度 1 的直接子件**。
     * <p>
     * 子树内部声明的不外传（与"优先级不外传"同一条精神，否则深层零件能从另一个门夺权）。
     * 旧字段 {@code twoHanded} 同时表达"主手格挡"和"屏蔽副手"两件事，所以它也算一票。
     */
    public static boolean disableOffHand(AssemblyTree tree) {
        PartTypeDef root = tree.rootType();
        if (root == null) return false;
        if (root.disableOffHand() || root.twoHanded()) return true;
        for (AssemblyTree.Node node : tree.nodes()) {
            if (node.depth() == 1 && node.type().disableOffHand()) return true;
        }
        return false;
    }

    // —— 内部 ——

    /** 一个"层"：类型 + 它的直接子件（levels 下标） */
    private record Level(PartTypeDef type, String partId, List<Integer> children) {}

    private static Map<BehaviorField, FieldResult> resolveLevel(Level level, boolean isRoot,
                                                                List<Map<BehaviorField, FieldResult>> results,
                                                                List<AssemblyTree.Node> nodes, AssemblyTree tree) {
        Map<BehaviorField, List<Candidate>> candidates = new EnumMap<>(BehaviorField.class);
        for (BehaviorField field : BehaviorField.values()) candidates.put(field, new ArrayList<>());

        // ① 本层自己的声明（本层默认，优先级 0）
        for (BehaviorField field : BehaviorField.values()) {
            declared(level.type(), field).ifPresent(decl ->
                    candidates.get(field).add(new Candidate(0, decl.behavior(), decl.hud().orElse(null),
                            level.partId(), false)));
        }

        // ② 旧布尔字段折算的别名——只在整件武器这一层，且直接读 AssemblyTree 已经派生好的值，
        //    保证与迁移前语义一字不差（offhandAttack 历来是全树 OR、twoHanded 历来只读根）。
        //    刻意**不走"子件以槽位优先级入场"**：那条路要求旧 JSON 补写槽位优先级，否则短刀的
        //    副手出刀会因为"槽位没给 offHandUse 说话权"而丢掉。
        if (isRoot) {
            if (tree.offhandAttack()) {
                candidates.get(BehaviorField.OFF_HAND_USE).add(new Candidate(0, BehaviorRegistry.SWING, null,
                        "（旧字段 offhandAttack）", true));
            }
            if (level.type().twoHanded()) {
                candidates.get(BehaviorField.MAIN_HAND_USE).add(new Candidate(0, BehaviorRegistry.BLOCK, null,
                        "（旧字段 twoHanded）", true));
            }
        }

        // ③ 直接子件：以"它所在槽位给这个字段的优先级"进入
        for (int childIndex : level.children()) {
            AssemblyTree.Node child = nodes.get(childIndex - 1);
            PartTypeDef.SlotDef slot = child.parentSlot();
            Map<BehaviorField, FieldResult> childResult = results.get(childIndex);
            if (slot == null || childResult == null) continue;
            for (BehaviorField field : BehaviorField.values()) {
                FieldResult winner = childResult.get(field);
                if (winner == null) continue;
                int priority = slot.priorityFor(field);
                if (priority < 1) continue;      // 槽位没给这个字段说话权 → 子树的结果在这一层不参与
                candidates.get(field).add(new Candidate(priority, winner.behavior(), winner.hud(),
                        child.partId(), false));
            }
        }

        // ④ 每字段取最大；并列 → 该字段没有胜者
        Map<BehaviorField, FieldResult> out = new EnumMap<>(BehaviorField.class);
        for (BehaviorField field : BehaviorField.values()) {
            FieldResult winner = pick(candidates.get(field));
            if (winner != null) out.put(field, winner);
        }
        return out;
    }

    /**
     * 从候选里挑胜者：行为取最高档、HUD 取"带 hud 的"最高档；最高档并列 → 该字段无胜者。
     * <p>
     * 包内可见是为了让 {@code tmp/codeccheck/BehaviorCheck.java} 能离线跑规则（那几条错得静默，
     * 只在游戏里表现为"某个行为不生效"，很难查）。除了这条检查器，没有别的调用方。
     */
    static FieldResult pick(List<Candidate> candidates) {
        if (candidates.isEmpty()) return null;

        int best = Integer.MIN_VALUE;
        for (Candidate candidate : candidates) best = Math.max(best, candidate.priority());
        int topCount = 0;
        Candidate top = null;
        for (Candidate candidate : candidates) {
            if (candidate.priority() == best) {
                topCount++;
                top = candidate;
            }
        }
        if (topCount > 1) return null;                       // 并列 = 冲突 → 该字段无胜者

        // HUD 独立选：在带 hud 的候选里取最高档；若最高档也并列，则只有 HUD 无胜者（行为不受影响）
        int hudBest = Integer.MIN_VALUE;
        for (Candidate candidate : candidates) {
            if (candidate.hud() != null) hudBest = Math.max(hudBest, candidate.priority());
        }
        String hud = null;
        if (hudBest != Integer.MIN_VALUE) {
            int hudTopCount = 0;
            String hudTop = null;
            for (Candidate candidate : candidates) {
                if (candidate.priority() == hudBest && candidate.hud() != null) {
                    hudTopCount++;
                    hudTop = candidate.hud();
                }
            }
            if (hudTopCount == 1) hud = hudTop;
        }
        return new FieldResult(top.behavior(), hud, best);
    }

    private static java.util.Optional<PartTypeDef.BehaviorDecl> declared(PartTypeDef type, BehaviorField field) {
        return switch (field) {
            case MAIN_HAND_USE -> type.mainHandUse();
            case OFF_HAND_USE -> type.offHandUse();
            case ATTACK -> type.attack();
        };
    }

    /** 一个候选：优先级 + 行为/HUD id + 来自谁（诊断用）+ 是否来自旧字段别名 */
    public record Candidate(int priority, String behavior, String hud, String partId, boolean fromAlias) {}

    /**
     * 某个字段在本层的胜出结果。
     *
     * @param behavior 行为 id（拿去 {@link BehaviorRegistry#get} 取实现）
     * @param hud      HUD id；null = 这个字段不画东西
     * @param priority 胜出时用的优先级（诊断用）
     */
    public record FieldResult(String behavior, String hud, int priority) {}
}
