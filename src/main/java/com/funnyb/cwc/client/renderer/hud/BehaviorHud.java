package com.funnyb.cwc.client.renderer.hud;

import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.combat.behavior.WeaponBehavior;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 一个 HUD（{@code hud} 字段的实现）——**客户端侧的独立注册表**，注册在 {@link BehaviorHudRegistry}。
 * <p>
 * 与 {@link WeaponBehavior} 的关系是"同一笔声明里的两半、两份注册表"：
 * <ul>
 *   <li>JSON 里同一个字段对象下并排写着 {@code behavior} 与 {@code hud} 两个 id；</li>
 *   <li>那个对象是**一笔声明**：行为按层内优先级胜出时，HUD 就是**它自己写的那个**——不从别的候选取
 *       （否则 HUD 会配上一个它没预期的行为）。想让两个行为共用一个 HUD，在各自的声明里写同一个
 *       hud id 即可（见 {@code BehaviorResolver} 的「HUD 跟着行为走」）；</li>
 *   <li>这边可以自由用 {@code GuiGraphics}——行为那边不可以（它在通用侧，会踩 RuntimeDistCleaner）。</li>
 * </ul>
 * <b>没写 {@code hud} 的字段不画东西</b>：HUD 是可选的，不是行为必须配一份。
 * <p>
 * 实现也必须是无状态单例：一帧可能画多条（主手一条、副手一条），状态留在实例里会互相污染。
 */
public interface BehaviorHud {

    /** hud id（注册键；JSON 里 {@code hud} 字段就是它）。内建的见 {@code BehaviorHudIds} */
    String id();

    /**
     * 画一条。反色混合由调用方 bracket（见 {@code CrosshairBar}），实现只管自己的几何。
     *
     * @param ctx 这次要画的是"哪只手 × 哪个字段"，以及该字段胜出的行为 —— 见 {@link HudContext}
     */
    void render(GuiGraphics gui, HudContext ctx);

    /**
     * 一次绘制请求。
     *
     * @param field   哪个字段的 HUD——同一个实现可能被多个字段共用
     * @param stack   这只手上的物品栈
     * @param decl    该字段胜出的**声明**（行为 id + hud id）
     * @param behavior 该字段胜出的**行为实现**（与 {@code decl.behavior()} 对应）。
     *                 给 HUD 读行为自己的状态用——行为与 HUD 之间只共享查询函数，不共享可变状态
     * @param crosshairTarget 准星命中的实体（"有没有目标"这类判断要用），没有则 null
     */
    record HudContext(BehaviorField field, Player player, InteractionHand hand, ItemStack stack,
                      PartTypeDef.BehaviorDecl decl, WeaponBehavior behavior, Entity crosshairTarget) {}
}
