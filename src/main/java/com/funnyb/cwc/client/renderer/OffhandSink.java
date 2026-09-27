package com.funnyb.cwc.client.renderer;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.combat.behavior.BehaviorResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * 副手被双手武器占用时（{@code disableOffHand}）的**视觉反馈：副手沉下去一半**。
 * <p>
 * 起因：上一轮把 {@code disableOffHand} 补成"真的占用副手"之后，副手什么都不做却**没有任何反馈**——
 * 手上的方块照原位举着，只有"按了没反应"。这里给它一个看得见的状态：被占用时下沉、解除时升回，
 * 两个方向都带过渡（作者 2026-09-27 定）。
 * <p>
 * <b>下沉量是个布尔驱动的量，不会叠加</b>：判据 {@code BehaviorResolver.handBlocked(player, OFF_HAND)}
 * 只看"有没有"（两只手都拿双手武器也只算一次），所以永远是 0 或 {@code 0.5} 两个目标值之一。
 *
 * <h2>数字全部照抄原版的装备动画（{@code ItemInHandRenderer}）</h2>
 * <ul>
 *   <li><b>位移系数 {@value #EQUIP_DROP}</b>：{@code applyItemArmTransform} 里
 *       {@code translate(±0.56, -0.52 + equippedProg * -0.6, -0.72)}——原版满级下沉就是 {@code 0.6}。
 *       本类的"下沉一半" = {@code 0.5 × 0.6 = 0.3}；</li>
 *   <li><b>步长 {@value #EQUIP_STEP}</b>：{@code ItemInHandRenderer.tick()} 里
 *       {@code height += Mth.clamp(target - height, -0.4F, 0.4F)}；</li>
 *   <li><b>插值</b>：{@code Mth.lerp(partialTick, oSink, sink)}，与原版
 *       {@code 1 - Mth.lerp(partialTicks, oOffHandHeight, offHandHeight)} 同形，帧间平滑。</li>
 * </ul>
 * 于是位移以 {@code 0.4 × 0.6 = 0.24} 每 tick 走——与原版满级下沉**逐帧同速**；距离只有一半，
 * 所以整段过渡只用 <b>1.25 tick</b>（"很快"是"速度照抄原版"的必然结果，不是没做动画）。
 * 想更缓就只调 {@link #EQUIP_STEP}，别动其余两个。
 *
 * <h2>为什么是"在 {@link RenderHandEvent} 里平移一下 PoseStack"</h2>
 * <ul>
 *   <li>事件在 {@code renderHandsWithItems} 里、**{@code renderArmWithItem} 之前** post，拿到的是那一帧的
 *       {@code PoseStack}（已含原版的视角 bob 旋转）。**本类的平移与原版 equip 的平移在同一个坐标系里**
 *       ——所以 {@code -0.3} 就是原版那 {@code -0.6} 的精确一半，不需要任何换算；</li>
 *   <li>一次平移覆盖副手那一手要画的**全部东西**（普通物品、格挡/弓/地图/望远镜等各有各的姿态分支），
 *       不必逐分支适配；</li>
 *   <li>被否掉的：{@code RenderArmEvent} 只管**空手**的手臂（副手拿着东西时根本不触发）；
 *       取消事件自己重画要把整个 {@code renderArmWithItem} 复刻一遍。</li>
 * </ul>
 *
 * <h2>⚠ 这里的 PoseStack 平移**故意不配对 pop**</h2>
 * {@code renderArmWithItem} 自己的 push/pop 在**本类的平移之后**，所以它的 pop 撤销不了这笔位移；
 * 这笔位移会一直留到 {@code GameRenderer.renderItemInHand} 那层的 {@code posestack.popPose()}
 * （它就在 {@code renderHandsWithItems} 之后紧接着执行，且 {@code PoseStack} 每帧新建）。
 * 也就是说：**只影响"副手之后、本次手部渲染结束之前"那段空档**，而那段里只有 {@code buffer.endBatch()}。
 * 这不是漏写 pop，是刻意的——看这段代码时别"补"上（补了反而会让副手自己没下沉）。
 *
 * <p>
 * <b>只管副手</b>：主手那侧不动（作者指定）。对称那一半（副手拿着双手武器 → 主手下沉）今天没有数据用，
 * 要做再说。原版自己的装备动画（切物品、划船收手）照旧生效，与本类的位移**相加**——最坏
 * {@code 0.6 + 0.3}，比原版最深的收手还低一点，游戏里看一眼会不会掉出视野。
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public final class OffhandSink {

    /** 原版装备动画的位移系数——见类注释，满级下沉 {@code 0.6} */
    private static final float EQUIP_DROP = 0.6F;
    /** 原版装备动画的每 tick 步长——见类注释 */
    private static final float EQUIP_STEP = 0.4F;
    /** "下沉一半"：原版满级（{@code 1.0}）的一半 */
    private static final float SINK = 0.5F;

    /** 当前下沉量。**单位与原版 equip 进度相同**（{@code 1.0} = 原版满级那 {@code 0.6} 的位移） */
    private static float sink;
    /** 上一 tick 的下沉量，渲染时与 {@link #sink} 一起按 partialTick 插值 */
    private static float oSink;

    private OffhandSink() {}

    /**
     * 每 tick 推进一次下沉量。{@code ClientTickEvent.Post} 在 {@code Minecraft.tick()} 末尾触发，
     * 而原版的 {@code itemInHandRenderer.tick()} 在 {@code GameRenderer.tick()} 里——**本类紧随其后**，
     * 与它同一相位。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        oSink = sink;
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            sink = 0.0F;
            return;
        }
        // 判据复用现成的那一个（与副手指示器同源，见 CrosshairIndicators#renderHud），不新增规则。
        // 它是**布尔**：两手都拿双手武器也只沉一次，不会叠成两倍（作者 2026-09-27 明确要求）。
        float target = BehaviorResolver.handBlocked(player, InteractionHand.OFF_HAND) ? SINK : 0.0F;
        sink += Mth.clamp(target - sink, -EQUIP_STEP, EQUIP_STEP);
    }

    /** 副手那一手渲染前，把这一帧的 PoseStack 整体往下平移——见类注释（不配对 pop 是刻意的） */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND) return;
        float drop = Mth.lerp(event.getPartialTick(), oSink, sink);
        if (drop == 0.0F) return;   // 正常状态：一个字节都不碰 PoseStack
        event.getPoseStack().translate(0.0F, -drop * EQUIP_DROP, 0.0F);
    }

    /** 换号/退出时归零——状态归本类自己管（与 {@code CwcClientEvents.resetTransientState} 同一套路） */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        sink = 0.0F;
        oSink = 0.0F;
    }
}
