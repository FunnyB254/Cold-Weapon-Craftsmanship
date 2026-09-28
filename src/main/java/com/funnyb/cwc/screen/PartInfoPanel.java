package com.funnyb.cwc.screen;

import com.funnyb.cwc.crafting.AssemblyTree;
import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartStacks;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * 零件数值浮窗——画在主面板**左侧之外**，读"当前零件"的数值。
 * <p>
 * 观感是"被主面板压住"：它右端 4px 与主面板重叠，而 {@link BaseInventoryScreen} 把它画在主面板
 * <b>之前</b>，于是主面板的左边缘（1px 黑外轮廓 + 2px 白高光）盖在它上面，像从底下抽出来的一页。
 * <p>
 * 参照物是原版的标签页：<b>形状</b>同创造模式物品栏顶上那排，<b>体色</b>取
 * {@code gui/sprites/advancements/tab_left_*.png} 那一族——原版那边"选中的标签页"体色是
 * {@code #C6C6C6}（正是主面板的体色），"未选中的"是 {@code #8B8B8B}。浮窗用后者：它本就该是
 * 退到后面、被压住的暗面，和主面板一眼分得开。贴图由 {@code tmp/gen_part_info.py} 生成，
 * 脚本里带着"与参照物边缘同构"的断言。
 * <p>
 * 显示条件完全由调用方给不给 {@code part} 决定（空则整个不画）：制造界面给选中的材料变体，
 * 装配界面给底座槽里的物品。见 {@link BaseInventoryScreen#infoPanelPart()}。
 * <p>
 * 数值来源是 {@link AssemblyTree} 现成的聚合值，不在这里另算一套：零件自己没有子树时，
 * 那几个聚合值就是它自己的贡献；带着子树（子装配体）时就是整棵子树的合计。
 */
final class PartInfoPanel {

    /** 浮窗贴图。宽高与 {@code crafting_screen.json} 的 info_panel 一致时是一比一绘制 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "textures/gui/part_info.png");
    /**
     * 贴图文件的实际尺寸——只用于 UV。
     * <p>
     * 与布局里的 info_panel.{@code width/height} 分开是有意的：那一对是"画多大"，这一对是"图多大"。
     * 两者不等时整张图会被缩放（糊），而不是采样到图外去——要改尺寸就连 PNG 一起改。
     */
    private static final int TEX_WIDTH = 100;
    private static final int TEX_HEIGHT = 192;

    /** 文字左内缩：贴图 x=0..3 是 1px 黑外轮廓 + 2px 白高光，字要让开 */
    private static final int TEXT_INSET_LEFT = 8;
    /** 文字右内缩：最后 4px 压在主面板底下，字不许写进去（否则半截被盖住） */
    private static final int TEXT_INSET_RIGHT = 8;
    /** 首行文字的 y。取 6 是因为主面板标题也是 6（crafting_screen.json 的 title_y），两块对齐才像一套 */
    private static final int TEXT_TOP = 6;
    /**
     * 文字颜色。
     * <p>
     * 浮窗体色是 {@code #8B8B8B}（见下），与主面板的 {@code #C6C6C6} 不是一档暗度，
     * 所以这里<b>不能</b>沿用主面板标题那套深灰无阴影：同一种颜色组合放到暗面上就糊了。
     * 取白字 + 阴影，与本模组在同一个 {@code #8B8B8B} 面上的既有画法一致（{@code SlotList} 的行名）。
     */
    private static final int TEXT_COLOR = 0xFFFFFF;

    /**
     * 上一次显示过的零件——内容一变就把滚动相位归零。存**副本**：装配台底座槽那一份 ItemStack
     * 是会被原位改写的（装零件就是往它身上塞 {@code ASSEMBLED_SLOTS}），存引用等于拿它和自己比，
     * 永远判为"没变"。
     */
    private ItemStack shownPart = ItemStack.EMPTY;
    /** 本次内容是从哪一刻开始显示的（{@link Util#getMillis()}）——滚动相位以它为原点 */
    private long shownSince;

    /**
     * 画浮窗。{@code part} 为空则什么都不画（= 隐藏）。
     * <p>
     * 玩家从 {@link Minecraft#player} 现取——成品读数要它的属性基础值；取不到（理论上不该发生）
     * 就自动回落成贡献值形式，与原版 tooltip 没有玩家时的行为一致。
     */
    void render(GuiGraphics guiGraphics, int leftPos, int topPos, ItemStack part) {
        // 内容一变就把滚动相位归零——放进/取出零件、换选中的材料都会走到这里
        // （装配台上"放入零件"会改底座那份的 ASSEMBLED_SLOTS 组件，所以判据是组件级相等）。
        // 相位挂在"这块内容出现了多久"上，不能直接用绝对时间：那样面板刚出现时相位是随机的，
        // 一上来就可能已经滚到中段，等于没有"从开头开始"这回事。
        if (!ItemStack.isSameItemSameComponents(shownPart, part)) {
            shownPart = part.copy();
            shownSince = Util.getMillis();
        }
        if (part.isEmpty()) return;
        // 一行都拼不出来（认不出的零件 id、或数值全 0）就整块不画——空面板比不画更像出了故障
        List<Component> lines = linesOf(part, Minecraft.getInstance().player);
        if (lines.isEmpty()) return;

        var cfg = Layouts.craftingScreen().info_panel;
        int x = leftPos + cfg.x_offset;
        int y = topPos + cfg.y_offset;

        // 带目的尺寸的重载：源取整张贴图（TEX_WIDTH×TEX_HEIGHT），画到布局给定的宽高上
        guiGraphics.blit(TEXTURE, x, y, cfg.width, cfg.height,
                0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        var font = Minecraft.getInstance().font;
        long elapsed = Util.getMillis() - shownSince;
        // 放得下的行原地不动，放不下的那行在自己的视口里循环滚（见 ScrollingText）
        int viewWidth = cfg.width - TEXT_INSET_LEFT - TEXT_INSET_RIGHT;
        for (int i = 0; i < lines.size(); i++) {
            ScrollingText.draw(guiGraphics, font, lines.get(i),
                    x + TEXT_INSET_LEFT, y + TEXT_TOP + i * font.lineHeight,
                    viewWidth, elapsed, TEXT_COLOR, true);
        }
    }

    /**
     * 组装浮窗上的行。没声明过的属性不列（{@link AssemblyTree} 对缺失属性一律返回 0，
     * 从外面分不出"没有"与"是 0"，所以按"= 0 就不列"处理）。
     * <p>
     * 两条例外，都是为了和"把这件物品拿在手里时原版 tooltip 显示什么"对齐：
     * <ul>
     *   <li>手柄类零件的伤害与攻速<b>无条件列</b>——裸手柄在原版 tooltip 上就是
     *       "1 攻击伤害 / 4 攻击速度"（{@code WeaponStats.deriveModifiers} 说明里写明的那两条）；</li>
     *   <li>它们显示<b>成品读数</b>而不是贡献值，见 {@link #readout}。</li>
     * </ul>
     */
    private static List<Component> linesOf(ItemStack part, Player player) {
        String id = part.get(CwcDataComponents.PART_IDENTITY.get());
        if (id == null) return List.of();
        PartDef def = PartRegistry.getPartDef(id);
        if (def == null) return List.of();
        PartTypeDef type = PartRegistry.getTypeDef(def.type());
        AssemblyTree tree = AssemblyTree.of(part);

        // 是不是"手柄类"——判据复用 PartStacks.itemFor 的结论（承载物品是不是 HANDLE_PART），
        // 也就是"这件物品拿在手里会不会走原版属性 tooltip"那条判据本身。
        // 没有玩家时回落成贡献值：原版 addModifierTooltip 的成品读数同样只在有玩家时才算得出来。
        boolean finished = player != null && PartStacks.itemFor(id) == CwcItems.HANDLE_PART.get();

        List<Component> lines = new ArrayList<>();
        if (finished || tree.damage() != 0) {
            lines.add(line("damage", readout(player, Attributes.ATTACK_DAMAGE, tree.damage(), finished)));
        }
        if (finished || tree.speed() != 0) {
            lines.add(line("speed", readout(player, Attributes.ATTACK_SPEED, tree.speed(), finished)));
        }
        if (tree.durability() != 0) {
            lines.add(line("durability", number(tree.durability())));
        }
        if (tree.block() != 0) {
            lines.add(line("block", number(tree.block())));
        }
        // 屏蔽副手是个是非项、不是数值，所以只在 true 时列一行纯标签（false 不用写，缺行即"不屏蔽"）
        if (type != null && type.disableOffHand()) {
            lines.add(Component.translatable("tooltip.cwc.stat.disable_offhand"));
        }
        return lines;
    }

    /**
     * 一行"标签&lt;冒号&gt;值"。冒号用语言键（{@code tooltip.cwc.colon}）——中文是全角，硬写会错。
     */
    private static Component line(String key, String value) {
        return Component.translatable("tooltip.cwc.stat." + key)
                .append(Component.translatable("tooltip.cwc.colon"))
                .append(value);
    }

    /**
     * 数值文本。
     * <p>
     * {@code finished}（手柄类）给<b>成品读数</b>：玩家基础值 + 贡献，逐字照原版
     * {@code ItemStack.addModifierTooltip} 的算法，不带正负号——就是拿在手里时 tooltip 上那个数。
     * ⚠ 基础值必须<b>问玩家</b>，不能写死：{@code Attributes.ATTACK_DAMAGE} 的泛用默认值是 2.0，
     * 玩家那 1.0 是 {@code Player#createAttributes} 设的。
     * <p>
     * 其余零件给<b>贡献值</b>，正数补 "+"（负数由格式器自带 "-"）。
     * <p>
     * 数字格式复用原版的 {@code ATTRIBUTE_MODIFIER_FORMAT}（{@code "#.##"}），
     * 与武器 tooltip 上的读数写法一致，也顺手抹掉 {@code 3.0} 那个多余的零。
     */
    private static String readout(Player player, Holder<Attribute> attribute, double contribution, boolean finished) {
        double value = finished ? player.getAttributeBaseValue(attribute) + contribution : contribution;
        String text = ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value);
        return !finished && value > 0 ? "+" + text : text;
    }

    /** 平面数值（耐久、格挡）——没有"基础值"可加，也不带符号 */
    private static String number(double value) {
        return ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value);
    }
}
