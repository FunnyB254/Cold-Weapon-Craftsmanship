package com.funnyb.cwc.combat.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

/**
 * 一条行为声明——零件**或槽位**在说"装了我就让某只手某个键位做某件事"。
 * <p>
 * 声明的是**名字 + 优先级 + 参数**，不是实现：实现由 {@link BehaviorRegistry} 按 {@code behavior} 查。
 * 这样数据包只描述"要什么"、代码侧提供"怎么做"，第三方也能自己注册行为。
 *
 * @param hand     哪只手（JSON {@code "main"} / {@code "off"}）
 * @param button   哪个键位（JSON {@code "use"} / {@code "attack"}）
 * @param behavior 行为 id，必须在 {@link BehaviorRegistry} 注册过
 * @param priority 优先级：**≥1 参与竞争**（越大越优先）；**<0 = 主动退出**（装上也永不选中，并取消
 *                 "它所在槽位代它授予的同一个行为"）；**0 或缺失 = 解析错误**——写了行为就必须显式给
 *                 优先级，"没写"不该被静默当成某个默认值（那正是重合报错规则会失效的地方）
 * @param params   行为自己的参数，**原样透传**（{@link CompoundTag}），由各行为按 key 自己读；缺失为空。
 *                 这里刻意不做形状校验：参数形状是行为的私事，而这里只是个通用信封——为每个行为
 *                 维护一份参数 codec 注册表，收益不抵复杂度
 */
public record BehaviorDecl(InteractionHand hand, Button button, String behavior, int priority,
                           CompoundTag params) {

    /**
     * 旧的布尔字段（{@code twoHanded} / {@code offhandAttack}）折算成显式声明时用的优先级。
     * <p>
     * ⚠ 这个数是**拍出来的**：第三方零件若声明 ≥100 的同一行为，会**静默压过**双手武器的格挡。
     * 这是"支持覆盖"的代价，`docs/part-format.md` 里写明了。
     */
    public static final int ALIAS_PRIORITY = 100;

    /** JSON {@code "main"} / {@code "off"} ↔ {@link InteractionHand}；未知值报错 */
    public static final Codec<InteractionHand> HAND_CODEC = Codec.STRING.comapFlatMap(
            name -> switch (name) {
                case "main" -> DataResult.success(InteractionHand.MAIN_HAND);
                case "off" -> DataResult.success(InteractionHand.OFF_HAND);
                default -> DataResult.error(() -> "未知的手 hand=\"" + name + "\"（应为 main / off）");
            },
            hand -> hand == InteractionHand.MAIN_HAND ? "main" : "off");

    public static final Codec<BehaviorDecl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            HAND_CODEC.fieldOf("hand").forGetter(BehaviorDecl::hand),
            Button.CODEC.fieldOf("button").forGetter(BehaviorDecl::button),
            Codec.STRING.fieldOf("behavior").validate(BehaviorDecl::checkRegistered)
                    .forGetter(BehaviorDecl::behavior),
            Codec.INT.fieldOf("priority").validate(BehaviorDecl::checkPriority)
                    .forGetter(BehaviorDecl::priority),
            // CompoundTag.CODEC 就是 Codec.PASSTHROUGH：JSON 与 NBT 之间无损往返，正合"原样透传"的用法
            CompoundTag.CODEC.optionalFieldOf("params", new CompoundTag()).forGetter(BehaviorDecl::params)
    ).apply(instance, BehaviorDecl::new));

    /**
     * 未知行为 id → **加载期报错**，而不是运行时静默什么都不做。
     * <p>
     * 与未知 {@code parser} 同样的处理：失败做进 {@link DataResult}、不抛异常，所以只让这一条零件/类型
     * 加载失败并在日志里指名道姓，其余数据照常。
     */
    private static DataResult<String> checkRegistered(String id) {
        if (BehaviorRegistry.isRegistered(id)) return DataResult.success(id);
        return DataResult.error(() -> "未知的行为 behavior=\"" + id + "\"；已注册的有 "
                + BehaviorRegistry.registeredIds());
    }

    private static DataResult<Integer> checkPriority(int priority) {
        if (priority == 0) {
            return DataResult.error(() -> "priority 不能为 0：声明了行为就必须给 ≥1（越大越优先）；"
                    + "<0 表示主动退出（装上也永不选中）");
        }
        return DataResult.success(priority);
    }

    /** 旧布尔字段折算用：等价于 {@code new BehaviorDecl(hand, button, behavior, ALIAS_PRIORITY, new CompoundTag())} */
    public static BehaviorDecl alias(InteractionHand hand, Button button, String behavior) {
        return new BehaviorDecl(hand, button, behavior, ALIAS_PRIORITY, new CompoundTag());
    }
}
