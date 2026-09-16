package com.funnyb.cwc.screen;

import net.minecraft.resources.ResourceLocation;

/**
 * GUI 图标贴图——字形一律是独立的小 PNG，键体由原版按钮精灵在运行时负责。
 * <p>
 * 不再把图标烘焙进 256x256 的面板贴图：烘焙死的话悬停时没法换成原版的高亮键体。
 * 现在这样也便于单独精修——改一张 16x16 的小图即可，不必重画面板。
 * <p>
 * 出处：minecraft-texture-pipeline/scripts/gui_vanilla.py 生成，白字 + 右下 1px 深色投影，
 * 与原版按钮文字同一套配色。
 */
final class GuiIcons {

    /** 帮助「?」——16x16，两个界面的 helpButton */
    static final ResourceLocation HELP = tex("help");
    /** 循环配方——16x16，制造界面的 cycleButton（尺寸须与 crafting_screen.json 的 cycle_button 一致） */
    static final ResourceLocation CYCLE = tex("cycle");
    /** 列表详情热区——12x12，装配界面每行（尺寸须与 assembling_screen.json 的 info.size 一致） */
    static final ResourceLocation INFO = tex("info");

    private GuiIcons() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                "coldweaponcraftsmanship", "textures/gui/" + name + ".png");
    }
}
