package com.funnyb.cwc.combat;

import com.funnyb.cwc.crafting.PartNode;
import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.registry.CwcDataComponents;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * "这只手拿的是**哪一把**武器"的**单一所有者**，外加由它派生的换手规则：
 * **换上 CWC 武器要付一次冷却**（作者 2026-09-27 定）。
 * <p>
 * 本类只回答两个问题，两处消费者都从这一份事实上取数，不各算一遍：
 * <ul>
 *   <li>{@link #mainHandSwapTick} —— 主手武器身份**最后一次变化**的 tick。
 *       {@code CwcClientEvents.mainHandSettled} 拿它当"刚换过武器"的属性追平门槛（BUG-007 的修法）；</li>
 *   <li>{@link #observe} 里顺手收的冷却——换到武器就计费，见下。</li>
 * </ul>
 *
 * <h2>为什么需要这个类：原版那句判定对 CWC 武器恒为"没换"</h2>
 * {@code Player.tick()} 只在 {@code !ItemStack.matches(...)} **且** {@code !ItemStack.isSameItem(...)}
 * 时才归零攻速条（{@code Player.java:319-327}）。而本模组的**裸手柄与装配好的武器是同一个物品**
 * （{@code CwcItems.HANDLE_PART}），于是"换一把 CWC 武器"在原版眼里是"同一件物品"，**不归零**——
 * 换刀白赚一次满蓄力。本类在那一句之外补上这一次归零。
 *
 * <h2>两条规则</h2>
 * <ol>
 *   <li><b>主手</b>身份变化 → {@code resetAttackStrengthTicker()}（两侧都调）+ 服务端把自持时钟拨到
 *       此刻（见 {@link CwcCombat#chargeMainHandCooldown}）。计费口径不再另立：客户端门槛仍是
 *       0.9、服务端仍是 {@code ceil(0.9D) - 3}，所以"付一次冷却"与点一次左键等长；</li>
 *   <li><b>副手</b>身份变化**且换上的东西是武器** → 副手冷却重新计时（两侧都收，理由同
 *       {@code SwingBehavior.onClientUse} 里那句"本地先记一次"）。</li>
 * </ol>
 * <b>不对非武器的变化收冷却</b>：副手只认"换上武器"（{@code off != null}）；主手对任何身份变化都归零
 * 攻速条——那正是原版"换物品"该有的行为，而**服务端时钟**对非 CWC 主手根本不被读，是空操作。
 * <p>
 * <b>首次观察不收冷却</b>：上线、重生、换维度时手里已经拿着武器，不该凭空被罚一次。
 *
 * <h2>身份 = 零件身份 + 装配树，<u>刻意不含耐久</u></h2>
 * 与 {@code CwcClientEvents} 原先那套逐字一致：{@code PART_IDENTITY} + {@code ASSEMBLED_SLOTS}。
 * 拿整份组件去比（{@code ItemStack.matches} / {@code isSameItemSameComponents}）会让**每次掉耐久**
 * 都判成"刚换了武器"——那个坑的完整来龙去脉见 {@link PartNode} 的类文档与 BUG-010。
 * <p>
 * 还要先过一道 {@link CwcWeapon#isWeaponBase}：光看"有没有装配树"不够，**单个零件（刃/镡）也带
 * {@code PART_IDENTITY}**，不认物品本身的话，"副手塞了一块刃"会被当成"换上武器"。
 * <p>
 * 两把**零件完全相同**的武器之间切换不收冷却（身份相等）——与原版"同物品同组件不算换"一致，
 * 而且这种切换对玩家没有任何收益。
 *
 * <h2>⚠ 客户端<u>不</u>注册 {@code PlayerTickEvent}</h2>
 * {@code PlayerTickEvent.Post} 在 {@code Player.tick()} **末尾**触发（{@code Player.java:335}），
 * 而客户端一个 tick 的顺序是 {@code Player.tick()} → {@code handleKeybinds()} → {@code ClientTickEvent.Post}。
 * 点击路径（{@code onAttackKey} → {@code tryMainHandAttack} → {@code mainHandSettled}）跑在
 * {@code handleKeybinds} 里，**晚于**那个事件。若给客户端也挂一个处理器，它会把身份变化**提前消费掉**，
 * {@code mainHandSettled} 随后看到"没变化"而返回 true——**BUG-007 那两 tick 的属性追平门槛当场失效**，
 * 换武器后第一下空挥的老毛病回归。
 * <p>
 * 所以本类的两个入口是：客户端走 {@code mainHandSettled}（它自己就是那两个调用点之一），
 * 服务端走 {@code CwcCombatEvents} 的 {@code PlayerTickEvent.Post}（带 {@code isClientSide} 守卫）。
 *
 * <h2>已知取舍</h2>
 * 服务端一个 tick 内**先处理包、后跑 {@code Player.tick()}**（{@code ServerGamePacketListenerImpl.tick():266}
 * → {@code ServerPlayer.doTick()} → {@code super.tick()}），所以"换武器"与"攻击"两个包落在同一 tick 时，
 * 那次攻击是按**换之前**的时钟判的。正常客户端够不到：它在换武器那一刻就把自己的攻速条归零了，
 * 根本不会发包。按项目既有立场（BUG-012）不为此收紧。
 */
public final class WeaponSwapCooldown {

    /** "从未见过这个玩家"的哨兵——远早于任何真实 {@code tickCount}，让首次比较不会误判成"刚换过" */
    public static final int NEVER_SWAPPED = -1000;

    /**
     * 每个玩家上一次观察到的状态。整体替换而不是散成三个 map：一次观察要么什么都不写
     * （两边身份都没变，见 {@link #observe}），要么三样一起换新，不存在"只更新了一半"的中间态。
     *
     * @param main 上一次看到的主手武器身份；主手不是武器时为 {@code null}
     * @param off  上一次看到的副手武器身份；副手不是武器时为 {@code null}
     * @param mainSwapTick 主手身份最后一次变化的 tick；从未变过为 {@link #NEVER_SWAPPED}
     */
    private record Seen(WeaponIdentity main, WeaponIdentity off, int mainSwapTick) {}

    /**
     * 与 {@code CwcCombat.LAST_MAIN_ATTACK_TICK}、{@code LAST_HIT} 同款用 {@code WeakHashMap}
     * ——玩家对象释放即自动回收，不必手工清理，也就没有"登出时忘了重置"这一类坑
     * （原先那三个静态字段是靠 {@code CwcClientEvents.resetTransientState} 手写清的）。
     */
    private static final Map<Player, Seen> SEEN = new WeakHashMap<>();

    private WeaponSwapCooldown() {}

    /**
     * 一把武器的身份——{@code PART_IDENTITY} + {@code ASSEMBLED_SLOTS}，**不含耐久**（见类注释）。
     * 主手/副手不是武器时一律 {@code null}，于是"在几件普通物品之间换"不会被当成换武器。
     */
    private record WeaponIdentity(String partId, Map<String, PartNode> slots) {

        /** 取这把武器的身份；不是武器返回 {@code null} */
        static WeaponIdentity of(ItemStack stack) {
            if (!CwcWeapon.isWeaponBase(stack)) return null;
            return new WeaponIdentity(stack.get(CwcDataComponents.PART_IDENTITY.get()),
                    stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get()));
        }
    }

    /**
     * 主手武器身份**最后一次变化**的 tick；从没见过这个玩家返回 {@link #NEVER_SWAPPED}。
     * <p>
     * 只读，不推进状态——推进是 {@link #observe} 的事。
     */
    public static int mainHandSwapTick(Player player) {
        Seen seen = SEEN.get(player);
        return seen == null ? NEVER_SWAPPED : seen.mainSwapTick();
    }

    /**
     * 每 tick 观察一次两只手拿了什么，**变了才收冷却**。
     * <p>
     * 调用点只有两个，且都是**必须**的：客户端的 {@code CwcClientEvents.mainHandSettled}
     * （点击路径还要额外调一次，因为它跑在 tick 末尾那个调用点之前）与服务端的
     * {@code CwcCombatEvents} 的 {@code PlayerTickEvent.Post}。**不要在客户端再注册
     * {@code PlayerTickEvent}**——理由见类注释里那一节。
     * <p>
     * 常态（两手都没变）一个对象都不建、一个 map 都不写就返回。
     */
    public static void observe(Player player) {
        WeaponIdentity main = WeaponIdentity.of(player.getMainHandItem());
        WeaponIdentity off = WeaponIdentity.of(player.getOffhandItem());

        Seen seen = SEEN.get(player);
        if (seen == null) {
            // 首次观察只记下，不收冷却——上线/重生/换维度手里已经拿着武器，不该凭空被罚一次
            SEEN.put(player, new Seen(main, off, NEVER_SWAPPED));
            return;
        }

        boolean mainChanged = !Objects.equals(seen.main(), main);
        boolean offChanged = !Objects.equals(seen.off(), off);
        if (!mainChanged && !offChanged) return;   // 常态：不建对象、不写 map

        if (mainChanged) {
            // 补上原版漏掉的那次归零（裸手柄与装配好的武器是同一个物品，原版判成"没换"）
            player.resetAttackStrengthTicker();
            // 服务端那根自持时钟也拨到此刻，否则换武器在服务端是免费的（见类注释第 1 条）
            if (!player.level().isClientSide) {
                CwcCombat.chargeMainHandCooldown(player);
            }
        }
        // 只认"换上的是武器"：副手换成普通物品不收冷却（那时也没人去读）
        if (offChanged && off != null) {
            CwcCombat.applyOffhandCooldown(player, player.getOffhandItem());
        }

        SEEN.put(player, new Seen(main, off, mainChanged ? player.tickCount : seen.mainSwapTick()));
    }
}
