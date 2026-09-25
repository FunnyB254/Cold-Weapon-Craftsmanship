package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.AssemblyTree;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * <h2>旧字段折算的别名（兜底，不是竞争）</h2>
 * 迁移期间旧数据一个字不改也必须一模一样，于是三条旧声明折算成行为候选：
 * {@code twoHanded} → {@code mainHandUse = block_use}（外加 {@link #disableOffHand}）、
 * {@code offhandAttack} → {@code offHandUse = swing_use}、
 * {@code combat.style} → {@code attack = strike/sweep/critical_attack}。
 * <p>
 * 别名**只在整棵树里没有任何显式声明**时才加进候选（见 {@link #addAlias}）。若让它与显式声明一样
 * 以 0 档参与竞争，两者会**并列成冲突**、把整个字段废掉——新数据只要声明了同一个字段就该完全接管旧字段。
 * 别名还带上各自的内建 HUD id（旧数据照旧有指示器），这属于"折算成完全等价的 JSON"的一部分。
 *
 * <h2>不缓存</h2>
 * {@code AssemblyTree.of(stack)} 本来就是随用随建；把解析结果物化到物品上正是 ARCH-1 明令禁止的。
 */
public final class BehaviorResolver {

    private BehaviorResolver() {}

    /**
     * 解析整件武器：每个**有胜者**的字段给出一个结果；冲突/无人声明的字段**不在表里**。
     * 键的存在与否就是"这个字段有没有胜者"，调用方不必再判空。
     * <p>
     * 本方法只是**把装配树摊成 {@link LevelSpec} 列表**（下标 0 = 整件武器那一层，之后对应
     * {@code tree.nodes()} 的下标 +1），算法本身在 {@link #resolveLevels} 里——它不依赖物品栈与注册表，
     * 所以离线检查器能自己造几层数据跑（那几条规则错起来全是静默的）。
     */
    public static Map<BehaviorField, FieldResult> resolve(AssemblyTree tree) {
        return diagnose(tree).winners();
    }

    /**
     * 与 {@link #resolve} 同一趟解析，但**多给出并列信息**（哪个字段被并列废掉了、谁在抢、并列在哪一档）。
     * <p>
     * 给诊断用（武器 tooltip）。别在每 tick 的路径上调它——那只需要 {@link #behaviorFor}。
     */
    public static Diagnosis diagnose(AssemblyTree tree) {
        PartTypeDef root = tree.rootType();
        if (root == null) return new Diagnosis(Map.of(), Map.of());

        List<AssemblyTree.Node> nodes = tree.nodes();
        int count = nodes.size();

        // children[i] = 第 i 层的直接子件（先按下标收集，再包进不可变的 LevelSpec）
        List<List<ChildSpec>> children = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) children.add(new ArrayList<>());

        // 父子关系：DFS 前序里，深度 d 的父就是最近一个深度 d-1 的节点；深度 1 的父是根。
        // 于是"子件的下标必然大于父件"——这正是自底向上遍历的前提。
        int[] lastAtDepth = new int[Math.max(1, tree.maxDepth() + 1)];
        Arrays.fill(lastAtDepth, -1);
        for (int i = 0; i < count; i++) {
            int depth = nodes.get(i).depth();
            int parentLevel = depth <= 1 ? 0 : lastAtDepth[depth - 1];
            if (parentLevel >= 0) {
                AssemblyTree.Node node = nodes.get(i);
                children.get(parentLevel).add(new ChildSpec(i + 1, node.parentSlot(), node.partId()));
            }
            if (depth < lastAtDepth.length) lastAtDepth[depth] = i + 1;
        }

        List<LevelSpec> levels = new ArrayList<>(count + 1);
        levels.add(new LevelSpec(root, tree.rootId(), true, tree.offhandAttack(), tree.attackStyle(), children.get(0)));
        for (int i = 0; i < count; i++) {
            AssemblyTree.Node node = nodes.get(i);
            levels.add(new LevelSpec(node.type(), node.partId(), false, false, null, children.get(i + 1)));
        }
        LevelOutcome outcome = resolveLevels(levels);
        return new Diagnosis(outcome.winners(), outcome.ties());
    }

    /**
     * 解析算法本体——**只吃 {@link LevelSpec} 列表**，与物品栈、注册表、{@code AssemblyTree} 全都无关。
     * <p>
     * 约定：每个子件的下标**大于**它的父件（DFS 前序保证），所以倒着遍历一遍就能自底向上算完
     * （子件先于父件出结果）。返回下标 0 那一层的结果。
     */
    static LevelOutcome resolveLevels(List<LevelSpec> levels) {
        int count = levels.size();
        List<LevelOutcome> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) results.add(null);
        for (int i = count - 1; i >= 0; i--) {
            results.set(i, resolveLevel(levels.get(i), results));
        }
        return results.get(0);
    }

    /** 便捷：某个字段的胜者；没有（未声明或冲突）返回 null */
    public static FieldResult select(AssemblyTree tree, BehaviorField field) {
        return resolve(tree).get(field);
    }

    /** 便捷：物品栈上某个字段的胜者 */
    public static FieldResult select(ItemStack stack, BehaviorField field) {
        return select(AssemblyTree.of(stack), field);
    }

    /**
     * 胜出的**行为实现**——调用方真正要用的东西。没有（未声明 / 冲突 / 未注册）返回 null。
     * <p>
     * null 的含义是"这个字段在这件武器上不生效"，调用方据此决定放行原版还是什么都不做——
     * 不是错误（冲突本来就是数据包作者要看见的结论之一）。
     */
    public static WeaponBehavior behaviorFor(ItemStack stack, BehaviorField field) {
        FieldResult winner = select(stack, field);
        return winner == null ? null : BehaviorRegistry.get(winner.behavior());
    }

    /** 胜出的**声明**（行为 id + hud id）——行为的各入口都要带着它。没有则 null */
    public static PartTypeDef.BehaviorDecl declFor(ItemStack stack, BehaviorField field) {
        FieldResult winner = select(stack, field);
        if (winner == null) return null;
        return new PartTypeDef.BehaviorDecl(winner.behavior(), Optional.ofNullable(winner.hud()));
    }

    /**
     * 只拿得到物品栈的原版钩子（{@code Item.getUseAnimation}）用：主手字段优先，其次副手。
     * <p>
     * 那是原版签名本身的限制（没有手也没有实体），见 {@code WeaponBehavior.useAnimation}。
     * 内置行为里只有格挡有动画、而格挡挂在 {@code mainHandUse} 上，故实际不会歧义。
     */
    public static PartTypeDef.BehaviorDecl firstUseDecl(ItemStack stack) {
        PartTypeDef.BehaviorDecl main = declFor(stack, BehaviorField.MAIN_HAND_USE);
        return main != null ? main : declFor(stack, BehaviorField.OFF_HAND_USE);
    }

    /**
     * 这只物品栈在实体手上的哪一边——**只给拿不到手参数的钩子用**（{@code getUseDuration}）。
     * <p>
     * 原版 {@code LivingEntity.startUsingItem} 把 {@code getUseItem()} 直接当物品栈传进来，所以
     * 这里按**实例**比较是准确的（不是值比较）。都不匹配时按主手——那种情形下结论本来就无意义。
     */
    public static InteractionHand handOf(LivingEntity entity, ItemStack stack) {
        return entity.getOffhandItem() == stack ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /**
     * 这只手是否被**另一只手**的武器屏蔽（{@code disableOffHand}）。
     * <p>
     * 判定"另一只手上的那把武器"，与旧代码里的 {@code isTwoHandedStack(player.getMainHandItem())}
     * 同向：手上拿着双手武器时，副手出刀被挡。反向也成立（副手握着声明了 disableOffHand 的武器时，
     * 主手右键归它）——目前没有数据这么用，但规则是对称的。
     */
    public static boolean otherHandBlocks(Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(other);
        return !stack.isEmpty() && disableOffHand(AssemblyTree.of(stack));
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

    /**
     * 一层的解析输入——{@code AssemblyTree} 那一侧适配出来的东西。
     * <p>
     * 摊成这个形状是为了让算法**不依赖物品栈与注册表**：离线检查器（{@code tmp/codeccheck}）直接造几层
     * 就能跑同一条代码路径。四条规则错起来全是静默的（表现为"某个行为不生效"），只靠游戏里试很难定位。
     *
     * @param type                 本层的类型
     * @param partId               本层来自哪个零件（诊断用）
     * @param isRoot               是不是整件武器那一层（只有它吃旧字段别名）
     * @param legacyOffhandAttack  旧的 {@code offhandAttack} 派生值（全树 OR）——仅根层有意义
     * @param legacyStyle          旧的 {@code combat.style} 派生值（第一个 attack 型节点）——仅根层有意义
     * @param children             直接子件，**下标必须大于本层**（DFS 前序保证，见 {@link #resolveLevels}）
     */
    record LevelSpec(PartTypeDef type, String partId, boolean isRoot, boolean legacyOffhandAttack,
                     PartTypeDef.AttackStyle legacyStyle, List<ChildSpec> children) {}

    /**
     * 一个直接子件：它在 {@code levels} 里的下标 + **它所在槽位**（优先级从槽位读，不从零件读）+ 它是哪个
     * 零件（诊断用，将来装配界面标"谁在抢这个字段"要用）。
     * {@code slot} 为 null 表示"这个子件没有所在槽位"（装配树里不可能，但也没必要炸）。
     */
    record ChildSpec(int index, PartTypeDef.SlotDef slot, String partId) {}

    private static LevelOutcome resolveLevel(LevelSpec level, List<LevelOutcome> results) {
        Map<BehaviorField, List<Candidate>> candidates = new EnumMap<>(BehaviorField.class);
        for (BehaviorField field : BehaviorField.values()) candidates.put(field, new ArrayList<>());

        // ① 本层自己的声明（本层默认，优先级 0）
        for (BehaviorField field : BehaviorField.values()) {
            declared(level.type(), field).ifPresent(decl ->
                    candidates.get(field).add(new Candidate(0, decl.behavior(), decl.hud().orElse(null),
                            level.partId(), null, false)));
        }

        // ② 直接子件：以"它所在槽位给这个字段的优先级"进入
        for (ChildSpec child : level.children()) {
            LevelOutcome childOutcome = results.get(child.index());
            if (child.slot() == null || childOutcome == null) continue;
            for (BehaviorField field : BehaviorField.values()) {
                FieldResult winner = childOutcome.winners().get(field);
                if (winner == null) continue;
                int priority = child.slot().priorityFor(field);
                if (priority < 1) continue;      // 槽位没给这个字段说话权 → 子树的结果在这一层不参与
                candidates.get(field).add(new Candidate(priority, winner.behavior(), winner.hud(),
                        child.partId(), child.slot().name(), false));
            }
        }

        // ③ 旧字段折算的别名——只在整件武器这一层，且**兜底**（没有任何显式声明时才加）。
        //    用的是 AssemblyTree 已经派生好的值，保证与迁移前语义一字不差（offhandAttack 历来是全树 OR、
        //    twoHanded 历来只读根、attackStyle 历来取第一个 attack 型节点）。刻意**不走"子件以槽位优先级
        //    入场"**那条路：它要求旧 JSON 补写槽位优先级，否则短刀的副手出刀会因为"槽位没给 offHandUse
        //    说话权"而整个丢掉。
        if (level.isRoot()) {
            if (level.legacyOffhandAttack()) {
                addAlias(candidates, BehaviorField.OFF_HAND_USE, BehaviorRegistry.SWING,
                        BehaviorHudIds.OFFHAND_ATTACK, "（旧字段 offhandAttack）");
            }
            if (level.type().twoHanded()) {
                // 格挡没有 HUD——姿态动画本身就是反馈（旧数据也没有这一条指示器）
                addAlias(candidates, BehaviorField.MAIN_HAND_USE, BehaviorRegistry.BLOCK,
                        null, "（旧字段 twoHanded）");
            }
            addAlias(candidates, BehaviorField.ATTACK, styleBehaviorId(level.legacyStyle()),
                    BehaviorHudIds.ATTACK_INDICATOR, "（旧字段 combat.style）");
        }

        // ④ 每字段裁决；并列 → 该字段没有胜者（但把"并列"记下来，诊断要用）
        Map<BehaviorField, FieldResult> winners = new EnumMap<>(BehaviorField.class);
        Map<BehaviorField, Tie> ties = new EnumMap<>(BehaviorField.class);
        for (BehaviorField field : BehaviorField.values()) {
            Pick pick = pick(candidates.get(field));
            if (pick.winner() != null) winners.put(field, pick.winner());
            if (pick.tie() != null) ties.put(field, pick.tie());
        }
        return new LevelOutcome(winners, ties);
    }

    /**
     * 从候选里挑胜者：行为取最高档、HUD 取"带 hud 的"最高档；最高档并列 → 该字段无胜者。
     * <p>
     * 包内可见是为了让 {@code tmp/codeccheck/BehaviorCheck.java} 能离线跑规则（那几条错得静默，
     * 只在游戏里表现为"某个行为不生效"，很难查）。除了这条检查器，没有别的调用方。
     */
    static Pick pick(List<Candidate> candidates) {
        if (candidates.isEmpty()) return new Pick(null, null);

        int best = Integer.MIN_VALUE;
        for (Candidate candidate : candidates) best = Math.max(best, candidate.priority());
        List<Candidate> top = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.priority() == best) top.add(candidate);
        }
        if (top.size() > 1) {
            // 并列 = 冲突 → 该字段无胜者。参与者一并记下来：诊断（武器 tooltip）要报"哪两个槽在抢"
            List<Participant> participants = new ArrayList<>(top.size());
            for (Candidate candidate : top) {
                participants.add(new Participant(candidate.slotName(), candidate.partId(), candidate.fromAlias()));
            }
            return new Pick(null, new Tie(best, List.copyOf(participants)));
        }

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
        return new Pick(new FieldResult(top.get(0).behavior(), hud, best), null);
    }

    private static java.util.Optional<PartTypeDef.BehaviorDecl> declared(PartTypeDef type, BehaviorField field) {
        return switch (field) {
            case MAIN_HAND_USE -> type.mainHandUse();
            case OFF_HAND_USE -> type.offHandUse();
            case ATTACK -> type.attack();
        };
    }

    /**
     * 追加一条**别名**候选（旧字段折算）——该字段已经有了显式声明就什么都不加，见类注释的说明。
     * 别名一律以 0 档（本层默认）入场：它是"没人说话时的旧解释"，不是一支竞争力量。
     */
    private static void addAlias(Map<BehaviorField, List<Candidate>> candidates, BehaviorField field,
                                 String behavior, String hud, String partId) {
        for (Candidate candidate : candidates.get(field)) {
            if (!candidate.fromAlias()) return;      // 有人显式声明了 → 别名让位
        }
        candidates.get(field).add(new Candidate(0, behavior, hud, partId, null, true));
    }

    /** 旧的 {@code combat.style} → 攻击方式行为 id */
    private static String styleBehaviorId(PartTypeDef.AttackStyle style) {
        return switch (style) {
            case SWEEP -> BehaviorRegistry.SWEEP_ATTACK;
            case CRITICAL -> BehaviorRegistry.CRITICAL_ATTACK;
            case NORMAL -> BehaviorRegistry.STRIKE_ATTACK;
        };
    }

    /**
     * 一个候选：优先级 + 行为/HUD id + 来自谁（诊断用）+ 是否来自旧字段别名。
     *
     * @param slotName 这个候选是从哪个槽位进来的（槽位有语言键，诊断里报它比报零件 id 可读）；
     *                 本层类型自己的声明与旧字段别名为 null
     */
    public record Candidate(int priority, String behavior, String hud, String partId, String slotName,
                            boolean fromAlias) {}

    /** 一层解析完的结果：有胜者的字段 + 并列的字段（并列意味着**没有**胜者，见 {@link Tie}） */
    record LevelOutcome(Map<BehaviorField, FieldResult> winners, Map<BehaviorField, Tie> ties) {}

    /**
     * 整件武器的诊断——胜者与并列各一张表。
     * <p>
     * 存在的理由是"并列"没有别的观察窗口：{@link #resolve} 只给胜者，于是"该字段被并列废掉了"与
     * "该字段没人声明"在调用方看来一模一样。武器的 tooltip 用这个把结论显示出来（见 {@code CwcWeapon}）。
     */
    public record Diagnosis(Map<BehaviorField, FieldResult> winners, Map<BehaviorField, Tie> ties) {}

    /**
     * 一个字段的裁决——**至多一个非 null**：
     * 有胜者时 {@code winner} 非 null、{@code tie} 为 null；并列时反过来；没有候选时两者都是 null。
     */
    record Pick(FieldResult winner, Tie tie) {}

    /**
     * 并列——最高档上有多个候选，于是该字段在本层没有胜者（作者定的"并列 = 冲突，不是先到先得"）。
     *
     * @param priority     并列发生在哪一档（数据作者改槽位 {@code priority} 时看的就是这个数）
     * @param participants 并列的参与者（正常是"两个槽都在声明"）
     */
    public record Tie(int priority, List<Participant> participants) {}

    /** 并列的一方：来自哪个槽位（别名/本层声明为 null）+ 哪个零件 + 是不是旧字段别名 */
    public record Participant(String slotName, String partId, boolean fromAlias) {}

    /**
     * 某个字段在本层的胜出结果。
     *
     * @param behavior 行为 id（拿去 {@link BehaviorRegistry#get} 取实现）
     * @param hud      HUD id；null = 这个字段不画东西
     * @param priority 胜出时用的优先级（诊断用）
     */
    public record FieldResult(String behavior, String hud, int priority) {}
}
