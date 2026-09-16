package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.AssembledWeaponRenderer;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.network.serverbound.CwcMainHandAttackPacket;
import com.funnyb.cwc.network.serverbound.CwcOffhandAttackPacket;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;
import com.funnyb.cwc.screen.CwcScreen;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CWC 客户端事件处理器。负责四件事：
 * 1. 攻击输入拦截——主手 CWC 武器左键、副手短刀右键，改走自研攻击包（见 {@link #onAttackKey} / {@link #onUseKey}）
 * 2. 按住键自动攻击——每 tick 补一次出手（见 {@link #onClientTick}）
 * 3. CWC 界面打开时隐藏原版 HUD 和手持物品
 * 4. 准星层接管自画攻击指示器；资源重载时刷新 Layouts 缓存与武器合成缓存
 * <p>
 * （PartRegistry 由服务端 datapack 加载，客户端不重载，理由见 {@link #onRegisterReloadListeners}。）
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public class CwcClientEvents {

    /** 若当前屏幕是 CWC 界面，取消手持物品的渲染 */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof CwcScreen) {
            event.setCanceled(true);
        }
    }

    /** 若当前屏幕是 CWC 界面，取消 HUD（血量、饥饿度、快捷栏等）的渲染 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof CwcScreen) {
            event.setCanceled(true);
        }
    }

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
     * 主手出手一次——冷却达到门槛才发包。事件回调与每 tick 自动攻击（{@link #onClientTick}）共用。
     * <p>
     * {@code minScale} 是两条路径唯一的差别：手动点击传 {@link CwcCombat#MAIN_CLICK_MIN_SCALE}
     * （冷却到九成即可出手），自动攻击传 {@link CwcCombat#MAIN_AUTO_MIN_SCALE}（必须满冷却）。
     * 未达门槛直接返回：不发包也不挥动。刚换过武器也直接返回（见 {@link #mainHandSettled}）。
     */
    private static void tryMainHandAttack(Minecraft mc, Player player, float minScale) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;
        if (!mainHandSettled(player)) return;   // 刚换过武器：等服务端属性追平，否则这一下会被服务端拒收
        if (!CwcCombat.isCooldownReady(player, InteractionHand.MAIN_HAND, stack, minScale)) return;

        // 客户端点选目标（眼睛到碰撞箱距离 ≤ reach，与主/副目标判定同款量法），发主手攻击包（-1 空挥）。
        // 横扫武器额外做双区域前置校验（客户端先验一遍能不能打中，与服务端同一几何）；normal/critical 维持准星点选即可
        int targetId = -1;
        double reach = CwcCombat.resolveReach(player, InteractionHand.MAIN_HAND, stack);
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != player
                && ehr.getEntity().getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach
                && (CwcWeapon.attackStyle(stack) != PartTypeDef.AttackStyle.SWEEP
                    || CwcCombat.isInSweepDualRange(player, ehr.getEntity(), reach))) {
            targetId = ehr.getEntity().getId();
        }
        PacketDistributor.sendToServer(new CwcMainHandAttackPacket(targetId));
        player.swing(InteractionHand.MAIN_HAND, false);  // 纯本地挥动（不发包）
        player.resetAttackStrengthTicker();              // 本地攻速条/准星指示器同步归零
    }

    /**
     * 准星是否命中一个**非空气方块**。
     */
    private static boolean isMiningTarget(Minecraft mc) {
        if (mc.level == null) return false;
        return mc.hitResult instanceof BlockHitResult bhr
                && !mc.level.getBlockState(bhr.getBlockPos()).isAir();
    }

    // —— 按住左键的模式锁 ——

    /** 本次按住左键的模式是否已锁定（按下那一刻判定一次，直到松开才重置） */
    private static boolean holdModeLocked = false;

    /** 锁定的模式：true = 整个按住期间只挖掘，false = 只攻击 */
    private static boolean holdModeMining = false;

    /**
     * 维护"按住左键的模式锁"：**按下的那一刻判定一次，整个按住期间不再改变**。
     * <p>
     * 按下时准星没指着方块 → 本次按住只攻击，之后准星移到哪都不挖；
     * 按下时准星指着方块 → 本次按住只挖掘，之后准星移到哪都不攻击。
     * <p>
     * 松开即解锁，下次按下重新判定。由 {@link #onClientTickPre}（tick 开头）按左键的按下/松开沿更新。
     */
    private static void updateHoldMode(Minecraft mc) {
        if (!mc.options.keyAttack.isDown()) {
            holdModeLocked = false;   // 松开：解锁
            return;
        }
        if (holdModeLocked) return;
        // 界面打开 / 鼠标未抓取时不落锁：此时 hitResult 不刷新，锁定会用到陈旧值，而且本来也攻击不了。
        // 解锁分支在上面——界面打开时的 releaseAll 能正常清锁，不会卡在锁定态。
        if (mc.screen != null || mc.getOverlay() != null || !mc.mouseHandler.isMouseGrabbed()) return;
        holdModeMining = isMiningTarget(mc);   // 按下降沿：判定并锁定
        holdModeLocked = true;
    }

    /**
     * 当前生效的模式：已锁定则用锁定值；**尚未锁定**时按当前准星即时判定。
     * <p>
     * 未锁定这条分支是给按下那一 tick 用的——{@link #onAttackKey} 在 {@code handleKeybinds} 里
     * 触发，早于 tick 末尾的 {@link #onClientTick}，此时锁还没落下。按即时判定既拿到了刚刷新的
     * {@code hitResult}，结果又与随后落锁的值一致，不会出现"第一下和后续不同"。
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
    private static Map<String, ItemStack> lastMainHandSlots = null;

    /** 主手武器身份最后一次变化时所在的 {@link Player#tickCount} */
    private static int mainHandChangedTick = -1000;

    /**
     * 更新"主手武器是否刚换过"，并返回现在能否出手。
     * <p>
     * 换武器（无论从普通物品换过来还是两把模组武器互换）时，客户端本地的 {@code ATTACK_SPEED} /
     * {@code ENTITY_INTERACTION_RANGE} 属性和攻速条复位**立刻**生效，而服务端要等换手包到达、
     * 在它自己的 tick 里处理才算数。这个窗口内出手，服务端会拿**旧物品**的攻速去算冷却
     * （{@code getAttackStrengthScale} 的分母就是它）、或拿旧交互距离判距离，于是拒收。
     * <p>
     * 等价说法见 {@link CwcCombat#isServerCooldownReady}：两边的 {@code attackStrengthTicker}
     * 本来就存在固定相位差，这里只是让客户端在换武器后**多等一拍**，不要卡在最早那一 tick 出手。
     * <p>
     * 每 tick 都调（{@link #onClientTick} 里保持状态新鲜），点击路径也会在 {@link #tryMainHandAttack}
     * 里再调一次，保证早于 tick 末尾的按下同样能被拦住。
     */
    private static boolean mainHandSettled(Player player) {
        ItemStack stack = player.getMainHandItem();
        String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
        Map<String, ItemStack> slots = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (!Objects.equals(lastMainHandId, id) || !Objects.equals(lastMainHandSlots, slots)) {
            lastMainHandId = id;
            lastMainHandSlots = slots == null ? null : new HashMap<>(slots);
            mainHandChangedTick = player.tickCount;
            return false;   // 本 tick 刚换：先不出手
        }
        return player.tickCount - mainHandChangedTick >= WEAPON_SETTLE_TICKS;
    }

    // —— 副手自动攻击的挂起状态（见 onUseKey / onClientTick）——

    /** 本 tick 是否见到 {@code startUseItem} 走到主手迭代 */
    private static boolean sawMainHandUse = false;
    /** 本 tick 是否见到 {@code startUseItem} 走到副手迭代 */
    private static boolean sawOffhandUse = false;
    /** 主手 consumesAction 期间挂起副手自动攻击，直到某次真的走到副手迭代或松开右键 */
    private static boolean offhandAutoSuspended = false;

    /**
     * 按下左键那一 tick、**在 {@code handleKeybinds} 之前**落模式锁。
     * <p>
     * 必须放在 {@code Pre} 而不是 tick 末尾——tick 末尾时方块可能已经被挖掉了：创造模式/秒破
     * 会在同一 tick 内把方块移除，此时再查 {@link #isMiningTarget} 得到的是 false，锁就错记成
     * "只攻击"，表现为"按住只挖掉一个方块，之后开始自动攻击，且每个冷却都重置攻速条"。
     * {@code Pre} 位于 {@code gameRenderer.pick()} 与 {@code handleKeybinds()} 之前，
     * 拿到的是按下前的准星与世界状态，正是"按下那一刻"应有的语义。
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        updateHoldMode(mc);
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
            if (!offhandAutoSuspended) tryOffhandAttack(mc, player);
        } else {
            offhandAutoSuspended = false;
        }
    }

    /**
     * 副手短刀右键攻击——目标判定在客户端（手感与瞄准一致）。
     * 右键（keyUse）在副手触发点、且副手是短刀武器时：取消原版右键动作（不发 use 包、不触发方块/实体交互），
     * 用客户端 {@link Minecraft#hitResult} 点选目标实体，发 {@link CwcOffhandAttackPacket}（带目标实体 id）
     * 给服务端权威执行伤害/冷却。空挥（无实体目标）发 -1，服务端仅进冷却 + 广播挥动。
     * 未接管（非短刀/主手双手武器/冷却中）时放行原版流程——原版 useItem 也会因 isOnCooldown 直接 pass。
     * <p>
     * 原版按住右键时本事件**每 ~5 tick 就会再来一次**：{@code handleKeybinds} 里
     * {@code keyUse.isDown() && rightClickDelay == 0 && !isUsingItem()} 会每 tick 调 {@code startUseItem}，
     * 而它正是在这里 post 本事件。所以副手本就能"按住连发"，只是节奏被 {@code rightClickDelay}
     * 量化成 5 tick 一档。补上 {@link #onClientTick} 的每 tick 驱动后按真实冷却出手，
     * 与这条路径重复的那次由 {@link #tryOffhandAttack} 里的本地冷却挡住。
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
        if (!tryOffhandAttack(mc, mc.player)) return;              // 未接管 → 放行原版

        event.setCanceled(true);
        // 不让 startUseItem 默认挥动（LocalPlayer.swing 会发 ServerboundSwingPacket，
        // 服务端 handleAnimate → player.swing → ServerPlayer.swing 会 resetAttackStrengthTicker
        // 把主手攻击强度清零）。本地挥动已在 tryOffhandAttack 内完成（swing(hand,false) 不发包）。
        event.setSwingHand(false);
    }

    /**
     * 副手出刀一次——冷却就绪且确为短刀才出手。事件回调与每 tick 自动攻击（{@link #onClientTick}）共用。
     *
     * @return 是否接管了本次右键（true = 已发包并本地挥动，调用方应取消原版动作）
     */
    private static boolean tryOffhandAttack(Minecraft mc, Player player) {
        ItemStack off = player.getOffhandItem();
        if (!CwcWeapon.isOffhandKnife(off)) return false;                       // 副手不是短刀
        if (CwcWeapon.isTwoHandedStack(player.getMainHandItem())) return false; // 双手武器占用右键
        if (!CwcCombat.isCooldownReady(player, InteractionHand.OFF_HAND, off)) return false; // 副手独立冷却中

        // 客户端点选目标：hitResult 命中实体、且在短刀自身攻击距离内才取其 id，否则空挥（-1）。
        // 距离用与主手原版一致的眼睛到碰撞箱量法，避免不同高度下副手距离偏短。
        int targetId = -1;
        double reach = CwcCombat.resolveReach(player, InteractionHand.OFF_HAND, off);
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != player
                && ehr.getEntity().getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach) {
            targetId = ehr.getEntity().getId();
        }
        PacketDistributor.sendToServer(new CwcOffhandAttackPacket(targetId));

        // 本地先记一次冷却：服务端的 OFFHAND_COOLDOWN 要一个往返才同步回来，不补的话按住右键会在
        // 空窗期每 tick 重发包，准星指示器也会滞后一 tick。数值与服务端 CwcCombat.applyCooldown 同源。
        player.getCooldowns().addCooldown(CwcItems.OFFHAND_COOLDOWN.get(), CwcCombat.offhandCooldownTicks(off));
        player.swing(InteractionHand.OFF_HAND, false);   // 纯本地挥动，不发包（主副手解耦）
        return true;
    }

    // —— 准星 / 攻击指示器（主手 CWC 武器时接管 crosshair 层自画）——

    /** 准星本体精灵（复刻原版 renderCrosshair 居中 15×15） */
    private static final ResourceLocation CROSSHAIR_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/crosshair");
    private static final ResourceLocation OFFHAND_INDICATOR_BACKGROUND =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final ResourceLocation OFFHAND_INDICATOR_PROGRESS =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
    private static final ResourceLocation OFFHAND_INDICATOR_FULL =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");

    /**
     * crosshair 层接管——主手 CWC 武器时取消原版层（原版准星 + 攻击指示器），自画准星 + CWC 指示器。
     * <p>消除两次反色：CWC 武器 delay>5，原版攻击指示器在准星对准活体时也会画满格（反色），与 CWC 补画
     * 同一区域两次反色 = 还原消失。接管后原版不再画，CWC 全权绘制（一次反色）。
     * F3 debug 模式放行原版：原版 debug 分支只画 3D 准星、不画攻击指示器，无两次反色。</p>
     */
    @SubscribeEvent
    public static void onCrosshairPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;    // 只第一人称（与准星一致）
        if (player.isSpectator()) return;                            // 旁观者走原版逻辑
        ItemStack main = player.getMainHandItem();
        if (main.getItem() != CwcItems.HANDLE_PART.get()) return;    // 只接管 CWC 主手

        // F3 debug 3D 准星放行原版（debug 分支只画 3D 准星、不画攻击指示器，无两次反色）
        if (mc.getDebugOverlay().showDebugScreen()
                && !player.isReducedDebugInfo() && !mc.options.reducedDebugInfo().get()) {
            return;
        }

        event.setCanceled(true);
        GuiGraphics gui = event.getGuiGraphics();
        // 反色混合：准星本体 + 主手攻击指示器（复刻原版 renderCrosshair 样式）
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        // 准星本体：居中 15×15
        gui.blitSprite(CROSSHAIR_SPRITE, (gui.guiWidth() - 15) / 2, (gui.guiHeight() - 15) / 2, 15, 15);
        // 主手 CWC 攻击指示器：仅 CROSSHAIR 模式画准星下方；HOTBAR 走原版快捷栏指示器（hotbar 层不受影响）、OFF 不显示
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
            renderMainHandIndicator(gui, player, main);
        }
        RenderSystem.defaultBlendFunc();
        // 副手短刀指示器（内部自管反色混合）
        renderOffhandIndicator(gui, player);
        RenderSystem.disableBlend();
    }

    /**
     * 主手 CWC 攻击指示器（准星下方，反色混合由调用方保证）：
     * 攻速未满 → 进度条；攻速满 + 存在可攻击目标 → 满格图标；攻速满 + 无目标 → 空。
     * "存在可攻击目标"：横扫 = 攻击范围内有可攻击实体（不要求准星对准）；单体（短刀/斧）= 准星目标可命中。
     */
    private static void renderMainHandIndicator(GuiGraphics gui, Player player, ItemStack main) {
        Minecraft mc = Minecraft.getInstance();
        float scale = player.getAttackStrengthScale(0.0F);
        int x = gui.guiWidth() / 2 - 8;
        int y = gui.guiHeight() / 2 + 9;
        if (scale >= 1.0F) {
            if (CwcCombat.hasAnyAttackableTarget(player, main, InteractionHand.MAIN_HAND,
                    mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null)) {
                gui.blitSprite(OFFHAND_INDICATOR_FULL, x, y, 16, 16);
            }
        } else {
            gui.blitSprite(OFFHAND_INDICATOR_BACKGROUND, x, y, 16, 4);
            gui.blitSprite(OFFHAND_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (scale * 17.0F), 4);
        }
    }

    /**
     * 副手短刀攻击指示器（准星上方，内部自管反色混合）：短刀冷却就绪 + 存在可命中目标 → 满格图标；
     * 冷却中即使有目标也只显示就绪度进度条。主手非 CWC 时由 {@link #onCrosshairPost} 调用，
     * 主手 CWC 时由 {@link #onCrosshairPre} 接管调用。
     */
    private static void renderOffhandIndicator(GuiGraphics gui, Player player) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack off = player.getOffhandItem();
        if (!CwcWeapon.isOffhandKnife(off)) return;
        if (CwcWeapon.isTwoHandedStack(player.getMainHandItem())) return; // 双手武器占用右键
        // 就绪度 = 1 - 副手独立冷却占比（1 可攻击，0 刚出刀）；partial tick 固定 0（与原版 getAttackStrengthScale(0.0F) 一致）
        float ready = CwcCombat.offhandReadiness(player);
        int x = gui.guiWidth() / 2 - 8;      // 16 宽居中于准星
        // 顶部对齐锚点：冷却条(16×4)与满格图标(16×16)同顶，就绪时往下长；
        // 锚点取准星上方刚好不碰准星的最低位置（图标 16 高、底边距准星顶 h/2-7 留 1px → y = h/2-24）
        int y = gui.guiHeight() / 2 - 24;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        // 满格图标（可攻击提示）：冷却就绪 && 存在可攻击目标（不要求准星对准横扫，单体看准星）
        boolean canHit = ready >= 1.0F && CwcCombat.hasAnyAttackableTarget(player, off, InteractionHand.OFF_HAND,
                mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null);
        if (canHit) {
            gui.blitSprite(OFFHAND_INDICATOR_FULL, x, y, 16, 16);  // 满格 16×16，与冷却条顶部对齐（往下长）
        } else if (ready < 1.0F) {
            gui.blitSprite(OFFHAND_INDICATOR_BACKGROUND, x, y, 16, 4);
            gui.blitSprite(OFFHAND_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (ready * 17.0F), 4);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * 攻击指示器 Post 兜底——主手**非 CWC** 时的副手短刀指示器（主手 CWC 时由 {@link #onCrosshairPre}
     * 接管，Post 不触发）。**冷却就绪且存在可攻击目标 → 提示可攻击**。
     */
    @SubscribeEvent
    public static void onCrosshairPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;    // 只第一人称显示（与准星一致）
        if (player.isSpectator()) return;                            // 旁观者不攻击
        if (player.getMainHandItem().getItem() == CwcItems.HANDLE_PART.get()) return; // 主手 CWC 由 Pre 接管
        renderOffhandIndicator(event.getGuiGraphics(), player);
    }

}
