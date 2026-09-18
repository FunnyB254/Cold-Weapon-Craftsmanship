package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.crafting.PartNode;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * CWC 自定义 DataComponent 注册中心。
 * 所有零件身份和属性通过 DataComponent 存储在 ItemStack 上。
 */
public class CwcDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ColdWeaponCraftsmanship.MODID);

    /** 装配槽位映射的持久化 codec——槽位名 → 装配子树 */
    private static final Codec<Map<String, PartNode>> ASSEMBLED_SLOTS_CODEC =
            Codec.unboundedMap(Codec.STRING, PartNode.CODEC);

    /** 零件完整标识，如 "cwc.standard_blade.iron" */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PART_IDENTITY =
            COMPONENTS.register("part_identity",
                    () -> DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    /**
     * 装配槽位映射——"槽位名 → 装配子树"，写在被改造的底座武器上。
     * <p>
     * 值类型是 {@link PartNode}（零件 id + 子节点）而非 {@code ItemStack}：{@code ItemStack} 没有覆写
     * {@code equals}（身份比较），塞进组件值会让整份组件的相等性失效，同一把武器跨一次网络反序列化就
     * 判成"不相等"。三个症状与完整来龙去脉见 {@link PartNode} 的类文档。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, PartNode>>> ASSEMBLED_SLOTS =
            COMPONENTS.register("assembled_slots",
                    () -> DataComponentType.<Map<String, PartNode>>builder()
                            .persistent(ASSEMBLED_SLOTS_CODEC)
                            .networkSynchronized(ByteBufCodecs.map(
                                    HashMap::new, ByteBufCodecs.STRING_UTF8, PartNode.STREAM_CODEC, 16))
                            .build());

    /**
     * 双手武器格挡减伤加成——**已废弃，仅保留注册**。
     * <p>
     * 它同样是装配树的派生量，物化就会陈旧（改装配件后不更新、绕过装配台时不存在），所以格挡减伤改为
     * 受击时现算（{@code CwcWeapon.blockBonus} → {@code CwcCombatEvents.onHurt}）。保留注册是因为
     * 删除已注册的组件类型会让携带它的旧物品解码失败——本项目只用于单人测试，本可直接删掉，
     * 留着是为了不必要地破坏任何已存物品；代码中已无任何引用。
     */
    @Deprecated
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> BLOCK_VALUE =
            COMPONENTS.register("block_value",
                    () -> DataComponentType.<Float>builder()
                            .persistent(Codec.FLOAT)
                            .networkSynchronized(ByteBufCodecs.FLOAT)
                            .build());

    public static void init(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
