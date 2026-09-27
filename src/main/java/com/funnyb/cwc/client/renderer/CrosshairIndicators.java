package com.funnyb.cwc.client.renderer;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.hud.HudStyle;
import com.funnyb.cwc.client.renderer.hud.HudStyleRegistry;
import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.combat.behavior.BehaviorResolver;
import com.funnyb.cwc.combat.behavior.WeaponBehavior;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.item.CwcWeapon;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 准星层接管与 CWC 指示器的**分派**。
 * <p>
 * <b>本类只做三件事</b>：决定"要不要接管原版准星层"、画准星本体、把"哪只手 × 哪个字段"分派给
 * **行为给的读数** + **数据选的样式**。读数见 {@code WeaponBehavior.hudReadout}，
 * 画法在 {@link HudStyle}（几何唯一一份，在 {@link CrosshairBar} 里）。
 * <p>
 * <b>{@link #onCrosshairPre} 与 {@link #onCrosshairPost} 是一对互补物，必须同处一个类</b>：
 * Pre 在主手**是** CWC 武器时取消原版 crosshair 层（原版准星 + 攻击指示器）并自画；
 * Post 只在主手**非** CWC 时补画副手指示器。两者各自判一次主手是不是 CWC，所以只搬走一个的后果是
 * 副手指示器**画两次**（同一位置两次反色 = 看着不对）或**完全消失**。
 * <p>
 * <b>只画固定的两对（手 × 字段）</b>：主手的 {@code attack} 与副手的 {@code offHandUse}。
 * 每对的读数由**该字段胜出的行为**给（{@code WeaponBehavior.hudReadout}），画法由数据里那个
 * {@code hud} id 选（{@link HudStyleRegistry}），**画在哪由手决定**（主手在准星下方、副手是它的镜像）
 * ——所以两条可以共用同一个样式。
 * <p>
 * 副手的 {@code attack} 字段**故意不单独画**：它是"怎么打"，不是"哪个事件"。出刀那条的读数本来就
 * 取那只手的 {@code attack} 几何（出刀按它打），两条画在同一位置会反色两次、等于什么都没画。
 * <p>
 * 本类**不读**任何跨 tick 状态（模式锁 / 主手武器身份 / 副手挂起旗子），只读 {@code mc.hitResult}、
 * {@code mc.options} 与 {@code CwcCombat} 的查询——它与 {@code CwcClientEvents} 的 tick 相位耦合为零。
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public class CrosshairIndicators {

    /** 准星本体精灵（复刻原版 renderCrosshair 居中 15×15） */
    private static final ResourceLocation CROSSHAIR_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/crosshair");

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
        if (!CwcWeapon.isWeaponBase(player.getMainHandItem())) return;   // 只接管 CWC 主手

        // F3 debug 3D 准星放行原版（debug 分支只画 3D 准星、不画攻击指示器，无两次反色）
        if (mc.getDebugOverlay().showDebugScreen()
                && !player.isReducedDebugInfo() && !mc.options.reducedDebugInfo().get()) {
            return;
        }

        event.setCanceled(true);
        GuiGraphics gui = event.getGuiGraphics();
        CrosshairBar.enableInvertBlend();   // 准星本体与两条指示器共用这一档反色混合
        // 准星本体：居中 15×15
        gui.blitSprite(CROSSHAIR_SPRITE, (gui.guiWidth() - 15) / 2, (gui.guiHeight() - 15) / 2, 15, 15);
        // 主手：攻击指示器（准星下方）；副手：出刀指示器（准星上方，见下）
        renderHud(gui, player, InteractionHand.MAIN_HAND, BehaviorField.ATTACK);
        renderHud(gui, player, InteractionHand.OFF_HAND, BehaviorField.OFF_HAND_USE);
        CrosshairBar.disableInvertBlend();
    }

    /**
     * 攻击指示器 Post 兜底——主手**非 CWC** 时的副手指示器（主手 CWC 时由 {@link #onCrosshairPre}
     * 接管，Post 不触发；原版准星此时由原版自己画）。
     */
    @SubscribeEvent
    public static void onCrosshairPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;    // 只第一人称显示（与准星一致）
        if (player.isSpectator()) return;                            // 旁观者不攻击
        if (CwcWeapon.isWeaponBase(player.getMainHandItem())) return;    // 主手 CWC 由 Pre 接管

        GuiGraphics gui = event.getGuiGraphics();
        CrosshairBar.enableInvertBlend();
        renderHud(gui, player, InteractionHand.OFF_HAND, BehaviorField.OFF_HAND_USE);
        CrosshairBar.disableInvertBlend();
    }

    /**
     * 画"这只手 × 这个字段"的 HUD——**分派点**：读数问这只手这个字段胜出的行为，画法用数据里那个
     * {@code hud} id 选的样式。
     * <p>
     * 四道闸：这只手被双手武器占用（{@code disableOffHand}，**只有副手会中**）→ 不画；原版攻击指示器设置不是
     * CROSSHAIR → 不画（**只对主手那条**，见下）；该字段没有胜出声明 → 不画；行为说这只手不显示
     * （读数为空）→ 不画。样式 id 没注册 → WARN 一次（见 {@link HudStyleRegistry} 的说明）。
     * <p>
     * <b>原版设置那道闸为什么在这儿、而不在样式里</b>：那条设置管的是**原版攻击指示器**，也就是主手那条
     * ——副手那条一直不受它影响。放进样式会把副手那条一并挡掉，那是行为变化（样式层也不该知道原版设置）。
     */
    private static void renderHud(GuiGraphics gui, Player player, InteractionHand hand, BehaviorField field) {
        if (BehaviorResolver.handBlocked(player, hand)) return;   // 双手武器占用副手（主手永远不中）
        if (hand == InteractionHand.MAIN_HAND
                && Minecraft.getInstance().options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) {
            return;   // 原版设置只管主手那条
        }
        ItemStack stack = player.getItemInHand(hand);
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.declFor(stack, field);
        if (decl == null || decl.hud().isEmpty()) return;   // 这个字段没声明样式 → 不画
        String styleId = decl.hud().get();
        HudStyle style = HudStyleRegistry.get(styleId);
        if (style == null) {
            warnUnknownStyle(styleId);
            return;
        }
        WeaponBehavior behavior = BehaviorResolver.behaviorFor(stack, field);
        if (behavior == null) return;                       // 正常不可能：行为 id 在加载期已校验
        Optional<WeaponBehavior.HudReadout> readout = behavior.hudReadout(player, hand, stack, crosshairTarget());
        if (readout.isEmpty()) return;                      // 行为说这只手不显示
        WeaponBehavior.HudReadout numbers = readout.get();
        style.render(gui, numbers.progress(), numbers.highlight(), hand == InteractionHand.OFF_HAND);
    }

    /** 准星命中的实体——判定"有没有可攻击目标"用（客户端侧取，通用包不能引用客户端类型） */
    private static Entity crosshairTarget() {
        return Minecraft.getInstance().hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null;
    }

    /** 已经 WARN 过的样式 id——样式注册在客户端、数据在服务端，加载期查不了，只能首次绘制时报一次，别刷屏 */
    private static final Set<String> WARNED_STYLES = new HashSet<>();

    private static void warnUnknownStyle(String styleId) {
        if (WARNED_STYLES.add(styleId)) {
            ColdWeaponCraftsmanship.LOGGER.warn("未知的 HUD 样式 id \"{}\"；已注册的有 {}",
                    styleId, HudStyleRegistry.registeredIds());
        }
    }
}
