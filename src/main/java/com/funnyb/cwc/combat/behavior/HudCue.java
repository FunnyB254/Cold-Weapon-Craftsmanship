package com.funnyb.cwc.combat.behavior;

import net.minecraft.world.InteractionHand;

/**
 * 行为想显示的 HUD 提示——**只有数据，没有绘制，也没有一个坐标**。
 * <p>
 * "画在哪、上下怎么叠、要不要镜像/翻转、blend 与 GL 状态怎么设"全部归统一的 HUD 层
 * （{@code client/renderer/HudLayer}）。这条边界不是洁癖，理由是具体的：副手指示器那套几何
 * （位置取关于准星精灵中心行的镜像 + 三张 sprite 逐行倒序翻转）是调了四轮才对的，
 * 如果每个新行为各自发明坐标，那份工作要重做 N 遍。
 *
 * @param hand            哪只手的提示（决定画在准星的哪一侧：MAIN 在下、OFF 在上）
 * @param progress        0..1 的就绪 / 蓄力进度（1 = 就绪）
 * @param actionAvailable 当前是否真有可作用的目标（决定"满格"那档提示要不要亮）
 * @param shape           画成什么形状；具体用哪张图由 HUD 层决定
 */
public record HudCue(InteractionHand hand, float progress, boolean actionAvailable, Shape shape) {

    /** HUD 层要画成的形状——{@code BAR_AND_ICON} 就是现在这套（冷却条 + 满格图标） */
    public enum Shape {
        BAR_AND_ICON,
        RING,
        CHARGE_BAR
    }
}
