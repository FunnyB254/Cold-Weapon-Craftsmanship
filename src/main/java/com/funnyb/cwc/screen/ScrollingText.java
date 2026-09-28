package com.funnyb.cwc.screen;

import com.funnyb.cwc.Config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 一行放不下时的循环滚动绘制——零件数值浮窗的数值行（{@link PartInfoPanel}）与装配台的槽位名
 * （{@link SlotList}）共用这一份，两处的滚动手感因此必然一致。
 * <pre>
 *   开头（先停一会儿）  |qwertyuiopa|
 *   滚到末尾            |cvbnm   qwerty|
 * </pre>
 * 做法是**画两份**，第二份接在"整行 + {@link #GAP} 的缺口"之后。第一份向左滚出视口时，
 * 第二份正好从右边顶上来，滚到 -cycle 时它的位置与第一份在 0 时完全重合——所以这是个无缝的圈，
 * 换轮那一刻看不出来。两份就够：会走到这里来的行本来就比视口宽，cycle 必然大于视口，
 * 中间不会出现空窗。
 * <p>
 * 缺口是给读的人看的（分清尾巴和新一轮的开头），不是拿来藏接缝的。
 * <p>
 * 相位按"这块内容出现了多久"（{@code elapsedMs}）算，**不按绝对时间**：绝对时间的话相位是随机的，
 * 界面刚打开就可能已经滚到中段。归零的时机由各调用方自己定——它们才知道"内容变了"是几时：
 * 数值浮窗按当前零件判（{@code PartInfoPanel.render}），槽位名按行内容与滚动位置判
 * （{@code SlotList.setRows}）。
 * <p>
 * 停顿时长与滚动速度从设置里现读（见 {@link Config}），改设置立刻生效。
 */
final class ScrollingText {

    /**
     * 一次循环里，文本末尾到它自己下一次开头之间空多少像素。
     * <p>
     * 这个缺口不是装饰：文本滚到末尾后是**接着它自己的开头**继续走的，没有缺口的话
     * "...bcm|qwerty..." 会连成一片，读的人分不出哪儿是尾巴哪儿是新的一轮。
     */
    private static final int GAP = 16;

    private ScrollingText() {}

    /**
     * 画一行。放得下就原地画；放不下就让它在 {@code viewWidth} 宽的视口里**循环左滚**。
     *
     * @param elapsedMs 这块内容已经显示了多久（相位原点见类注释）
     */
    static void draw(GuiGraphics guiGraphics, Font font, Component text,
                     int x, int y, int viewWidth, long elapsedMs, int color, boolean shadow) {
        int textWidth = font.width(text);
        if (textWidth <= viewWidth) {
            guiGraphics.drawString(font, text, x, y, color, shadow);
            return;
        }
        int cycle = textWidth + GAP;
        long scrolled = elapsedMs - (long) (Config.dwellSeconds() * 1000.0);
        int shift = 0;
        if (scrolled > 0) {
            // 推进量按"像素/秒"换算，**取模之后**才转 int：
            // (int) 一个超过 Integer.MAX_VALUE 的 double 会饱和成 MAX_VALUE，那样连续滚久了会跳一下
            long advanced = (long) (scrolled * Config.scrollPixelsPerSecond() / 1000.0);
            shift = -(int) Math.floorMod(advanced, (long) cycle);
        }
        // 视口就是这段裁切——文字从边上滑进滑出靠它。它比调用方自己的裁切更紧
        // （数值浮窗按行、槽位列表按整个列表体），所以不会漏到外面去
        guiGraphics.enableScissor(x, y, x + viewWidth, y + font.lineHeight);
        guiGraphics.drawString(font, text, x + shift, y, color, shadow);
        guiGraphics.drawString(font, text, x + shift + cycle, y, color, shadow);
        guiGraphics.disableScissor();
    }
}
