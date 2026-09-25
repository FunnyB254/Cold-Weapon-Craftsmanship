package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.AssembledWeaponRenderer;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.combat.behavior.BehaviorDispatch;
import com.funnyb.cwc.combat.behavior.Button;
import com.funnyb.cwc.combat.behavior.WeaponBehavior;
import com.funnyb.cwc.crafting.PartNode;
import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.network.serverbound.CwcMainHandAttackPacket;
import com.funnyb.cwc.network.serverbound.CwcOffhandAttackPacket;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * CWC 客户端事件处理器。负责两件事：
 * 1. 攻击输入拦截——主手 CWC 武器左键、副手短刀右键，改走自研攻击包（见 {@link #onAttackKey} / {@link #onUseKey}）
 * 2. 按住键自动攻击——每 tick 补一次出手（见 {@link #onClientTick}）
 * <p>
 * 准星与攻击指示器的接管已移到 {@link com.funnyb.cwc.client.renderer.CrosshairIndicators}
 * （那是纯视觉、不读本类任何状态的独立单元）；资源重载时刷新 Layouts 缓存与武器合成缓存见
 * {@link #onRegisterReloadListeners}。
 * <p>
 * （PartRegistry 由服务端 datapack 加载，客户端不重载，理由见 {@link #onRegisterReloadListeners}。）
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public class CwcClientEvents {

    /**
     * 资源重载（F3+T）后刷新 Layouts 缓存、清空武器合成缓存（含顶点网格与动态纹理）。
     * 注意：{@code RegisterClientReloadListenersEvent} 是 {@code IModBusEvent}，本应由模组总线派发；
     * NeoForge 会按事件类型自动分流，所以这里不写 bus 参数也能收到（显式写 bus 已被标记待删除）。
     * PartRegistry 数据由服务端 datapack 加载（{@link ColdWeaponCraftsmanship} 服务端事件），
     * 客户端资源管理器不含 data/，重载会把共享注册表清空（F3+T 后制造界面列表变空），故不在此重载。
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // 注入布局 JSON 的资源管理器来源——Layouts 已移到通用包，不能再自己引用 Minecraft
        Layouts.setResourceManagerSupplier(() -> Minecraft.getInstance().getResourceManager());
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Layouts.invalidate();
            AssembledWeaponRenderer.clearCache();
        });
    }

    /**
     * 退出世界 / 断开连接时清空本类的全部静态状态。
     * <p>
     * 本类那些跨 tick 的静态字段（模式锁、主手武器身份、副手挂起旗子）不清就会**残留到下一个世界**。
     * 今天"恰好无害"——{@code mainHandSettled} 下次比较自然会判定成"变了"、{@code holdModeLocked}
     * 会在松开左键时解锁——但那是**巧合而不是保证**。显式重置一次，把这类运气去掉。
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetTransientState();
    }

    /**
     * 把全部跨 tick 记忆恢复成初始态。
     * <p>
     * <b>本类新增静态可变字段时，记得加进来。</b>
     */
    private static void resetTransientState() {
        holdModeLocked = false;
        holdModeMining = false;
        lastMainHandId = null;
        lastMainHandSlots = null;
        mainHandChangedTick = NO_WEAPON_CHANGE;
        sawMainHandUse = false;
        sawOffhandUse = false;
        offhandAutoSuspended = false;
    }

    /**
     * 主手 CWC 武器普攻——输入层拦截（客户端主拦截）。
     * 主手持本模组武器（HANDLE_PART）时取消 vanilla 攻击，改走自定义包
     * {@link CwcMainHandAttackPacket}：服务端按刃型普攻方式（AttackStyle）权威结算
     * （normal/critical 单目标、sweep 范围全额，空挥也结算范围）。
     * 冷却未满只取消不发包（强制等冷却）；冷却满点选目标发包。
     * 非本模组武器放行 vanilla；**本次按住锁定为"挖掘"时也放行**（见 {@link #isMiningLocked}）。
     * 服务器端 AttackEntityEvent 兜底见 {@link com.funnyb.cwc.combat.CwcCombatEvents}。
     * <p>
     * 本事件有两个 post 来源，且命中方块时两者无法区分，故按模式锁切分：
     * <ul>
     *   <li>{@code startAttack()}——每次左键**点击**</li>
     *   <li>{@code continueAttack()}——按住左键**且准星指着非空气方块**时，每 tick 一次</li>
     * </ul>
     * 若一律取消，挖方块分支（{@code startDestroyBlock} / {@code continueDestroyBlock}）会在
     * 事件被取消后直接 return，表现为"拿 CWC 武器挖不动方块"。
     * <p>
     * 按住不放的连发不靠这里（原版点击不连发），由 {@link #onClientTick} 驱动。
     */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getKeyMapping() != Minecraft.getInstance().options.keyAttack) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;
        // 按下沿落锁。**必须在这里**——本回调位于 handleKeybinds 内，晚于 tick 开头的 pick()，
        // 因而读到的是按下那一刻刚刷新的 hitResult（详见 lockHoldModeIfNotLocked）。
        lockHoldModeIfNotLocked(mc);
        if (player.getMainHandItem().getItem() != CwcItems.HANDLE_PART.get()) return; // 非 CWC 主手放行 vanilla
        if (isMiningLocked(mc)) return;   // 本次按住锁定为"只挖掘"：这条左键归原版，不取消也不出刀

        // 主手 CWC 武器一律拦截 vanilla 攻击（无论冷却）
        event.setCanceled(true);
        // 不让 startAttack 走默认挥动（LocalPlayer.swing 发 ServerboundSwingPacket →
        // 服务端 handleAnimate → player.swing → ServerPlayer.swing 会 resetAttackStrengthTicker
        // 隐式重置攻速条），改为纯本地挥动（swing(hand,false) 不发包）
        event.setSwingHand(false);
        tryMainHandAttack(mc, player, CwcCombat.MAIN_CLICK_MIN_SCALE);   // 手动点击：九成即可出手
    }

    /**
     * 原版"双手忙"闸门——**划船时按着任一移动键**（{@code LocalPlayer.rideTick} 把 {@code handsBusy} 置位），
     * 原版据此在 {@code Minecraft.startAttack} / {@code startUseItem} 里直接拒掉攻击与交互
     * （连手中的物品都不渲染，见 {@code ItemInHandRenderer}）。仅限**船**：矿车不走这条路。
     * <p>
     * 本模组两条出手路径里，点击那条（{@code InteractionKeyMappingTriggered}）是原版从
     * {@code startAttack} 内部发出来的，早被这道闸挡住；但按住那条（{@link #onClientTick} 的每 tick 驱动）
     * 绕过了 {@code handleKeybinds}，必须自己补——否则会出现"划船时点左键没反应、按住却照打"。
     * <p>
     * 只拦攻击，不拦挖掘：原版的 {@code continueAttack} 本来就没有这道闸，持续挖掘在划船时是允许的
     * （被拒的只有 {@code startAttack} 里那次"开始挖掘"）。
     */
    private static boolean handsBusy(Player player) {
        return player instanceof LocalPlayer local && local.isHandsBusy();
    }

    /**
     * 主手出手一次——冷却达到门槛才发包。事件回调与每 tick 自动攻击（{@link #onClientTick}）共用。
     * <p>
     * {@code minScale} 是两条路径唯一的差别：手动点击传 {@link CwcCombat#MAIN_CLICK_MIN_SCALE}
     * （冷却到九成即可出手），自动攻击传 {@link CwcCombat#MAIN_AUTO_MIN_SCALE}（必须满冷却）。
     * 未达门槛直接返回：不发包也不挥动。刚换过武器也直接返回（见 {@link #mainHandSettled}）。
     */
    private static void tryMainHandAttack(Minecraft mc, Player player, float minScale) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;
        if (handsBusy(player)) return;          // 划船中按着移动键：原版禁止出手
        if (!mainHandSettled(player)) return;   // 刚换过武器：等服务端属性追平，否则这一下会被服务端拒收
        if (!CwcCombat.isCooldownReady(player, InteractionHand.MAIN_HAND, stack, minScale)) return;

        // 客户端点选目标（-1 空挥）；判定与服务端结算同源，细则与"为什么还要补骑乘链"见 pickAttackTargetId
        int targetId = CwcCombat.pickAttackTargetId(player, stack, InteractionHand.MAIN_HAND,
                mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null);
        PacketDistributor.sendToServer(new CwcMainHandAttackPacket(targetId));
        player.swing(InteractionHand.MAIN_HAND, false);  // 纯本地挥动（不发包）
        player.resetAttackStrengthTicker();              // 本地攻速条/准星指示器同步归零
    }

    /**
     * 准星是否**指着一个方块**（模式锁的落锁依据）。
     * <p>
     * 必须判 {@code getType() == BLOCK}，不能只判 {@code instanceof BlockHitResult}——**"空挥"也是一个
     * BlockHitResult**：原版拾取没打中任何方块时返回 {@code BlockHitResult.miss(端点, ..., BlockPos.containing(端点))}，
     * 类型是 MISS，但里面揣着**射线末端那个方块的坐标**（端点 = 眼睛 + 视线 × 手长）。
     * 而拾取用的 {@code ClipContext.Fluid.NONE} 让射线**穿过水**，所以水下挥砍时端点必然泡在水里，
     * 水的方块状态又不是空气——只判 {@code instanceof} 的话，水下会被误判成"指着方块"，
     * 整个按住期间锁成"只挖掘"，点击与按住两条出手路径全被挡掉（表现为水下挥砍完全不出手、无音效）。
     * 同理适用于草丛/花/藤蔓这类没有碰撞箱、射线会穿过去的非空气方块。
     * <p>
     * {@code isAir} 那句保留：与 {@code Minecraft.startAttack} 里的原版判断同口径。
     */
    private static boolean isMiningTarget(Minecraft mc) {
        if (mc.level == null) return false;
        return mc.hitResult instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK
                && !mc.level.getBlockState(bhr.getBlockPos()).isAir();
    }

    // —— 按住左键的模式锁 ——

    /** 本次按住左键的模式是否已锁定（按下那一刻判定一次，直到松开才重置） */
    private static boolean holdModeLocked = false;

    /** 锁定的模式：true = 整个按住期间只挖掘，false = 只攻击 */
    private static boolean holdModeMining = false;

    /**
     * 落锁——**必须在 {@link #onAttackKey} 里调**，即 {@code handleKeybinds} 内部。
     * <p>
     * 时机是这条锁的全部难点。{@code handleKeybinds} 位于 tick 开头的 {@code gameRenderer.pick()}
     * **之后**，所以此刻的 {@code hitResult} 正是"按下那一刻"刷新出来的。此前落锁挂在
     * {@code ClientTickEvent.Pre}，而 Pre 早于 {@code pick()}，读到的是**上一 tick** 的准星——
     * 快速甩视角同时按下会锁错模式。
     * （也不能挪到 tick 末尾：那时秒破的方块已被移除，会误判成"只攻击"。）
     * <p>
     * 只锁一次即可：按住打空气时事件只在点击那一下来（{@code continueAttack} 仅在准星指着方块时
     * 每 tick post），而按住挖掘时每 tick 都来——所以用 {@link #holdModeLocked} 保证整个按住期间只判定一次。
     * <p>
     * 语义：按下时准星没指着方块 → 本次按住只攻击，之后准星移到哪都不挖；指着方块 → 只挖掘，
     * 之后准星移到哪都不攻击。
     */
    private static void lockHoldModeIfNotLocked(Minecraft mc) {
        if (holdModeLocked) return;
        // 界面打开 / 鼠标未抓取时不落锁：此时 hitResult 不刷新，锁定会用到陈旧值。
        // （这两个状态下 handleKeybinds 本来也不会走到这里，纯属防御。）
        if (mc.screen != null || mc.getOverlay() != null || !mc.mouseHandler.isMouseGrabbed()) return;
        holdModeMining = isMiningTarget(mc);
        holdModeLocked = true;
    }

    /**
     * 解锁——松开左键即解锁，下次按下重新判定。
     * <p>
     * 放在每 tick 都跑的 {@link #onClientTickPre}：松开左键不产生任何攻击事件，没有别的地方能观察到。
     * 解锁与 tick 内的相位无关，所以放 Pre 没有精度问题。
     */
    private static void unlockHoldModeIfReleased(Minecraft mc) {
        if (!mc.options.keyAttack.isDown()) {
            holdModeLocked = false;
        }
    }

    /**
     * 当前生效的模式：已锁定则用锁定值；尚未锁定（落锁前的一瞬）按当前准星即时判定。
     */
    private static boolean isMiningLocked(Minecraft mc) {
        return holdModeLocked ? holdModeMining : isMiningTarget(mc);
    }

    // —— 主手武器"刚换过"的识别（见 tryMainHandAttack）——

    /** 主手武器换过后需要的追平 tick 数——服务端刷新攻速/交互距离属性比客户端晚约一 tick，留一点余量 */
    private static final int WEAPON_SETTLE_TICKS = 2;

    /**
     * 主手武器的"身份"——用 {@code PART_IDENTITY} + {@code ASSEMBLED_SLOTS} 表示。
     * **刻意不含耐久**：1.21 里耐久是数据组件，武器每次命中都会掉，而耐久不影响攻速/交互距离。
     * 若拿整份组件去比（{@code ItemStack.matches} / {@code isSameItemSameComponents}），
     * 每次命中都会被误判成"刚换了武器"，反而把自动攻击卡住。
     */
    private static String lastMainHandId = null;
    private static Map<String, PartNode> lastMainHandSlots = null;

    /** "从未换过武器"的哨兵值——远早于任何真实 {@code tickCount}，让首次比较不会误判成"刚换" */
    private static final int NO_WEAPON_CHANGE = -1000;

    /** 主手武器身份最后一次变化时所在的 {@link Player#tickCount} */
    private static int mainHandChangedTick = NO_WEAPON_CHANGE;

    /**
     * 更新"主手武器是否刚换过"，并返回现在能否出手。
     * <p>
     * 换武器（无论从普通物品换过来还是两把模组武器互换）时，客户端本地的 {@code ATTACK_SPEED} /
     * {@code ENTITY_INTERACTION_RANGE} 属性和攻速条复位**立刻**生效，而服务端要等换手包到达、
     * 在它自己的 tick 里处理才算数。这个窗口内出手，服务端会拿**旧物品**的攻速去算冷却
     * （{@code getAttackStrengthScale} 的分母就是它）、或拿旧交互距离判距离，于是拒收。
     * <p>
     * 这里等的是服务端**属性**追平，不是冷却：服务端的冷却已改为自持时钟
     * （{@link CwcCombat#isServerCooldownReady}），不再受换武器时的攻速条复位影响；
     * 但伤害与交互距离读的仍是服务端侧属性，那些要等换手包到达才算数。
     * <p>
     * 每 tick 都调（{@link #onClientTick} 里保持状态新鲜），点击路径也会在 {@link #tryMainHandAttack}
     * 里再调一次，保证早于 tick 末尾的按下同样能被拦住。
     * <p>
     * <b>2026-09-17 修正：</b>{@code ASSEMBLED_SLOTS} 的值曾是 {@code Map<String,ItemStack>}，而
     * {@code ItemStack} 没有值语义 {@code equals}，于是**每次命中掉耐久**引发的整栈重发都会让这个比较
     * 判成"刚换武器"，白丢一次攻击。现在值是 {@link PartNode}（record，值语义递归成立），比较才真正
     * 表示"装配内容变了吗"；也不必再拷贝——组件里的 map 不可变且值语义。
     */
    private static boolean mainHandSettled(Player player) {
        ItemStack stack = player.getMainHandItem();
        String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
        Map<String, PartNode> slots = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (!Objects.equals(lastMainHandId, id) || !Objects.equals(lastMainHandSlots, slots)) {
            lastMainHandId = id;
            lastMainHandSlots = slots;
            mainHandChangedTick = player.tickCount;
            return false;   // 本 tick 刚换：先不出手
        }
        return player.tickCount - mainHandChangedTick >= WEAPON_SETTLE_TICKS;
    }

    // —— 副手自动攻击的挂起状态（见 onUseKey / onClientTick）——
    //
    // ⚠ 这一段是**对原版实现细节的依赖**，不是稳定的 API：它复刻的是 Minecraft.startUseItem 的
    //   迭代顺序（MAIN_HAND → OFF_HAND，主手 consumesAction 时直接 return，副手那一轮根本不会发生）。
    //   原版若改了 startUseItem 的迭代方式或 return 条件，这里会静默失准。
    //   判断依据不是"读到了什么状态"，而是"本 tick 有没有观察到 startUseItem 走到某一轮"——
    //   即用回调被调用的次数与顺序去推断原版内部的控制流。改动这段前先回看 startUseItem。

    /** 本 tick 是否见到 {@code startUseItem} 走到主手迭代 */
    private static boolean sawMainHandUse = false;
    /** 本 tick 是否见到 {@code startUseItem} 走到副手迭代 */
    private static boolean sawOffhandUse = false;
    /** 主手 consumesAction 期间挂起副手自动攻击，直到某次真的走到副手迭代或松开右键 */
    private static boolean offhandAutoSuspended = false;

    /**
     * tick 开头做两件事：**解锁**（松开左键的下降沿）与清空副手自动攻击的旗子。
     * <p>
     * 这里**不落锁**——落锁必须在 {@link #onAttackKey}（{@code handleKeybinds} 内、晚于
     * {@code gameRenderer.pick()}），才能拿到按下那一刻的准星。见 {@link #lockHoldModeIfNotLocked}。
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        unlockHoldModeIfReleased(mc);
        // 清空"本 tick startUseItem 走到哪一轮"的旗子——它们在 handleKeybinds 里被置位，
        // 由 tick 末尾的 onClientTick 读取
        sawMainHandUse = false;
        sawOffhandUse = false;
    }

    /**
     * 按住键自动攻击——每 tick 补一次"冷却就绪就出手"。
     * <p>
     * 原版左键只在点击时驱动 {@code startAttack}（不连发）；右键虽由 {@code handleKeybinds} 的
     * 按住循环驱动，却被 {@code rightClickDelay} 量化成 5 tick 一档、慢于武器真实攻速。
     * 故两侧统一在此按真实冷却驱动。
     * <p>
     * 用 {@code Post}：它在 {@code Minecraft.tick()} 末尾触发，**晚于** {@code handleKeybinds}。
     * 真实点击本 tick 已在 {@code onAttackKey} 出手并归零冷却，这里天然被冷却挡住，不会双发。
     * <p>
     * 守卫逐条对齐原版，避免出现"点击不能打、按住却能打"：{@code handleKeybinds} 只在无屏幕、
     * 无 overlay 时调用；{@code isMouseGrabbed} 与 {@code continueAttack} 同条件；
     * {@code isUsingItem} 时原版走的是**丢弃点击**的分支（拉弓/格挡中左键无效），
     * 本驱动绕过了 {@code handleKeybinds}，必须显式补上。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // 每 tick 刷新"刚换过武器"的状态（只在真变化时复制，代价可忽略），
        // 保证点击路径（早于 tick 末尾）调用 tryMainHandAttack 时也拿得到准确值
        mainHandSettled(player);

        if (mc.screen != null || mc.getOverlay() != null) return;   // overlay 是 private，NeoForge 提供 getOverlay()
        if (!mc.mouseHandler.isMouseGrabbed()) return;
        if (player.isSpectator() || player.isUsingItem()) return;

        // 锁定为"只攻击"时照常出手（哪怕此刻准星正指着方块）；锁定为"只挖掘"时不出手
        if (mc.options.keyAttack.isDown() && !isMiningLocked(mc)) {
            tryMainHandAttack(mc, player, CwcCombat.MAIN_AUTO_MIN_SCALE);   // 自动攻击：必须满冷却
        }

        if (mc.options.keyUse.isDown()) {
            // 主手 consumesAction（放置方块/交互）时原版 startUseItem 会直接 return，副手那一轮够不到；
            // 本驱动必须复刻同一个判断，否则放置方块期间短刀会照常出刀。
            // 判定来自 onUseKey 置的两面旗子；挂起状态是**粘性**的——放置后的几 tick startUseItem
            // 被 rightClickDelay 挡住不跑、旗子不动，挂起也就一直保持到松开右键或真的走到副手迭代。
            if (sawMainHandUse && !sawOffhandUse) {
                offhandAutoSuspended = true;
            } else if (sawOffhandUse) {
                offhandAutoSuspended = false;
            }
            if (!offhandAutoSuspended) tryBehaviorUse(mc, player, InteractionHand.OFF_HAND);
        } else {
            offhandAutoSuspended = false;
        }
    }

    /**
     * 副手右键——目标判定在客户端（手感与瞄准一致）。
     * 右键（keyUse）在副手触发点、且副手那件武器在这一手声明了行为时：取消原版右键动作
     * （不发 use 包、不触发方块/实体交互），用客户端 {@link Minecraft#hitResult} 点选目标实体，
     * 发 {@link CwcOffhandAttackPacket}（带目标实体 id）给服务端权威执行。空挥（无实体目标）发 -1，
     * 服务端仅进冷却 + 广播挥动。
     * <p>
     * 未接管（不是本模组武器 / 这一手没声明行为 / 主手武器占用右键 / 冷却中）时放行原版流程——
     * 原版 useItem 也会因 isOnCooldown 直接 pass。
     * <p>
     * 原版按住右键时本事件**每 ~5 tick 就会再来一次**：{@code handleKeybinds} 里
     * {@code keyUse.isDown() && rightClickDelay == 0 && !isUsingItem()} 会每 tick 调 {@code startUseItem}，
     * 而它正是在这里 post 本事件。所以副手本就能"按住连发"，只是节奏被 {@code rightClickDelay}
     * 量化成 5 tick 一档。补上 {@link #onClientTick} 的每 tick 驱动后按真实冷却出手，
     * 与这条路径重复的那次由行为自己的冷却挡住（{@code SwingBehavior.onClientUse}）。
     */
    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getKeyMapping() != Minecraft.getInstance().options.keyUse) return;
        // 记录 startUseItem 走到了哪一轮：主手 consumesAction 时会直接 return，副手那一轮根本不会发生。
        // 副手自动攻击（onClientTick）要靠这两面旗子复刻同一个判断，否则会导致"放方块时短刀仍出刀"。
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            sawMainHandUse = true;
            return;
        }
        sawOffhandUse = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!tryBehaviorUse(mc, mc.player, InteractionHand.OFF_HAND)) return;   // 未接管 → 放行原版

        event.setCanceled(true);
        // 不让 startUseItem 默认挥动（LocalPlayer.swing 会发 ServerboundSwingPacket，
        // 服务端 handleAnimate → player.swing → ServerPlayer.swing 会 resetAttackStrengthTicker
        // 把主手攻击强度清零）。本地挥动已在 tryBehaviorUse 内完成（swing(hand,false) 不发包）。
        event.setSwingHand(false);
    }

    /**
     * 右键出手一次——问"这只手此刻胜出的行为"，它说接管就发包并本地挥动。
     * <p>
     * 事件回调（点击）与每 tick 自动攻击（按住）**共用这一个方法**：两条路径的差别只在调用方
     * （点击那条还要取消原版动作），行为侧完全一致，所以行为接口没有分开的"自动"入口。
     * <p>
     * 前置判断分两层，别混：**这里**管输入层（划船中禁止交互、这一手有没有行为），
     * **行为自己**管"此刻能不能出手"（冷却门槛）。
     *
     * @return 是否接管了本次右键（true = 已发包并本地挥动，调用方应取消原版动作）
     */
    private static boolean tryBehaviorUse(Minecraft mc, Player player, InteractionHand hand) {
        if (handsBusy(player)) return false;                       // 划船中按着移动键：原版禁止交互
        BehaviorDispatch.Resolved resolved = BehaviorDispatch.resolve(player, hand);
        if (resolved == null) return false;                        // 这一手没有行为 → 放行原版
        Entity crosshair = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null;
        Optional<WeaponBehavior.UseIntent> intent = resolved.behavior().onClientUse(new WeaponBehavior.ClientUse(
                resolved.field(), player, hand, resolved.stack(), crosshair, resolved.decl()));
        if (intent.isEmpty()) return false;                        // 行为说此刻不能出手 → 放行原版
        if (!sendIntent(intent.get())) return false;               // 这一（手 × 键位）还没有协议 → 放行原版

        player.swing(hand, false);   // 纯本地挥动，不发包（主副手解耦）
        return true;
    }

    /**
     * 把出手意图发到服务端——**协议适配层**。
     * <p>
     * 今天只有两个包，对应两条已经存在的路径：副手右键（用）与主手普攻（攻击）。所以（手 × 键位）里
     * 只有这两种组合发得出去；数据结构上支持、但协议还没到的情况（例如把瞬发行为挂在
     * {@code mainHandUse} 上）返回 false，调用方据此放行原版、不接管。要支持它就得加一个包，
     * **这一处是唯一要改的地方**。
     *
     * @return 是否真的发出去了
     */
    private static boolean sendIntent(WeaponBehavior.UseIntent intent) {
        if (intent.hand() == InteractionHand.OFF_HAND && intent.button() == Button.USE) {
            PacketDistributor.sendToServer(new CwcOffhandAttackPacket(intent.targetId()));
            return true;
        }
        if (intent.hand() == InteractionHand.MAIN_HAND && intent.button() == Button.ATTACK) {
            PacketDistributor.sendToServer(new CwcMainHandAttackPacket(intent.targetId()));
            return true;
        }
        return false;
    }

}
