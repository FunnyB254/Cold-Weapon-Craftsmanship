package com.funnyb.cwc.item;

import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 普通零件物品（{@link CwcItems#PART}）——**非手柄零件**的承载物（各种刃、镡、配重、饰件）。
 * <p>
 * 它唯一的职责是 tooltip：把这件零件的**身份特征**（类型 / 安装方式 / 重量）列出来，
 * 让人在背包与创造栏里就能看出"它是什么、装得进哪种槽"，不必先钻进装配台。
 * <p>
 * <b>数值不在这里</b>——伤害/攻速/耐久/格挡那些贡献值归装配台左侧的零件数值浮窗
 * （{@code screen/PartInfoPanel}），两处不要各写一份。
 * <p>
 * 手柄类零件不由本物品承载（见 {@code PartStacks#itemFor} 按类型 {@code role()} 选承载物），
 * 它们走的是 {@link CwcWeapon} 那套行为读数。
 */
public class PartItem extends Item {

    public PartItem(Properties properties) {
        super(properties);
    }

    /**
     * 身份特征三行，缺哪条就不出哪行。
     * <p>
     * <b>读的是「类型」的 data，不是零件自己的 data。</b> 判据在槽位那边：
     * {@link PartTypeDef.SlotDef#accepts(PartTypeDef)} 收的是 {@code PartTypeDef}，读的是它的 {@code data}
     * （装配台的放入校验 {@code AssemblingMenu#isValidForSlot} 与类型图环检测共用这一套）。
     * 所以这三行显示什么，就等于"槽位收不收它"——两边同源，不会出现"tooltip 说装得进、槽位不收"。
     * 注意个别零件自己的 data 里也写了 {@code weight}（如铁轻护手），那个**不参与匹配**，是死数据。
     * <p>
     * **材质不在这里**（作者 2026-09-29 定）：它没有字段、只能从 id 路径末段推，与"槽位收不收"无关，
     * 不属于"特征"。
     * <p>
     * 语言键沿用槽位约束那套动态拼法（详见 {@link #addLine}），与装配台"特征需求"是同一批键。
     * <p>
     * 三处**查不到定义就一行都不出**，都是正常情形、不是错误：{@code PART_IDENTITY} 里放的是**类型** id 的
     * 类型图标（{@code PartStacks#typeIcon}，创造栏与列表里的分类图标）、旧存档里的非法 id、
     * 以及类型字段悬空的数据。注册表未绑定（还没进关卡）时同理——返回 null 即静默跳过。
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (id == null) return;
        PartDef def = PartRegistry.getPartDef(id);
        if (def == null) return;
        PartTypeDef type = PartRegistry.getTypeDef(def.type());
        if (type == null) return;           // 类型悬空（数据坏了）：三项都在类型那边，没什么可显示的
        addLine(tooltip, "type", type.data().get("type"));
        addLine(tooltip, "mount", type.data().get("mount"));
        addLine(tooltip, "weight", type.data().get("weight"));
    }

    /**
     * 一行"标签：取值"，标签灰字（与武器 tooltip 的行为行同一画风）。
     * <p>
     * 语言键按槽位约束的规则动态拼：{@code <键>.cwc} 是标签、{@code <键>.cwc.<取值>} 是取值。
     * 于是第三方加约束键时，按同规则补了标签就有这一行，不必来改代码；缺键时显示原始键
     * （与装配台"特征需求"缺键时的表现一致，见 BUG-026）。
     * <p>
     * 取值缺失（类型没声明这个键）时整行不出现。
     */
    private static void addLine(List<Component> tooltip, String key, String value) {
        if (value == null) return;
        tooltip.add(Component.translatable(key + ".cwc").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("tooltip.cwc.colon"))
                .append(Component.translatable(key + ".cwc." + value)));
    }
}
